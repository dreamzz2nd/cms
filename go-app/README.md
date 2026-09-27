# 🐱 Nyamimo - High-Performance Anime Streaming Engine & CMS

<p align="center">
  <strong>Platform Web Streaming Anime Modern, Ultra Cepat & Ringan Dibangun dengan Golang, HTMX, Tailwind CSS & Alpine.js</strong>
</p>

---

## ✨ Fitur Unggulan

### 🎬 1. Modern Hybrid Video Player
* **Pemutar Video Hybrid**: Mendukung format Artplayer (MP4/HLS M3U8) dan fallback native iFrame tanpa error.
* **Lewati Intro (+85s)**: Fitur 1-klik skip anime intro (+85 detik) dan hotkey keyboard (`S` / `I`).
* **Multi-Provider & Quality Switcher**: Server otomatis (Blogspot, Pixeldrain, VIP Streaming, Gdrive) dan pilihan kualitas (360p, 480p, 720p, 1080p).
* **Toggle Auto Switch Server**: Pengguna bebas menyalakan atau mematikan fitur auto fallback server.
* **Hotkey Keyboard Lengkap**: Spasi (Play/Pause), Panah Kiri/Kanan (+-10 detik), `F` (Layar Penuh).
* **Navigasi Episode Terintegrasi**: Tombol *Prev/Next Episode* dan scrollable episode selector responsif.

### 💰 2. Ad Placement Manager (Monetisasi Siap Pakai)
* Atur semua slot iklan langsung dari **Dashboard Admin**:
  * **Header Top Banner** (728x90 / Responsive).
  * **Slot Bawah Video Player** (CTR tertinggi).
  * **Popunder / Injeksi Script Direct Link**.
  * **Footer Sticky Banner**.
* Kompatibel dengan semua jaringan iklan: **Google AdSense, Adsterra, Monetag, PropellerAds, Yllix**, maupun custom banner HTML.

### ⚙️ 3. Konfigurasi Mudah (`config.json` & Admin Panel)
* Ganti Nama Website, Slogan/Tagline, URL Logo, Warna Aksen, dan Endpoint API Scraper langsung dari Admin Panel tanpa perlu sentuh kode Go atau kompilasi ulang.
* Dukungan Environment Variables (`PORT`, `SITE_NAME`, `API_BASE_URL`).

### 🛡️ 4. Administrator & User Authentication
* **Admin Dashboard Interaktif**: Pantau total anime realtime, status server uptime, kelola akun pengguna, dan bersihkan cache API.
* **Carousel Hero Banner Manager**: Tambah dan atur banner beranda lengkap dengan fitur **Cari Wallpaper HD Otomatis**.
* **Sistem Bookmark & Watch History**: Pengguna dapat menyimpan anime favorit dan melihat riwayat episode terakhir yang ditonton.

### 🚀 5. Performa Super Ringan (Ultra-Low RAM)
* Dibuat dengan **Go (Golang)**: Konsumsi memori sangat hemat (< 30-50MB RAM), sanggup melayani ribuan pengunjung simultan pada VPS $3/bulan.
* Render instan menggunakan **HTMX** (zero page reload).
* **SEO Teroptimasi**: Meta tags lengkap, OpenGraph, Twitter Cards, dan Schema.org JSON-LD otomatis.

---

## 🛠️ Tech Stack

* **Backend Engine**: Go (Golang 1.22)
* **Frontend Rendering**: Go HTML Templates + HTMX
* **Styling**: Tailwind CSS + Vanilla CSS Custom Design Tokens
* **Interaktivitas**: Alpine.js + Lucide Icons + Swiper.js + Artplayer.js
* **Storage Requirement**: **0 GB (Zero Storage)** — Semua media video ter-resolve otomatis via API.

---

## 🚀 Panduan Cepat Menjalankan

### Windows (Lokal):
Klik ganda file `nyamimo-server.exe` atau jalankan di terminal:
```cmd
.\nyamimo-server.exe
```
Buka browser di `http://localhost:8080`.

### Linux VPS / Docker / Cloud:
Lihat panduan lengkap langkah-demi-langkah di file **[PANDUAN_SETUP.md](PANDUAN_SETUP.md)**.

---

## 👑 Akun Default Administrator
* **URL**: `http://localhost:8080/admin`
* **Username**: `admin`
* **Password**: `admin123`

---

## 📄 Lisensi & Disclaimer
Projek ini dibuat untuk tujuan edukasi dan manajemen situs streaming media. Pengguna/pembeli bertanggung jawab penuh atas konfigurasi API dan konten yang disajikan melalui sistem ini.
