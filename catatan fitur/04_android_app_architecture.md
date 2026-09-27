# Catatan Fitur: Aplikasi Android Nyamimo (Native WebView Shell)

## Keputusan Arsitektur: Pisah Folder (`android/`)
Memisahkan folder project Android dari root backend Go adalah pendekatan standar industri terbaik (*Clean Separation of Concerns*):
- **Root Project (`c:/Users/user/nyamimo`)**: Fokus pada Go Server, HTMX SSR, Template engine, API Client, dan JSON Database.
- **Folder `android/`**: Fokus pada Android Studio project (Gradle, Kotlin, AndroidManifest, Proguard, Asset Resource).

## Fitur-Fitur Android Native Shell:
1. **PWA Manifest & Service Worker Cache**: Mendukung installability dan caching aset statis secara cepat.
2. **Hardware Accelerated WebView**: Render animasi 60 FPS pada low-end dan high-end devices.
3. **HTML5 Fullscreen Landscape Handler**: Menggunakan `WebChromeClient` custom untuk menangani pemutar video fullscreen lanskap otomatis.
4. **Native Download Manager**: Integrasi dengan `DownloadManager` Android untuk mengunduh episode anime.
5. **Swipe-to-Refresh & Back Navigation**: Navigasi tombol kembali pintar (exit confirmation 2 detik & back history) serta pull-to-refresh.
