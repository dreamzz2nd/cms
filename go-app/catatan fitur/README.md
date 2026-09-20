# 📚 Direktori Catatan Fitur Nyamimo

Direktori ini berisi seluruh catatan dokumentasi teknis, arsitektur, panduan fungsional, dan pedoman pemeliharaan untuk setiap fitur baru yang dikembangkan pada proyek **Nyamimo Anime Stream**.

---

## 📌 Indeks Catatan Fitur

| No | Dokumen Fitur | Status | Deskripsi Singkat |
|---|---|---|---|
| 01 | [01_custom_video_player.md](./01_custom_video_player.md) | ✅ Aktif | Pemutar Video Kustom Hybrid (Artplayer.js + Iframe Fail-Safe Fallback, Lewati Intro +85s, Speed Control, Hotkeys, Memory Playback) |
| 02 | [02_modal_auth_and_admin_dashboard.md](./02_modal_auth_and_admin_dashboard.md) | ✅ Aktif | Modal Autentikasi In-Place (Login/Register tanpa redirect), Dashboard Admin `/admin`, dan Manajemen Cache |

---

## 🛡️ Prinsip Utama Pengembangan (Zero Regression)
1. **Keandalan Mutlak**: Dilarang merusak, mengubah, atau menghapus fitur yang sudah berjalan dan disetujui sebelumnya.
2. **Fail-Safe Fallback**: Setiap implementasi kustom harus memiliki mekanisme fallback otomatis agar layanan tidak pernah macet atau crash bagi pengguna.
3. **Penyelarasan Ganda (Dual-Sync)**: Seluruh perubahan template dan logika wajib diselaraskan pada direktori root (`./`) dan subdirektori deploy (`./go-app/`).
