# Resolving Gradle Wrapper Merge Conflicts

When `gradlew` or the wrapper properties change (for example during a version bump), merges can leave conflict markers. Use the following checklist to fix them safely.

## 1. Inspect the working tree
```
git status
rg "^<<<<<<<" gradle gradlew gradlew.bat
```
If the working tree shows `unmerged files`, open each for markers.

## 2. Restore known-good wrapper scripts
The wrapper scripts are auto-generated and should match the version in `gradle/wrapper/gradle-wrapper.properties`.
```
./gradlew wrapper --gradle-version <version from properties>
```
This rewrites `gradlew`, `gradlew.bat`, and the `gradle/wrapper/gradle-wrapper.jar` to consistent content, eliminating most conflicts.

## 3. Resolve properties conflicts manually
Open `gradle/wrapper/gradle-wrapper.properties` and reconcile the `distributionUrl` or checksum lines. Keep a single, correct `distributionUrl` pointing at the desired Gradle version, e.g.
```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip
```
Remove conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) and save.

## 4. Mark files as resolved
```
git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.jar
```
Verify that `git status` now reports a clean working tree aside from any staged changes.

## 5. Re-run the wrapper task (optional but recommended)
After resolving, regenerate the wrapper to ensure scripts match the properties file:
```
./gradlew wrapper
```

## 6. Commit
```
git commit -m "chore: resolve gradle wrapper merge conflict"
```
Then retry your pull or push.

## Troubleshooting
- If `./gradlew` fails to run, ensure it is executable: `chmod +x gradlew`.
- If the JAR file remains conflicted, delete it and re-run `./gradlew wrapper` to regenerate.
