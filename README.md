# Ujian Kiosk Android

Aplikasi WebView untuk membuka alamat ujian (URL/domain/IP lokal) dan mengunci perangkat sekolah pada aplikasi. Minimum Android 9 (API 28).

## Fitur

- URL ujian dapat berupa `https://domain...` atau `http://192.168.x.x:port`.
- Panel admin lokal tersembunyi.
- PIN admin disimpan sebagai hash SHA-256, bukan teks biasa.
- Tombol Back dinonaktifkan, tampilan layar penuh, dan tautan selain HTTP/HTTPS diblokir.
- Lock Task/kiosk penuh bila aplikasi dijadikan **Device Owner**.
- HTTP lokal diizinkan untuk server ujian pada jaringan sekolah.

## Build APK

1. Buka folder ini di Android Studio (JDK 17).
2. Tunggu Gradle Sync selesai.
3. Pilih **Build > Build APK(s)**.
4. APK debug berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Build APK gratis melalui GitHub Actions

File konfigurasi sudah tersedia di `.github/workflows/build-apk.yml`.

1. Buat repository baru di GitHub.
2. Upload **isi folder `UjianKiosk`**, bukan folder ZIP-nya. Pastikan folder `.github` ikut terunggah.
3. Buka tab **Actions** pada repository.
4. Pilih workflow **Build APK Android**.
5. Tekan **Run workflow**, lalu tekan tombol hijau **Run workflow**.
6. Tunggu proses selesai dan berwarna hijau.
7. Buka hasil build, lalu pada bagian **Artifacts** unduh `UjianKiosk-debug-apk`.
8. Ekstrak hasil unduhan untuk mendapatkan `app-debug.apk`.

Build juga berjalan otomatis setiap kali perubahan dikirim ke branch `main` atau `master`. Artifact disimpan selama 14 hari.

## Instalasi kiosk penuh (perangkat sekolah)

> Menjadikan Device Owner mensyaratkan perangkat baru/factory reset tanpa akun yang sudah disiapkan. Cadangkan data terlebih dahulu.

1. Build lalu pasang APK pada perangkat Android 9+.
2. Aktifkan USB debugging dan sambungkan ADB.
3. Jalankan:

   `adb shell dpm set-device-owner id.sch.ujian.kiosk/.KioskDeviceAdminReceiver`

4. Buka aplikasi. Saat pertama kali, kode admin awal adalah `123456`.
5. Masukkan URL/IP ujian dan segera ubah kode admin.

Jika perintah Device Owner gagal karena perangkat sudah pernah disiapkan, lakukan factory reset lalu ulangi sebelum menambahkan akun.

## Cara admin membuka dan keluar

- Tekan dan tahan tulisan **UJIAN** di pojok kiri atas selama 4 detik.
- Masukkan kode admin.
- Admin dapat mengganti alamat, mengganti kode, memuat ulang halaman, atau keluar aplikasi.

## Catatan keamanan

- Tanpa Device Owner, Android hanya menjalankan screen pinning/Lock Task terbatas dan pengguna mungkin masih dapat keluar melalui kontrol sistem.
- Gunakan HTTPS untuk ujian melalui internet. HTTP diaktifkan hanya agar IP server LAN tetap dapat dibuka.
- Untuk produksi, ganti application ID/nama sekolah dan gunakan APK release yang ditandatangani.
