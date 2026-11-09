# Product Requirements Document (PRD) - Gerai Go

## 1. Ikhtisar Produk
- **Nama Aplikasi:** Gerai Go
- **Package:** `id.geraigo.app`
- **Tipe:** Point of Sale (POS) / Kasir Offline-First untuk Usaha Mikro, Kecil, dan Menengah (UMKM).
- **Target OS:** Android 8.0 (API 26) hingga Android 15 (API 35).
- **Arsitektur:** Kotlin 2.0 + Jetpack Compose Material 3 + Room SQLite (Offline-First) + WorkManager.

## 2. Fitur Utama
1. **Pencatatan Transaksi & Kas Harian:**
   - Pencatatan transaksi multi-item dalam satu nota (struk).
   - Dukungan item favorit dengan penanda bintang.
   - Autocomplete nama barang dan pencarian harga terakhir dari riwayat transaksi.
   - Ringkasan kas hari ini: total pemasukan, jumlah transaksi, dan rata-rata transaksi.

2. **Riwayat & Pencarian Transaksi:**
   - Filter transaksi berdasarkan tanggal (Date Picker) dan pencarian query (nama barang atau catatan).
   - Pengeditan transaksi tersimpan dan penghapusan nota lengkap dengan dialog konfirmasi.
   - Toggle status favorit nota langsung dari menu konteks.

3. **Dashboard & Analitik Keuangan:**
   - Metrik utama: Pendapatan hari ini, transaksi hari ini, rata-rata transaksi, minggu ini, dan bulan ini.
   - Grafik garis (Line Chart) untuk tren pemasukan harian 7 hari terakhir.
   - Grafik batang (Bar Chart) untuk perbandingan pemasukan mingguan (4 minggu terakhir).
   - Diagram lingkaran (Pie Chart) untuk proporsi kontribusi nilai penjualan per produk teratas.
   - Perhitungan aman dari pembagian nol (`zero division guard`).

4. **Konektivitas Thermal Printer ESC/POS (Bluetooth):**
   - Mendukung printer thermal Bluetooth ukuran kertas 58 mm dan 80 mm.
   - Integrasi aman dengan permission runtime `BLUETOOTH_CONNECT` pada Android 12+ (API 31+).
   - Eksekusi cetak terlindung dengan `Mutex` untuk mencegah tubrukan socket RFCOMM Bluetooth.
   - Layout struk terstandarisasi untuk pratinjau layar, cetak fisik ESC/POS, dan ekspor gambar.

5. **Pengaturan Nota & Identitas Toko:**
   - Kustomisasi nama toko, alamat, kontak, teks header, dan footer nota.
   - Unggah gambar logo toko dan gambar promosi/footer tambahan dengan perlindungan downsampling memori (anti-OOM).
   - Slider pengaturan lebar gambar cetak dan pemilih lebar kertas (58 mm / 80 mm).
   - Konfigurasi persentase pajak dan biaya admin opsional per transaksi baru.

6. **Backup & Sinkronisasi:**
   - Penyimpanan salinan otomatis berkala via WorkManager ke penyimpanan internal sandbox (`automatic-backups/`).
   - Salinan lokal otomatis ke folder `Documents/Gerai Go/` melalui Android MediaStore API.
   - Dukungan sinkronisasi otomatis ke Google Drive berbasis URI dokumen terizin persistabel.
   - Ekspor dan impor manual berkas backup format JSON (`gerai-go-backup`).

7. **Desain & Tema Antarmuka:**
   - Desain anti-AI slop: Bebas emoji dekoratif, menggunakan ikon vektor Material Icons Extended resmi.
   - Dukungan True Neutral / OLED Dark Theme (`#000000`, `#121212`) dan Clean Light Material 3.
   - Skeleton screen dengan shimmer loading untuk transisi data Room yang halus tanpa kedipan.
   - Scaffolding keyboard-safe dengan `.imePadding()` untuk mencegah input tertutup keyboard virtual.
