#include <jni.h>
#include <android/log.h>
#include <errno.h>
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
};

// Track the currently running node process ID (if any).
static pid_t g_node_pid = -1;
static NodeStatus g_status = NodeStatus::NOT_RUNNING;

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

bool IsExecutable(const std::string &path) {
    struct stat info {};
    return stat(path.c_str(), &info) == 0 && (info.st_mode & S_IXUSR);
}

std::string FindNodeBinary(const std::string &data_dir) {
    // Prefer well-known locations under the app's data directory but fall back
    // to a plain executable name to allow PATH resolution in the shell.
    const std::vector<std::string> candidates = {
            data_dir + "/bin/digibyted",
            data_dir + "/digibyted",
            "/data/local/tmp/digibyted",
            "digibyted",
    };

    for (const auto &candidate : candidates) {
        if (IsExecutable(candidate)) {
            return candidate;
        }
    }

    return {};
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_digimobile_node_DigiMobileNodeController_nativeStartNode(
        JNIEnv *env, jobject /*thiz*/, jstring j_config_path, jstring j_data_dir) {
    std::string config_path = ToStdString(env, j_config_path);
    std::string data_dir = ToStdString(env, j_data_dir);

    if (config_path.empty() || data_dir.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "Config path or data dir is empty; refusing to start node");
        return;
    }

    if (g_node_pid > 0 && IsProcessAlive(g_node_pid)) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "nativeStartNode called but node already running with PID %d", g_node_pid);
        g_status = NodeStatus::RUNNING;
        return;
    }

    std::string binary_path = FindNodeBinary(data_dir);
    if (binary_path.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
                            "Node binary not found near %s – Digi-Mobile core not installed yet",
                            data_dir.c_str());
        g_status = NodeStatus::BINARY_MISSING;
        g_node_pid = -1;
        return;
    }

    // Build a minimal command. In production we expect tighter control and
    // potentially to embed the daemon as a library rather than spawning a child.
    std::string command = binary_path + " -conf=\"" + config_path + "\" -datadir=\"" + data_dir + "\"";

    pid_t child_pid = fork();
    if (child_pid == 0) {
        // Child process: execute the daemon via /system/bin/sh for compatibility.
        execlp("/system/bin/sh", "sh", "-c", command.c_str(), static_cast<char *>(nullptr));
        _exit(127);  // If execlp fails.
    } else if (child_pid > 0) {
        g_node_pid = child_pid;
        g_status = NodeStatus::RUNNING;
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "Started Digi-Mobile node with PID %d", g_node_pid);
    } else {
        g_status = NodeStatus::NOT_RUNNING;
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
        // Non-blocking reap attempt to avoid zombie processes.
        int status = 0;
        if (waitpid(g_node_pid, &status, WNOHANG) > 0) {
            __android_log_print(ANDROID_LOG_INFO, kLogTag,
                                "Digi-Mobile node exited with status %d", status);
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
