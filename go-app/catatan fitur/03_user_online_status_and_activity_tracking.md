# Fitur Status Online/Offline & Riwayat Aktivitas Pengguna (Admin Dashboard)

## Deskripsi Fitur
Fitur ini memungkinkan administrator memantau status aktivitas seluruh pengguna secara *real-time* di panel **Admin Dashboard (Manajemen Pengguna)**:
1. **Status Sedang Online**: Pengguna yang sedang membuka website ditandai dengan badge hijau beranimasi radar ping (*pulse*) dan indikator titik hijau di foto profil.
2. **Status Offline**: Pengguna yang sedang tidak membuka website ditandai dengan badge abu-abu dan keterangan durasi relatif kapan terakhir kali online (misalnya: *X detik yang lalu, X menit yang lalu, X jam yang lalu, X hari yang lalu*).
3. **Kartu Statistik Ringkasan**:
   - Total Pengguna Terdaftar
   - Pengguna Sedang Online (Aktif Saat Ini)
   - Pengguna Offline (Tidak Aktif)
4. **Filter & Pencarian Instan (Real-Time)**:
   - Filter cepat tab: `Semua`, `Online`, `Offline`.
   - Pencarian instan berdasarkan Nama, Username, dan Email.
5. **Heartbeat Otomatis & Throttle**:
   - Endpoint `/api/user/heartbeat` mengirimkan sinyal detak aktivitas setiap 25 detik saat tab aktif.
   - Throttle penyimpanan ke `data/users.json` untuk menjaga performa server tetap ringan.

---

## File Terkait
- `main.go` & `go-app/main.go`: Struktur `User.LastSeenAt`, helper `isUserOnline`, `formatLastSeen`, `formatLastSeenExact`, handler `handleUserHeartbeat`, sorting status pengguna.
- `templates/layout.html` & `go-app/templates/layout.html`: Script pengirim detak aktivitas pengguna berkala (*heartbeat*).
- `templates/admin_dashboard.html` & `go-app/templates/admin_dashboard.html`: Antarmuka manajemen pengguna dengan indikator online/offline, kartu metrik, dan filter pencarian.
- `data/users.json`: Penyimpanan riwayat timestamp `last_seen_at`.
