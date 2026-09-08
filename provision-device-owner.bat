@echo off
echo Pastikan perangkat baru/factory reset, USB debugging aktif, dan APK sudah terpasang.
adb shell dpm set-device-owner id.sch.ujian.kiosk/.KioskDeviceAdminReceiver
pause
