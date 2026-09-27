@echo off
set "JAVA_HOME=C:\Program Files\Unity\Hub\Editor\6000.4.1f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK"
set "ANDROID_HOME=C:\Users\user\AppData\Local\Android\Sdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ===================================================
echo   Building Nyamimo Android App APK (Release/Debug)
echo ===================================================

cd /d "%~dp0"
call "gradle_dist\gradle-8.5\bin\gradle.bat" assembleDebug --stacktrace

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================
    echo   BUILD BERHASIL! File APK tersedia di:
    echo   android\app\build\outputs\apk\debug\app-debug.apk
    echo ===================================================
) else (
    echo.
    echo [ERROR] Gagal membuild APK. Periksa log di atas.
)
