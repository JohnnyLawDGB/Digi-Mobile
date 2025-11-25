# Deploying Digi-Mobile artifacts to Android

## Using adb (debuggable devices)
1. Build and bundle artifacts:
   ```bash
   ARCH=arm64-v8a ANDROID_NDK_ROOT=/path/to/ndk ./scripts/build-android.sh
   ARCH=arm64-v8a ./scripts/make-android-rootfs.sh
   ```
2. Push bundle to device:
   ```bash
   adb push android/dist/arm64-v8a /data/local/tmp/digi-mobile
   ```
3. Shell into device and run the daemon:
   ```bash
   adb shell
   cd /data/local/tmp/digi-mobile/bin
   ./digibyted -conf=/data/local/tmp/digi-mobile/config/android-pruned.conf -datadir=/data/local/tmp/digi-data
   ```

## Using Termux (on-device build/run)
- Install NDK and dependencies via Termux packages if you want native builds.
- Alternatively, push the prebuilt bundle as above and run it directly in Termux shell.

## Service integration notes
- For production apps, wrap the daemon with an Android Service that manages lifecycle and foreground notifications.
- Use `rpcbind=127.0.0.1` and `rpcallowip=127.0.0.1` to restrict access; communicate via localhost RPC or a JNI shim.
- Persist data in app-private storage (`/data/data/<app>/files/.digibyte`) with pruning enabled.
