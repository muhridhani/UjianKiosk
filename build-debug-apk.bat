@echo off
echo Jalankan file ini dari Android Studio Terminal setelah Gradle Sync selesai.
call gradlew.bat assembleDebug
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
