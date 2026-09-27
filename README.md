# Gerai Go

Aplikasi kasir Android offline-first untuk usaha kecil. Dibangun dengan Kotlin, Jetpack Compose Material 3, dan Room SQLite.

## Menjalankan

Workflow GitHub Actions membangun APK release setelah perubahan didorong ke branch `main`/`master`, atau saat workflow dijalankan manual. Build dijalankan di GitHub Actions, bukan di perangkat lokal.

## Fitur yang sudah tersedia

- Pencatatan transaksi cepat dengan nama bebas, harga, jumlah, catatan, total otomatis, dan saran dari riwayat.
- Data transaksi offline dengan Room, daftar/pencarian riwayat, hapus, dan preview nota.
- Dashboard pendapatan hari ini, mingguan, bulanan, jumlah transaksi, grafik tren, dan ringkasan item.
- Navigasi Kas, Riwayat, Dashboard, dan Pengaturan.
- GitHub Actions hanya menjalankan `assembleRelease` (tanpa unit test), memakai cache Gradle, dan mengunggah APK sebagai artifact selama 30 hari.
- Tambahkan secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, dan `ANDROID_KEY_PASSWORD` untuk tanda tangan rilis yang konsisten. Tanpa secrets, CI tetap menandatangani APK dengan kunci sementara agar artifact bisa dipasang untuk dicoba.

## Catatan MVP

Pengaturan toko saat ini berupa rancangan UI lokal; penyimpanan persistennya, backup/import, pemilihan gambar logo, cetak dan bagikan nota, filter kalender, edit transaksi, serta grafik kategori penuh perlu dilanjutkan sebelum rilis produksi. Integrasi Bluetooth 58/80 mm juga belum diaktifkan.
