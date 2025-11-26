package com.digimobile.node;

import android.content.Context;
import android.content.res.AssetManager;

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
    static {
        System.loadLibrary("digimobile_jni");
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
        AssetManager assets = context.getAssets();
        String filesDir = context.getFilesDir().getAbsolutePath();
        nativeStartNode(assets, configPath, dataDir, filesDir);
    }

    /**
     * Attempt to terminate the Digi-Mobile node process.
     */
    public void stopNode() {
        nativeStopNode();
    }

    /**
     * Return a coarse-grained lifecycle status for the node.
     *
     * @return one of "NOT_RUNNING", "RUNNING", "BINARY_MISSING", or "ERROR".
     */
    public String getStatus() {
        return nativeGetStatus();
    }

    private native void nativeStartNode(AssetManager assetManager, String configPath, String dataDir, String filesDir);
    private native void nativeStopNode();
    private native String nativeGetStatus();
}
