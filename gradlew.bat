@echo off
setlocal
set GRADLE_BINARY=%GRADLE_BINARY%
if "%GRADLE_BINARY%"=="" set GRADLE_BINARY=gradle
where %GRADLE_BINARY% >nul 2>&1
if errorlevel 1 (
  echo Gradle is not installed. Please install Gradle or set GRADLE_BINARY to a valid Gradle executable.
  exit /b 1
)
"%GRADLE_BINARY%" %*
