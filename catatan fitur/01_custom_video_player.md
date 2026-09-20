# 🎬 Catatan Fitur: Pemutar Video Kustom Hybrid (Nyamimo Custom Player)

- **Tanggal Implementasi**: 19 September 2026
- **Status**: ✅ Selesai & Terverifikasi
- **Komponen Terdampak**: `templates/anime_detail.html`, `templates/partials/modal_player.html`, `templates/layout.html`, `main.go` (dan salinan di `go-app/`)

---

## 1. Latar Belakang & Tujuan
Nyamimo menyajikan pengalaman streaming anime subtitle Indonesia modern. Sebelumnya, video disematkan hanya menggunakan iframe default dari penyedia pihak ketiga. Fitur **Pemutar Video Kustom Hybrid** ini dibangun untuk:
1. Memberikan antarmuka pemutar video eksklusif Nyamimo dengan tema warna khas (`#FFCC00` & dark obsidian `#17171B`).
2. Menghadirkan fitur-fitur spesifik anime seperti tombol **"Lewati Intro (+85s)"**, **pengaturan kecepatan (0.5x - 2.0x)**, **pemilihan resolusi**, **Picture-in-Picture (PiP)**, dan **pintasan keyboard**.
3. Menjamin **100% Zero Regression** melalui arsitektur hybrid dengan fail-safe auto fallback, sehingga video dari server terproteksi tetap dapat diputar tanpa pernah mengalami error atau layar hitam.

---

## 2. Arsitektur Hybrid & Fail-Safe Fallback

```
                    ┌───────────────────────────────┐
                    │  Sumber Streaming Video API   │
                    └───────────────┬───────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │     Evaluasi Tipe Stream      │
                    └───────┬───────────────┬───────┘
                            │               │
        [Direct MP4/M3U8/Pixeldrain]        [Iframe Provider Terproteksi]
                            │               │
                            ▼               ▼
            ┌──────────────────────┐ ┌───────────────────────────┐
            │   Artplayer Engine   │ │ Iframe Fallback Responsif │
            │  (#nyamimo-artplayer)│ │ (#player-iframe-container)│
            └──────────┬───────────┘ └─────────────┬─────────────┘
                       │                           │
                       │ (Jika Decode Error)       │
                       └───────────────►───────────┘
                                       │
                                       ▼
                       ┌───────────────────────────┐
                       │  Unified Control Overlay  │
                       │  - Lewati Intro (+85s)    │
                       │  - Speed (0.5x - 2.0x)    │
                       │  - Quality Selector       │
                       │  - Fullscreen (F)         │
                       │  - Auto-Switch Watchdog   │
                       └───────────────────────────┘
```

---

## 3. Rincian Fitur Utama

### A. Lewati Intro (+85s)
- **Fungsi**: Memajukan pemutaran video sebanyak 85 detik (durasi rata-rata opening anime standar) secara instan.
- **Aksesibilitas**:
  - Tombol aksi mengambang (*floating pill*) di sudut kanan atas pemutar.
  - Tombol pintas di bilah kontrol bawah.
  - **Hotkey Keyboard**: Tombol `S` atau `I`.

### B. Pengaturan Kecepatan Pemutaran (Playback Speed)
- Pilihan kecepatan: `0.5x`, `0.75x`, `1.0x` (Normal), `1.25x`, `1.5x`, `2.0x`.
- Tampilan dropup elegan dengan indikator aktif berwarna kuning `#FFCC00`.

### C. Pemilih Kualitas & Resolusi Video
- Pilihan: `Auto (Optimal)`, `1080p (Full HD)`, `720p (HD)`, `480p (SD)`, `360p (Hemat Data)`.
- Terintegrasi langsung dengan perpindahan provider/server streaming HTMX tanpa me-reload seluruh halaman.

### D. Auto-Switch Server Watchdog
- Mengawasi kesehatan streaming selama 7 detik pertama.
- Jika server mengalami kendala jaringan atau diblokir provider, watchdog otomatis memindahkan pemutaran ke server berikutnya dan menampilkan toast notifikasi status.

### E. Memori Posisi Tonton (Resume Playback)
- Menyimpan posisi detik terakhir pemutaran ke dalam `localStorage` browser.
- Otomatis melanjutkan pemutaran saat pengguna kembali membuka episode tersebut.

### F. Pintasan Keyboard (Hotkeys)
- `Space` / `K`: Putar / Jeda (Play/Pause)
- `Panah Kiri` / `J`: Mundur 10 detik
- `Panah Kanan` / `L`: Maju 10 detik
- `S` / `I`: Lewati Intro (+85s)
- `F`: Buka / Tutup Layar Penuh (Fullscreen)

---

## 4. Jaminan Zero Regression
- **Kompatibilitas Penuh**: Semua tombol provider streaming (`hx-get="/api/video-url"`), daftar download episode, breadcrumb navigasi, dan switch episode tetap berjalan normal 100%.
- **Mobile Responsive**: Pemutar dan tombol kontrol didesain fleksibel untuk layar smartphone hingga desktop layar lebar.
