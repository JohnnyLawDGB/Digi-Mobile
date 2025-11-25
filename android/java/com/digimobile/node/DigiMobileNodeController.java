package com.digimobile.node;

/**
 * Low-level controller for managing the Digi-Mobile node lifecycle from Android.
 * <p>
 * This wrapper directly delegates to native JNI bindings that start and stop
 * the DigiByte daemon as a child process. Callers should provide configuration
 * and data directory paths that reside within the app's private storage.
 * </p>
 * <p>
 * Threading: starting and stopping the node may perform blocking work (process
 * creation and IPC). Callers should invoke these methods on a background
 * executor rather than the main/UI thread.
 * </p>
 */
public class DigiMobileNodeController {
    static {
        System.loadLibrary("digimobile_jni");
    }

    /**
     * Start the Digi-Mobile node using the provided configuration and data paths.
     * @param configPath absolute path to the node configuration file.
     * @param dataDir absolute path to the node's data directory.
     */
    public void startNode(String configPath, String dataDir) {
        nativeStartNode(configPath, dataDir);
    }

    /**
     * Attempt to terminate the Digi-Mobile node process.
     */
    public void stopNode() {
        nativeStopNode();
    }

    /**
     * Return a coarse-grained lifecycle status for the node.
     * @return one of "NOT_RUNNING", "RUNNING", or a placeholder state while
     * initialization is still in progress.
     */
    public String getStatus() {
        return nativeGetStatus();
    }

    private native void nativeStartNode(String configPath, String dataDir);
    private native void nativeStopNode();
    private native String nativeGetStatus();
}
