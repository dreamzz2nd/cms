# 📱 Nyamimo Anime Stream - Android App (Native WebView Shell)

Project Android resmi Nyamimo Anime Stream dengan arsitektur **Native WebView Shell (Kotlin)** yang ultra-ringan (~3MB), responsif, dan hemat memori.

---

## 🚀 Keunggulan Arsitektur Pisah Folder (`android/`):

1. **Ultra-Ringan & Cepat (~3MB APK)**: Tidak memerlukan framework berat seperti React Native / Flutter yang menghasilkan APK 40MB+.
2. **Otomatis Sinkron dengan Website**: Setiap update anime, fitur, atau perbaikan di server Go langsung muncul di aplikasi Android tanpa pengguna harus download update APK baru di Play Store.
3. **Hardware Acceleration**: Rendering 60 FPS mulus dengan akselerasi GPU perangkat Android.
4. **Player Video Fullscreen Landscape**: Otomatis berputar ke mode landscape saat tombol fullscreen video ditekan (Immersive Mode).
5. **Native Download Manager**: Tombol download episode anime terhubung langsung dengan sistem unduhan Android bawaan (*Notification Download Manager*).
6. **Swipe-To-Refresh**: Tarik ke bawah untuk memuat ulang halaman.
7. **Deteksi Offline & Tombol Coba Lagi**: Tampilan offline modern jika internet terputus.

---

## 🛠 Panduan Menjalankan & Build APK di Android Studio

### 1. Buka Project di Android Studio
1. Buka aplikasi **Android Studio**.
2. Pilih **Open** / **Buka Project**.
3. Arahkan ke folder: `c:\Users\user\nyamimo\android`
4. Tunggu Gradle Sync selesai mengunduh dependencies.

### 2. Mengatur URL Target (Lokal vs Production)
Buka file: [`android/app/src/main/res/values/strings.xml`](file:///c:/Users/user/nyamimo/android/app/src/main/res/values/strings.xml)
- **Untuk Production (Live)**:
  ```xml
  <string name="web_url">https://nyamimo.onrender.com</string>
  ```
- **Untuk Testing Emulator Android Studio**:
  ```xml
  <string name="web_url">http://10.0.2.2:3000</string>
  ```
- **Untuk Testing HP Asli (via Wi-Fi yang sama)**:
  ```xml
  <string name="web_url">http://IP_KOMPUTER_ANDA:3000</string>
  ```

### 3. Build APK Siap Pasang
- Di menu atas Android Studio, klik: **`Build`** ➔ **`Build Bundle(s) / APK(s)`** ➔ **`Build APK(s)`**.
- Setelah selesai, klik **`locate`** untuk mengambil file `app-debug.apk` atau `app-release.apk`.
- Kirim file `.apk` ke HP Android Anda dan instal!
