# Status Pengembangan & State Persisten - Gerai Go

## Tanggal Pembaruan: 2026-11-09

### 1. Riwayat Audit & Perbaikan Tuntas (Completed)
- [x] **Room KSP2 Stability:** Mengubah semua fungsi DAO Room suspend `DELETE` dan `UPDATE` (`deleteReceipt`, `delete`, `clear`, `update`) agar mengembalikan tipe `: Int`. Menambahkan `exportSchema = false` untuk meniadakan warning compiler.
- [x] **Dead Code Cleanup:** Menghapus query mati yang tidak pernah dipanggil (`search`, `latest`, `insert(sale)` tunggal).
- [x] **Bluetooth Runtime Permission:** Menambahkan helper `ThermalPrinter.hasBluetoothPermission()` dan guard aman saat mengakses `bondedDevices` serta `device.name` di Android 12+ (API 31+). Menghilangkan potensi crash `SecurityException`.
- [x] **Printer Concurrency & Safety:** Memasang proteksi `Mutex` tunggal pada operasi cetak ESC/POS (`ThermalPrinter.print`) untuk mencegah benturan socket RFCOMM Bluetooth.
- [x] **RupiahInputTransformation Fix:** Memperbaiki algoritma `OffsetMapping` dengan array prekomputasi bidireksional bound-checked. Menghilangkan crash fatal `IllegalArgumentException: OffsetMapping.originalToTransformed returned invalid mapping`.
- [x] **Anti-OOM Image Loading:** Memperbaiki `ReceiptRenderer.loadImage()` dan `BackupCodec.encode()` dengan kalkulasi `inSampleSize` downsampling. Mengatasi lonjakan memori 40-150 MB saat kasir memilih foto logo beresolusi tinggi.
- [x] **Receipt Layout Formatting:** Memperbaiki fungsi `wrapMultiline` agar mempertahankan karakter baris baru `\n`, menghilangkan double-wrap, menghitung lebar teks berbasis piksel riil, dan mencetak dot pemisah monokrom solid untuk printer thermal.
- [x] **Dashboard Math Safety:** Memasang zero-division guard pada Pie Chart dan rasio Bar/Line Chart. Menjaga integritas data saat transaksi Rp 0.
- [x] **Data Integrity & Relasi:** Memperbaiki resolusi `taxPercent` dan `adminPercent` pada `OrderReceipt` menggunakan `maxOfOrNull` agar tidak terpengaruh urutan pengembalian item dari query database.
- [x] **Auto Backup Resiliency:** Memperbaiki relative path MediaStore dokumen, pembersihan backup lama berbasis regex, dan mengembalikan `Result.retry()` saat offline/gagal agar WorkManager melakukan retry otomatis.
- [x] **Modular Architecture:** Memecah monolit `MainActivity.kt` (629 baris) menjadi modul-modul terpisah.
- [x] **Infrastruktur Build & Chroot Standard:** Menambahkan `gradlew` (executable), `gradle/wrapper/` (Gradle 8.9), dan `ksp.useKSP2=false` pada `gradle.properties`.
- [x] **Pre-flight Linter:** Lolos 100% verifikasi sintaksis `verify_kotlin_syntax.py` (0 Error, 0 Warning).

### 2. Fitur Baru & Peningkatan UX Kasir Cepat (v1.2.0)
- [x] **Alur Kasir Cepat (Enter Key Navigation):**
  - Input Nama Barang $\rightarrow$ tekan Enter $\rightarrow$ otomatis lompat ke input Harga.
  - Input Harga $\rightarrow$ tekan Enter $\rightarrow$ otomatis lompat ke input Jumlah.
  - Input Jumlah $\rightarrow$ tekan Enter $\rightarrow$ otomatis menambah baris barang baru dan fokus ke nama barang selanjutnya tanpa perlu klik manual.
- [x] **Live Thermal Preview Real-time:** Menampilkan kertas struk thermal 58/80 mm langsung di bawah formulir input barang dan catatan secara real-time saat kasir mengetik.
- [x] **In-App Updater System:**
  - Mengimplementasikan `AppUpdateManager` berbasis OkHttp streaming terisolasi dengan file sementara `.part` (buffer 64 KB).
  - Pengecekan rilis GitHub Releases otomatis via API dengan perbandingan SemVer numerik hierarkis (`Major -> Minor -> Patch`) terhadap `BuildConfig.VERSION_NAME`.
  - Verifikasi integritas APK 4 lapis (`isApkValid`: physical size > 8MB, deep DEX & signing certs parse, exact package name match, valid version code).
  - Pemicuan installer aman via `FileProvider` + permission guard `canRequestPackageInstalls()`.
  - UI pembaruan di `SettingsScreen`: kartu versi aplikasi, dialog unduh ber-progress bar realtime, dan tombol instalasi instan.
- [x] **Standardisasi Rilis GitHub & Changelog Dinamis:**
  - Menambahkan `.github/scripts/generate_release_notes.py` untuk ekstraksi riwayat git dan tabel checksum SHA-256 otomatis.
  - Workflow GitHub Actions otomatis membuat tag versi rilis resmi (`v1.2.0`) dengan aset APK bertanda tangan (`gerai-go-v1.2.0.apk`), serta memperbarui rolling tag `ci-latest`.

### 3. Status Proyek Saat Ini
- Versi: **1.2.0** (`versionCode 10200`).
- Siap build dan rilis via GitHub Actions CI.
