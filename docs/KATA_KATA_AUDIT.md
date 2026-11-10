# Audit & Inventarisasi Kata-Kata (Copywriting) Gerai Go

> **Tujuan:** Menghapus kata-kata bertele-tele, kaku, formal birokratis, atau dobel fungsi (lebay/slop). Menggantinya dengan bahasa Indonesia sehari-hari yang **singkat, padat, lugas, dan mudah dipahami orang awam/pedagang warung/lapangan**.
>
> *Status: Usulan Perubahan (Belum mengubah kode sumber, menunggu persetujuan).*

---

## Ringkasan Prinsip Standar Kata-Kata
1. **Aplikasi Ini adalah Aplikasi Kasir / Penjualan (POS), Bukan Buku Pencatat Kas.**
   - Singkirkan kata `"Kas"` yang membuat aplikasi terkesan seperti pencatat buku kas keuangan (cashflow logger).
   - Ganti menu utama dan header menjadi **`Penjualan`** atau **`Kasir`**.
2. **Ganti Istilah "Kasbon" Menjadi "Belum Lunas" (atau "Belum Bayar").**
   - Kata *"Kasbon"* terdengar aneh dan tidak sopan jika dicetak di kertas struk yang diserahkan ke pelanggan.
   - Pasangan kata yang paling klop, sopan, dan profesional adalah **`Lunas`** vs **`Belum Lunas`**.
3. **Satu fungsi = Satu kata/frasa singkat.**
   - Tidak ada penjelasan dalam kurung berulang seperti `Lunas (Tunai)` atau `Kasbon (Belum Lunas)`.
4. **Hilangkan kalimat robotik & sloganik.**
   - Hapus kalimat klise seperti *"Disimpan di Lipatan Buku Kasbon digital agar tidak lupa"*.

---

## 1. Layar Penjualan Hari Ini (`CashScreen.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 1 | L48 | `ScreenHeader("Kas hari ini", "Semua transaksi tersimpan di perangkat")` | Kata "Kas" salah citra, subtitle kaku | `ScreenHeader("Penjualan Hari Ini", "Ringkasan transaksi hari ini")` |
| 2 | L67 | `TOTAL PEMASUKAN TUNAI` | Terkesan aplikasi buku kas | `TOTAL PENJUALAN` |
| 3 | L87 | `${todayPaid.size} lunas` | Kurang tegas satuan transaksinya | `${todayPaid.size} transaksi` |
| 4 | L96 | `Kasbon: Rp XX.XXX (N)` | Istilah "Kasbon" diganti | `Belum lunas: Rp XX.XXX (N nota)` |
| 5 | L125 | `Favorit` | Sudah pas | `Favorit` |
| 6 | L142 | `Terbaru` & `${orders.size} transaksi` | Standar | `Transaksi Terakhir` |
| 7 | L149 | `EmptyState("Belum ada transaksi", "Tekan tombol + di bawah untuk mulai mencatat penjualan.")` | Terlalu panjang dan kaku | `EmptyState("Belum Ada Penjualan", "Tekan tombol + untuk mulai transaksi.")` |
| 8 | L188 | Badge: `KASBON` | Terkesan aneh | Badge: `BELUM LUNAS` |

---

## 2. Formulir Transaksi Baru / Edit (`SaleFormDialog.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 9 | L157 | `Transaksi baru` / `Edit transaksi` | Standar | `Transaksi Baru` / `Edit Transaksi` |
| 10 | L160 | `Ketik barang, tekan Enter untuk langsung mengisi harga & jumlah` | Bertele-tele, instruksi kepanjangan | `Ketik nama barang lalu tekan Enter` |
| 11 | L217-218 | Label: `Nama barang`, Placeholder: `Ketik nama barang` | Dobel kata sama | Label: `Nama barang`, Placeholder: `Misal: Beras 1kg, Kopi` |
| 12 | L273 | Label: `Harga · Rp` | Tanda titik tengah agak aneh | `Harga (Rp)` |
| 13 | L297 | Label: `Jumlah` | Standar | `Jumlah` |
| 14 | L327 | `Subtotal barang` | Cukup `Subtotal` | `Subtotal` |
| 15 | L348 | `Tambah barang lain` | Cukup singkat | `+ Tambah Barang` |
| 16 | L357 | `Catatan nota (opsional)` | Terlalu formal | `Catatan (opsional)` |
| 17 | L370 | Header: `Metode & Status Pembayaran` | Terlalu panjang untuk judul grup | `Status Bayar` |
| 18 | L386 | Chip: `Lunas (Tunai)` | Dobel kata | `Lunas` |
| 19 | L393 | Chip: `Kasbon (Belum Lunas)` | Ganti kata kasbon | `Belum Lunas` |
| 20 | L405 | Label: `Nama / Panggilan Pembeli *` | Bertele-tele | `Nama Pelanggan *` |
| 21 | L406 | Placeholder: `Contoh: Pak RT, Mbak Sari, Mas Budi` | Bagus & membumi | `Misal: Pak RT, Mas Budi, Bu Siti` |
| 22 | L413 | `Disimpan di Lipatan Buku Kasbon digital agar tidak lupa.` | Kalimat lebay / jargon AI-slop | *(Dihapus total agar layar kasir bersih)* |
| 23 | L421 | `Uang Tunai Diterima` | Cukup `Uang Diterima` | `Uang Diterima` |
| 24 | L431 | Chip: `Uang Pas` | Sudah tepat & praktis | `Uang Pas` |
| 25 | L447 | Label: `Uang Diterima · Rp` | Tanda titik tengah tidak lazim | `Nominal Diterima (Rp)` |
| 26 | L461 | `Kembalian:` | Sudah tepat | `Kembalian:` |
| 27 | L483 | `Uang Masih Kurang:` | Terlalu bertele-tele | `Kurang:` |
| 28 | L511 | Header Preview: `Pratinjau nota langsung` | Kata `langsung` mubazir | `Pratinjau Struk` |
| 29 | L562 | Row Summary: `Status: KASBON (BELUM LUNAS)` | Ganti istilah kasbon | `Status: BELUM LUNAS` |
| 30 | L564 | Row Summary: `Pelanggan` | Standar | `Pelanggan` |
| 31 | L578 | Error: `Tambahkan minimal satu barang.` | Kaku | `Pilih atau isi minimal 1 barang.` |
| 32 | L579 | Error: `Nama setiap barang wajib diisi.` | Kaku | `Nama barang tidak boleh kosong.` |
| 33 | L580 | Error: `Harga barang harus lebih dari Rp 0.` | Cukup singkat | `Harga barang tidak boleh Rp 0.` |
| 34 | L581 | Error: `Jumlah barang minimal 1.` | Sudah pas | `Jumlah barang minimal 1.` |
| 35 | L582 | Error: `Nama pembeli wajib diisi untuk transaksi kasbon.` | Ganti istilah kasbon | `Nama pelanggan wajib diisi jika belum lunas.` |
| 36 | L618 | Error: `Gagal menyimpan transaksi. Coba lagi.` | Standar | `Gagal menyimpan transaksi.` |
| 37 | L628 | Tombol: `Simpan dan lihat nota` / `Simpan perubahan` | Terlalu panjang | `Simpan & Lihat Struk` / `Simpan Perubahan` |

---

## 3. Layar Riwayat Transaksi (`HistoryScreen.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 38 | L52 | `ScreenHeader("Riwayat", "Cari dan kelola riwayat transaksi")` | Subtitle kaku & formal | `ScreenHeader("Riwayat", "Daftar semua transaksi")` |
| 39 | L58 | Placeholder: `Cari nama barang, pelanggan, atau catatan` | Terlalu panjang memenuhi kolom pencarian | `Cari barang, nama, catatan...` |
| 40 | L82-92 | Filter: `Semua`, `Lunas`, `Kasbon` | Ganti chip `Kasbon` | `Semua`, `Lunas`, `Belum Lunas` |
| 41 | L104 | Filter: `Semua tanggal` | Standar | `Semua Tanggal` |
| 42 | L110 | Filter: `Pilih tanggal` | Standar | `Pilih Tanggal` |
| 43 | L118 | `EmptyState("Tidak ada transaksi ditemukan", "Ubah kata kunci pencarian, status kasbon, atau tanggal filter.")` | Terlalu panjang dan kaku | `EmptyState("Tidak Ada Transaksi", "Coba ubah kata kunci atau tanggal.")` |
| 44 | L149 | Menu Item: `Tandai Sudah Lunas` | Ada kata `Sudah` yang berlebihan | `Tandai Lunas` |
| 45 | L155 | Menu Item: `Lihat nota` | Standar | `Lihat Struk` |
| 46 | L160 | Menu Item: `Edit transaksi` | Standar | `Edit Transaksi` |
| 47 | L166 | Menu Item: `Hapus dari favorit` / `Jadikan favorit` | Standar | `Hapus Favorit` / `Tambah Favorit` |
| 48 | L171 | Menu Item: `Hapus transaksi` | Standar | `Hapus Transaksi` |
| 49 | L195 | Tombol DatePicker: `Hapus filter` | Standar | `Reset Tanggal` |

---

## 4. Pratinjau & Aksi Nota/Struk (`ReceiptDialog.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 50 | L62 | Header: `Nota Tersimpan` | Kasir lebih akrab dengan `Struk` | `Struk Belanja` |
| 51 | L68 | Subtitle: `Pratinjau cetak struk 58 mm` | Kepanjangan | `Kertas ${settings.paperWidth} mm` |
| 52 | L101 | Tombol: `Teks` | Singkat | `Bagikan Teks` |
| 53 | L111 | Tombol: `Gambar` | Singkat | `Bagikan Gambar` |
| 54 | L121 | Tombol: `Simpan` | Singkat | `Simpan Berkas` |
| 55 | L139 | Tombol: `Tandai Sudah Lunas` | Ada kata `Sudah` yang berlebihan | `Tandai Lunas` |
| 56 | L153 | Tombol: `Cetak ke printer Bluetooth` | Terlalu panjang untuk satu tombol utama | `Cetak Struk` |

---

## 5. Cetak & Format Kertas Struk (`ReceiptRenderer.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 57 | L104 | `No. nota` | Istilah kasir | `No. Struk` |
| 58 | L108-109 | `Tanggal` dan `Waktu` | Standar | `Tanggal`, `Waktu` |
| 59 | L111 | `Status: KASBON (BELUM LUNAS)` | "Kasbon" aneh dicetak di struk pembeli | `Status: BELUM LUNAS` |
| 60 | L116 | `Status: LUNAS (TUNAI)` | Dobel kata | `Status: LUNAS` |
| 61 | L113, 118 | `Pelanggan` | Standar | `Pelanggan` |
| 62 | L123 | `Barang (${receipt.items.size})` | Standar | `Daftar Barang (${receipt.items.size})` |
| 63 | L134 | `Ringkasan` | Standar | *(Bisa dihilangkan untuk hemat kertas)* |
| 64 | L135 | `Total kuantitas: X pcs` | Terlalu kaku & bahasa Inggris campur | `Total: X barang` |
| 65 | L136 | `Subtotal` | Standar | `Subtotal` |
| 66 | L138 | `Pajak X%` | Standar | `Pajak X%` |
| 67 | L141 | `Biaya admin X%` | Standar | `Biaya Admin X%` |
| 68 | L145 | `TOTAL BAYAR` | **Tabrakan layout di kertas 58mm jika nominal jutaan** | **Pecah 2 Baris:** Baris 1: `TOTAL`, Baris 2: `Rp 1.500.000` |
| 69 | L147 | `*** BELUM LUNAS (KASBON) ***` | "Kasbon" tidak pantas di struk fisik pembeli | `*** BELUM LUNAS ***` |
| 70 | L149 | `Bayar tunai` | Standar | `Tunai` |
| 71 | L150 | `Kembalian` | Standar | `Kembalian` |

---

## 6. Layar Dashboard & Laporan (`DashboardScreen.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 72 | L65 | `ScreenHeader("Dashboard", "Ringkasan metrik keuangan dan penjualan")` | Bahasa korporat/SaaS kaku | `ScreenHeader("Laporan", "Ringkasan penjualan toko")` |
| 73 | L71 | `Kas masuk hari ini` | Kata "Kas" diganti | `Penjualan Hari Ini` |
| 74 | L72 | `Transaksi hari ini` | Sudah pas | `Transaksi Hari Ini` |
| 75 | L77 | `Rata-rata transaksi` | Standar | `Rata-rata Transaksi` |
| 76 | L78 | `Kasbon belum lunas` | Ganti kata kasbon | `Total Belum Lunas` |
| 77 | L82 | `7 hari terakhir` | Standar | `7 Hari Terakhir` |
| 78 | L83 | `Bulan ini` | Standar | `Bulan Ini` |
| 79 | L87 | `Pemasukan harian · 7 hari` | Terkesan buku kas | `Penjualan 7 Hari` |
| 80 | L92 | `Perbandingan pemasukan mingguan` | Terlalu panjang | `Penjualan Mingguan` |
| 81 | L97 | `Persentase penjualan per item` | Bahasa formal | `Barang Terlaris` |
| 82 | L101 | `Belum ada data penjualan` | Standar | `Belum ada data` |

---

## 7. Navigasi Bawah Dock (`CommonUi.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 83 | L103 | Tab 1: `Kas` | **Aplikasi ini aplikasi kasir/penjualan, bukan pencatat kas!** | **`Penjualan`** (atau **`Kasir`**) |
| 84 | L103 | Tab 2: `Riwayat` | Sudah pas | `Riwayat` |
| 85 | L103 | Tab 3: `Dashboard` | Terlalu bahasa software/SaaS | `Laporan` |
| 86 | L103 | Tab 4: `Pengaturan` | Sudah pas | `Pengaturan` |

---

## 8. Layar Pengaturan (`SettingsScreen.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 87 | L110 | `ScreenHeader("Pengaturan", "Identitas toko, format nota, dan cadangan data")` | Subtitle kepanjangan | `ScreenHeader("Pengaturan", "Toko, struk, dan data")` |
| 88 | L113 | `Informasi Toko` | Standar | `Profil Toko` |
| 89 | L121 | `Tampilan Nota` | Standar | `Format Struk` |
| 90 | L127 | `Logo toko` | Standar | `Logo Toko` |
| 91 | L145 | `Gambar promosi / footer` | Bahasa teknis | `Gambar Catatan Bawah (Footer)` |
| 92 | L153 | `Ukuran gambar cetak` | Standar | `Ukuran Gambar` |
| 93 | L161 | `Lebar kertas printer` | Standar | `Ukuran Kertas Printer` |
| 94 | L179 | `Pajak & Biaya Admin` | Standar | `Pajak & Admin` |
| 95 | L205 | `Printer Bluetooth` | Standar | `Printer Bluetooth` |
| 96 | L212 | `Belum ada printer dipilih` | Standar | `Belum Ada Printer` |
| 97 | L226 | `Pilih printer tersanding` | Kata `tersanding` bahasa buku/terjemahan | `Pilih Printer Bluetooth` |
| 98 | L239 | `Simpan pengaturan nota` | Kepanjangan | `Simpan Pengaturan` |
| 99 | L245 | `Cadangan & Sinkronisasi` | Bahasa agak formal | `Cadangan Data (Backup)` |
| 100 | L278 | `Pilih file backup di Drive` | Standar | `Pilih File Backup` |
| 101 | L301 | `Nonaktifkan Drive` | Standar | `Putus Google Drive` |
| 102 | L317 | `Export JSON` | Teknis | `Cadangkan Data (Export)` |
| 103 | L326 | `Import JSON` | Teknis | `Pulihkan Data (Import)` |
| 104 | L333 | `Pembaruan Aplikasi` | Standar | `Update Aplikasi` |
| 105 | L356 | `Periksa Pembaruan` | Standar | `Cek Versi Baru` |
| 106 | L370 | `Pembaruan Gerai Go` | Standar | `Pembaruan Aplikasi` |
| 107 | L382 | `Memeriksa rilis terbaru...` | Formal | `Memeriksa versi baru...` |
| 108 | L386 | `Versi ${state.versionName} tersedia untuk diunduh.` | Kepanjangan | `Versi ${state.versionName} tersedia.` |
| 109 | L396 | `Catatan Rilis:` | Standar | `Apa yang baru:` |
| 110 | L406 | `Mengunduh pembaruan...` | Standar | `Mengunduh...` |
| 111 | L422 | `Berkas APK pembaruan berhasil diunduh dan lolos verifikasi integritas.` | Sangat kaku & robotik | `Pembaruan siap dipasang.` |
| 112 | L429 | `Aplikasi Gerai Go sudah menggunakan versi paling baru (v${state.currentVersion}).` | Bertele-tele | `Aplikasi sudah versi terbaru (v${state.currentVersion}).` |
| 113 | L436 | `Pemeriksaan gagal: ${state.message}` | Standar | `Gagal memeriksa: ${state.message}` |
| 114 | L468 | Tombol: `Unduh Sekarang` | Standar | `Unduh Update` |
| 115 | L478 | Tombol: `Pasang Pembaruan` | Standar | `Pasang Sekarang` |

---

## 9. Notifikasi & Banner Status (`MainActivity.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 116 | L129 | `Izin Bluetooth ditolak. Aktifkan izin Bluetooth di Pengaturan Android.` | Kepanjangan | `Izin Bluetooth belum aktif.` |
| 117 | L143 | `Nota berhasil disimpan ke berkas teks.` | Kaku | `Nota disimpan ke file teks.` |
| 118 | L145 | `Nota gagal disimpan. Coba lagi.` | Standar | `Gagal menyimpan nota.` |
| 119 | L178 | `Nota berhasil dicetak.` | Standar | `Struk berhasil dicetak.` |
| 120 | L180 | `Cetak gagal. Periksa koneksi printer atau pilih printer lain.` | Kepanjangan | `Gagal mencetak. Cek koneksi printer.` |
| 121 | L235 | `Nota berhasil ditandai LUNAS.` | Dobel kata | `Status diubah jadi LUNAS.` |
| 122 | L303 | `Transaksi kasbon$nameStr berhasil ditandai LUNAS.` | Kepanjangan | `Tagihan$nameStr sudah LUNAS.` |
| 123 | L319 | `Pengaturan berhasil disimpan.` | Standar | `Pengaturan disimpan.` |
| 124 | L321 | `Pengaturan gagal disimpan.` | Standar | `Gagal menyimpan pengaturan.` |
| 125 | L331 | `Backup berhasil disimpan.` | Standar | `Backup berhasil disimpan.` |
| 126 | L333 | `Backup gagal disimpan.` | Standar | `Gagal menyimpan backup.` |
| 127 | L351 | `Data berhasil dipulihkan dari backup.` | Kaku | `Data berhasil dipulihkan.` |
| 128 | L353 | `Data gagal dipulihkan. Pastikan format berkas benar.` | Kaku | `Gagal memulihkan data. Format file salah.` |
| 129 | L370 | Dialog: `Hapus transaksi?` | Standar | `Hapus Transaksi?` |
| 130 | L373 | `${target.items.size} barang dalam nota ini akan dihapus permanen.` | Agak formal | `Nota ini akan dihapus permanen.` |
| 131 | L409 | `Printer Bluetooth berhasil disimpan.` | Standar | `Printer berhasil disimpan.` |
| 132 | L420 | `Nota gagal dicetak. Pastikan printer menyala dan kertas tersedia.` | Kepanjangan | `Gagal cetak. Pastikan printer nyala & kertas ada.` |

---

## 10. Dialog Pilih Printer (`PrinterDialog.kt`)

| No | Lokasi Baris | Kata-Kata Saat Ini (Lama) | Masalah / Evaluasi | Usulan Kata-Kata Baru (Rekomendasi) |
|---|---|---|---|---|
| 133 | L29 | `Pilih Printer Bluetooth` | Standar | `Pilih Printer Bluetooth` |
| 134 | L33 | `Pastikan printer ESC/POS (58/80 mm) sudah dipasangkan di Pengaturan Bluetooth Android.` | Terlalu teknis & formal | `Pastikan printer sudah terhubung di Bluetooth HP Anda.` |
| 135 | L41 | `Belum ada perangkat printer tersanding yang ditemukan.` | Kata `tersanding` kaku | `Belum ada printer Bluetooth yang terhubung.` |
| 136 | L67 | `Tutup` | Singkat | `Tutup` |

