set up
---


Cek versi Go di Terminal / Command Prompt:
```bash
go version
```

---

Buka Terminal / PowerShell / CMD, lalu masuk ke folder project:
```bash
cd c:\Users\user\nyamimo
```

---

Jalankan perintah berikut untuk mengunduh semua modul yang diperlukan:
```bash
go mod download
```

---

#Jalankan server aplikasi utama:
```bash
go run main.go
```

Setelah server aktif, akan muncul keterangan:
```text
🚀 [NYAMIMO SERVER] Berjalan di port :3000
🌐 Buka di browser: http://localhost:3000
```

---

Buka browser dan akses URL berikut:
* **Website Utama**: [http://localhost:3000](http://localhost:3000)
* **Panel Admin / Dashboard**: [http://localhost:3000/admin/dashboard](http://localhost:3000/admin/dashboard)

---

## 📱 6. Akses dari HP / Tablet (Satu Jaringan Wi-Fi)
Untuk membuka website dari perangkat lain (HP / Tablet) yang berada di satu jaringan Wi-Fi, akses melalui IP komputer Anda:
```text
http://192.168.100.245:3000
```
