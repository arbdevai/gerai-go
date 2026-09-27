# Gerai Go

Aplikasi kasir Android offline-first untuk usaha kecil. Dibangun dengan Kotlin, Jetpack Compose Material 3, dan Room SQLite.

## Menjalankan

Workflow GitHub Actions membangun APK release setelah perubahan didorong ke branch `main`/`master`, atau saat workflow dijalankan manual. Build dijalankan di GitHub Actions, bukan di perangkat lokal.

## Fitur MVP

- Pencatatan dan edit transaksi, favorit, autocomplete serta harga terakhir dari riwayat.
- Database Room offline, pencarian, filter tanggal, hapus, nota tersimpan, preview, simpan teks nota, dan bagikan nota.
- Pengaturan identitas nota, header/footer, gambar logo dan gambar tambahan, opsi tampil, ukuran gambar, dan lebar printer 58/80 mm.
- Cetak printer Bluetooth ESC/POS, dashboard pemasukan dan jumlah/rata-rata transaksi, grafik garis, batang mingguan, dan pie item.
- Export/import backup JSON berisi transaksi, pengaturan, dan gambar nota.
- GitHub Actions hanya menjalankan `assembleRelease` (tanpa unit test), memakai cache Gradle, lalu menerbitkan APK installable ke GitHub Releases.
- Repo dikonfigurasi dengan signing secrets rilis tetap supaya setiap APK bisa menjadi pembaruan untuk instalasi sebelumnya.

## Catatan MVP

Cetak thermal memerlukan printer ESC/POS yang telah dipasangkan dari pengaturan Bluetooth Android. Untuk distribusi update lintas build, konfigurasi secrets keystore rilis tetap sebelum merilis ke pengguna.
