# 📱 Arsitektur Full Native Android Nyamimo (Bilibili Style)

Aplikasi Android Nyamimo kini telah bertransformasi menjadi **100% Full Native Android Application** menggunakan Kotlin, Google Media3 ExoPlayer, ViewPager2, RecyclerView, Glide, dan Material 3 Theme.

---

## 🏗️ 1. Struktur Modul & Komponen Native (`android/`):

1. **`com.nyamimo.app.model`**:
   - `HomeResponse`, `AnimeItem`, `AnimeDetailData`, `EpisodeItem`, `PlayerOption`, dll.
2. **`com.nyamimo.app.api.ApiClient`**:
   - OkHttp3 + Gson untuk fetching data REST API V1 asinkron secepat kilat.
3. **`com.nyamimo.app.adapter`**:
   - `BannerAdapter`: Carousel ViewPager2 atas (Auto-scroll setiap 5 detik dengan gradien overlay gelap & badge score).
   - `GenreChipAdapter`: Horizontal category pill chips untuk filter instan.
   - `AnimeGridAdapter`: 2-Kolom Grid anime dengan poster beresolusi tinggi, tag rating bintang emas, dan tag episode.
   - `EpisodeGridAdapter`: Pill/chip selector episode 5-kolom di halaman detail anime.
4. **`com.nyamimo.app.MainActivity`**:
   - Beranda Native Bilibili-style (Top search bar, banner carousel, ongoing & completed grid, pull-to-refresh).
   - Panel pencarian instan (search overlay).
   - Bottom Navigation Bar Material3 (Beranda, Jelajah, Riwayat, Profil).
5. **`com.nyamimo.app.AnimeDetailActivity`**:
   - Parallax backdrop header, cover card, sinopsis rapi, daftar episode, dan tombol tonton langsung.
6. **`com.nyamimo.app.PlayerActivity`**:
   - **Google Media3 (ExoPlayer)** Native Player untuk streaming direct MP4/HLS.
   - Accelerated WebView container fallback untuk server iframe/embed.
   - Fullscreen auto landscape, kontrol rasio aspek (Fit / Zoom Fullscreen).

---

## ⚡ 2. Endpoint REST API V1 (Server Go):
- `GET /api/v1/home` -> Data banner, anime ongoing, completed, dan kategori genres.
- `GET /api/v1/anime/{slug}` -> Data detail anime, sinopsis, list episode, rekomendasi.
- `GET /api/v1/episode?detail_eps=...&title=...&ep=...` -> Data streaming video, resolusi, server switcher.
- `GET /api/v1/search?q=...` -> Pencarian anime instan.

---

## 📦 3. Lokasi File APK:
- `android/app/build/outputs/apk/debug/app-debug.apk`
