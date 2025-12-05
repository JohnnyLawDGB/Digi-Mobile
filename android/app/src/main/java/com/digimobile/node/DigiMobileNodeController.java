package com.digimobile.node;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;

/**
 * Low-level controller for managing the Digi-Mobile node lifecycle from Android.
 *
 * This wrapper directly delegates to native JNI bindings that start and stop
 * the DigiByte daemon as a child process. Callers should provide configuration
 * and data directory paths that reside within the app's private storage.
 *
 * Threading: starting and stopping the node may perform blocking work (process
 * creation and IPC). Callers should invoke these methods on a background
 * executor rather than the main/UI thread.
 */
public class DigiMobileNodeController {
    private static final String TAG = "DigiMobileNode";
    private static final boolean nativeLoaded;

    static {
        boolean loaded;
        try {
            System.loadLibrary("digimobile_jni");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library digimobile_jni is missing", e);
            loaded = false;
        }
        nativeLoaded = loaded;
    }

    /**
     * Start the Digi-Mobile node using the provided configuration and data paths.
     * The daemon binary is unpacked from assets into {@code filesDir/bin} before execution.
     *
     * @param context    application or service context for asset access.
     * @param configPath absolute path to the node configuration file.
     * @param dataDir    absolute path to the node's data directory.
     */
    public void startNode(Context context, String configPath, String dataDir) {
        ensureNativeLoaded();
        AssetManager assets = context.getAssets();
        String filesDir = context.getFilesDir().getAbsolutePath();
        String daemonPath = new File(context.getFilesDir(), "bin/digibyted").getAbsolutePath();
        Log.i(TAG,
                "Launching digibyted at " + daemonPath + " with datadir=" + dataDir + " and conf=" + configPath);
        nativeStartNode(assets, configPath, dataDir, filesDir);

        String status = nativeGetStatus();
        if ("BINARY_MISSING".equals(status)) {
            throw new IllegalStateException(
                    "Missing digibyted asset. Rebuild with scripts/build-android.sh to stage assets/bin/digibyted-arm64.");
        }
        if ("ERROR".equals(status)) {
            throw new IllegalStateException("Native node startup failed with status=ERROR");
        }
    }

    /**
     * Attempt to terminate the Digi-Mobile node process.
     */
    public void stopNode() {
        ensureNativeLoaded();
        nativeStopNode();
    }

    /**
     * Return a coarse-grained lifecycle status for the node.
     *
     * @return one of "NOT_RUNNING", "RUNNING", "BINARY_MISSING", or "ERROR".
     */
    public String getStatus() {
        if (!nativeLoaded) {
            return "JNI missing";
        }
        return nativeGetStatus();
    }

    private void ensureNativeLoaded() {
        if (!nativeLoaded) {
            throw new IllegalStateException("Native library digimobile_jni is not available");
        }
    }

    private native void nativeStartNode(AssetManager assetManager, String configPath, String dataDir, String filesDir);
    private native void nativeStopNode();
    private native String nativeGetStatus();
}
