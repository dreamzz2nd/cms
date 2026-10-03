# 🐱 Panduan Setup & Menjalankan Website Nyamimo

Panduan ringkas untuk menjalankan website **Nyamimo** di komputer lokal.

---

## 📋 1. Prasyarat
Pastikan **Go (Golang)** sudah terpasang di komputer Anda (versi 1.22 atau lebih baru).

Cek versi Go di Terminal / Command Prompt:
```bash
go version
```

---

## 📂 2. Masuk ke Direktori Project
Buka Terminal / PowerShell / CMD, lalu masuk ke folder project:
```bash
cd c:\Users\user\nyamimo
```

---

## 📦 3. Unduh Dependency
Jalankan perintah berikut untuk mengunduh semua modul yang diperlukan:
```bash
go mod download
```

---

## 🚀 4. Jalankan Website
Jalankan server aplikasi utama:
```bash
go run main.go
```

Setelah server aktif, akan muncul keterangan:
```text
🚀 [NYAMIMO SERVER] Berjalan di port :3000
🌐 Buka di browser: http://localhost:3000
```

---

## 🌐 5. Buka di Browser
Buka browser dan akses URL berikut:
* **Website Utama**: [http://localhost:3000](http://localhost:3000)
* **Panel Admin / Dashboard**: [http://localhost:3000/admin/dashboard](http://localhost:3000/admin/dashboard)

---

## 📱 6. Akses dari HP / Tablet (Satu Jaringan Wi-Fi)
Untuk membuka website dari perangkat lain (HP / Tablet) yang berada di satu jaringan Wi-Fi, akses melalui IP komputer Anda:
```text
http://192.168.100.245:3000
```
