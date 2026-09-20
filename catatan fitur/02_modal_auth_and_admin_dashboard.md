# 🔐 Catatan Fitur: Modal Autentikasi In-Place & Admin Dashboard

- **Tanggal Implementasi**: 19 September 2026
- **Status**: ✅ Selesai & Terverifikasi
- **Komponen Terdampak**: `templates/layout.html`, `templates/navbar.html`, `templates/admin_dashboard.html`, `main.go`, `client/api.go` (dan salinan di `go-app/`)

---

## 1. Modal Autentikasi In-Place (Login & Register)
- **Tujuan**: Menghilangkan pengalihan halaman ke `/login` atau `/register`, menjaga pengguna tetap berada di halaman anime aktif yang sedang mereka jelajahi.
- **Mekanisme**:
  - Modal Alpine.js di `layout.html` mengelola state `loginView: 'options' | 'form' | 'register'`.
  - Tombol "Registrasi Sekarang" beralih langsung ke tampilan form pendaftaran di dalam modal tanpa redirect halaman.
  - Endpoint `/login` dan `/register` diarahkan kembali ke halaman asal (Referer) dengan query `show_login=true` atau `show_register=true`.

---

## 2. Admin Dashboard & Manajemen Server (`/admin`)
- **Tujuan**: Memberikan pusat kendali visual bagi administrator Nyamimo (`admin` / `admin123`).
- **Fitur Dashboard**:
  1. **Metric Cards**: Total Pengguna Terdaftar, Cache System Status, Anime Database Count, Uptime Server.
  2. **Hero Carousel Manager**: Pratinjau banner anime unggulan.
  3. **Manajemen Pengguna**: Tabel daftar pengguna terdaftar dengan aksi hapus pengguna.
  4. **Pembersih Cache (Flush API Cache)**: Tombol pembersihan cache in-memory secara instan via `/api/admin/clear-cache`.
