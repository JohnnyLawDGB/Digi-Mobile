#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <string>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

// Experimental JNI bridge to control the Digi-Mobile node process from Android.
// NOTE: Spawning a child process from within an Android app is discouraged for
// Play Store-distributed binaries. This harness is intended for internal and
// sideloaded testing. A future iteration is expected to embed DigiByte Core as
// a library and expose richer control and status APIs.
namespace {

constexpr const char *kLogTag = "DigiMobileJNI";

enum class NodeStatus {
    NOT_RUNNING,
    RUNNING,
    BINARY_MISSING,
    ERROR,
};

// Track the currently running node process ID (if any).
static pid_t g_node_pid = -1;
static NodeStatus g_status = NodeStatus::NOT_RUNNING;
static std::string g_node_binary;

// Helper to convert jstring to std::string.
std::string ToStdString(JNIEnv *env, jstring value) {
    if (!value) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) {
        return {};
    }
    std::string str(chars);
    env->ReleaseStringUTFChars(value, chars);
    return str;
}

bool IsProcessAlive(pid_t pid) {
    if (pid <= 0) {
        return false;
    }
    // kill(..., 0) checks for existence without sending a signal.
    return kill(pid, 0) == 0;
}

bool EnsureDir(const std::string &path) {
    struct stat info {};
    if (stat(path.c_str(), &info) == 0) {
        return S_ISDIR(info.st_mode);
    }
    return mkdir(path.c_str(), 0700) == 0;
}

bool MakeExecutable(const std::string &path) {
    return chmod(path.c_str(), 0700) == 0;
}

bool CopyAssetToFile(AAssetManager *asset_manager, const std::string &asset_path,
                     const std::string &dest_path) {
    if (!asset_manager) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Asset manager is null; cannot copy %s",
                            asset_path.c_str());
        return false;
    }

    AAsset *asset = AAssetManager_open(asset_manager, asset_path.c_str(), AASSET_MODE_STREAMING);
    if (!asset) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Asset %s not found in APK",
                            asset_path.c_str());
        return false;
    }

    int fd = open(dest_path.c_str(), O_CREAT | O_WRONLY | O_TRUNC, 0700);
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to open %s for writing: errno=%d",
                            dest_path.c_str(), errno);
        AAsset_close(asset);
        return false;
    }

    constexpr size_t kBufferSize = 16 * 1024;
    std::vector<unsigned char> buffer(kBufferSize);
    int bytes_read = 0;
    while ((bytes_read = AAsset_read(asset, buffer.data(), buffer.size())) > 0) {
        ssize_t written = write(fd, buffer.data(), static_cast<size_t>(bytes_read));
        if (written != bytes_read) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                                "Short write while copying asset %s to %s (errno=%d)",
                                asset_path.c_str(), dest_path.c_str(), errno);
            close(fd);
            AAsset_close(asset);
            return false;
        }
    }

    close(fd);
    AAsset_close(asset);
    if (bytes_read < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "Error reading asset %s (errno=%d)", asset_path.c_str(), errno);
        return false;
    }

    if (!MakeExecutable(dest_path)) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
                            "Failed to mark %s executable (errno=%d)", dest_path.c_str(), errno);
        return false;
    }
    return true;
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_digimobile_node_DigiMobileNodeController_nativeStartNode(
        JNIEnv *env, jobject /*thiz*/, jobject j_asset_manager, jstring j_config_path,
        jstring j_data_dir, jstring j_files_dir) {
    std::string config_path = ToStdString(env, j_config_path);
    std::string data_dir = ToStdString(env, j_data_dir);
    std::string files_dir = ToStdString(env, j_files_dir);

    if (config_path.empty() || data_dir.empty() || files_dir.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "Config path, data dir, or files dir is empty; refusing to start node");
        g_status = NodeStatus::ERROR;
        return;
    }

    if (g_node_pid > 0 && IsProcessAlive(g_node_pid)) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "nativeStartNode called but node already running with PID %d", g_node_pid);
        g_status = NodeStatus::RUNNING;
        return;
    }

    AAssetManager *asset_manager = AAssetManager_fromJava(env, j_asset_manager);
    const std::string bin_dir = files_dir + "/bin";
    if (!EnsureDir(bin_dir)) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to ensure bin directory at %s",
                            bin_dir.c_str());
        g_status = NodeStatus::ERROR;
        return;
    }

    g_node_binary = bin_dir + "/digibyted";
    if (access(g_node_binary.c_str(), X_OK) != 0) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "Extracting digibyted from assets into %s", g_node_binary.c_str());
        if (!CopyAssetToFile(asset_manager, "bin/digibyted-arm64", g_node_binary)) {
            g_status = NodeStatus::BINARY_MISSING;
            g_node_pid = -1;
            return;
        }
    } else {
        if (!MakeExecutable(g_node_binary)) {
            __android_log_print(ANDROID_LOG_WARN, kLogTag,
                                "digibyted at %s exists but is not executable", g_node_binary.c_str());
        }
    }

    const std::string cli_binary = bin_dir + "/digibyte-cli";
    if (access(cli_binary.c_str(), X_OK) != 0) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "Extracting digibyte-cli from assets into %s", cli_binary.c_str());
        if (!CopyAssetToFile(asset_manager, "bin/digibyte-cli-arm64", cli_binary)) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                                "digibyte-cli asset missing; stop/getblockchaininfo calls will fail");
            g_status = NodeStatus::ERROR;
            g_node_pid = -1;
            return;
        }
    } else {
        if (!MakeExecutable(cli_binary)) {
            __android_log_print(ANDROID_LOG_WARN, kLogTag,
                                "digibyte-cli at %s exists but is not executable", cli_binary.c_str());
        }
    }

    std::string conf_arg = "-conf=" + config_path;
    std::string datadir_arg = "-datadir=" + data_dir;

    pid_t child_pid = fork();
    if (child_pid == 0) {
        execl(g_node_binary.c_str(), g_node_binary.c_str(), conf_arg.c_str(), datadir_arg.c_str(),
              static_cast<char *>(nullptr));
        _exit(127);  // If execl fails.
    } else if (child_pid > 0) {
        g_node_pid = child_pid;
        g_status = NodeStatus::RUNNING;
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "Started Digi-Mobile node with PID %d",
                            g_node_pid);
    } else {
        g_status = NodeStatus::ERROR;
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "Failed to fork for Digi-Mobile node: errno=%d", errno);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_digimobile_node_DigiMobileNodeController_nativeStopNode(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    if (g_node_pid <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
                            "nativeStopNode called but no node PID is recorded");
        g_status = (g_status == NodeStatus::BINARY_MISSING) ? NodeStatus::BINARY_MISSING : NodeStatus::NOT_RUNNING;
        return;
    }

    if (kill(g_node_pid, SIGTERM) == 0) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "Sent SIGTERM to Digi-Mobile node (PID %d)", g_node_pid);
        int status = 0;
        if (waitpid(g_node_pid, &status, WNOHANG) > 0) {
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "Digi-Mobile node exited with status %d", status);
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "Failed to signal Digi-Mobile node (PID %d): errno=%d", g_node_pid, errno);
    }

    g_node_pid = -1;
    if (g_status != NodeStatus::BINARY_MISSING) {
        g_status = NodeStatus::NOT_RUNNING;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_digimobile_node_DigiMobileNodeController_nativeGetStatus(
        JNIEnv *env, jobject /*thiz*/) {
    if (g_status == NodeStatus::BINARY_MISSING) {
        return env->NewStringUTF("BINARY_MISSING");
    }

    if (g_status == NodeStatus::ERROR) {
        return env->NewStringUTF("ERROR");
    }

    if (g_node_pid > 0) {
        if (IsProcessAlive(g_node_pid)) {
            g_status = NodeStatus::RUNNING;
        } else {
            g_node_pid = -1;
            g_status = NodeStatus::NOT_RUNNING;
        }
    }

    const char *status = (g_status == NodeStatus::RUNNING) ? "RUNNING" : "NOT_RUNNING";

    // A more complete implementation may track start-up phases and use RPC to
    // report sync progress. This placeholder status is intentionally minimal.
    return env->NewStringUTF(status);
}
