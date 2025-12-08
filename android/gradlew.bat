@echo off
rem Helper wrapper to invoke the repository Gradle wrapper from within the android\ directory.
set SCRIPT_DIR=%~dp0
set REPO_ROOT=%SCRIPT_DIR%..\
"%REPO_ROOT%gradlew.bat" %*
