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
- [x] **Modular Architecture:** Memecah monolit `MainActivity.kt` (629 baris) menjadi modul-modul terpisah:
  - `id.geraigo.app.ui.theme.Theme.kt` (True Dark OLED `#000000` & `#121212` + Clean Light Material 3)
  - `id.geraigo.app.ui.components.Shimmer.kt` (Skeleton screen shimmer per skill 1.D)
  - `id.geraigo.app.ui.components.CommonUi.kt` (StatusBanner, FloatingNavDock, ScreenHeader, EmptyState, Helper Format)
  - `id.geraigo.app.ui.components.InputTransformation.kt` (RupiahInputTransformation)
  - `id.geraigo.app.ui.screens.CashScreen.kt` (Layar Kas + Skeleton Shimmer)
  - `id.geraigo.app.ui.screens.HistoryScreen.kt` (Riwayat, Pencarian, Filter Kalender, Menu Aksi)
  - `id.geraigo.app.ui.screens.DashboardScreen.kt` (Metrik Finansial, Grafik Garis, Batang, dan Lingkaran)
  - `id.geraigo.app.ui.screens.SettingsScreen.kt` (Pengaturan Toko, Gambar Logo, Kertas, Pajak, Printer, Drive, Ekspor/Impor)
  - `id.geraigo.app.ui.screens.SaleFormDialog.kt` (Form Multi-Item + Autocomplete + `.imePadding()`)
  - `id.geraigo.app.ui.screens.ReceiptDialog.kt` (Pratinjau Struk, Bagikan Teks/Gambar, Simpan File)
  - `id.geraigo.app.ui.screens.PrinterDialog.kt` (Dialog Pemilih Printer Bluetooth Aman)
  - `id.geraigo.app.MainActivity.kt` (Entrypoint ringkas berbasis `collectAsStateWithLifecycle`)
- [x] **Infrastruktur Build & Chroot Standard:** Menambahkan `gradlew` (executable), `gradle/wrapper/` (Gradle 8.9), dan `ksp.useKSP2=false` pada `gradle.properties`.
- [x] **Keyboard Safety:** Menambahkan `.imePadding()` pada `SaleFormDialog` untuk mencegah keyboard virtual menutupi input dan tombol aksi.
- [x] **Pre-flight Linter:** Lolos 100% verifikasi sintaksis `verify_kotlin_syntax.py` (0 Error, 0 Warning).
- [x] **Cloud CI Compilation (GitHub Actions):** Build sukses 100% pada Run ID `42599221707` dalam 2 menit 21 detik. APK Release (`gerai-go-release.apk`) berhasil ditandatangani (v1, v2, v3 signature) dan dipublikasikan ke GitHub Releases tag `ci-latest`.

### 2. Status Proyek Saat Ini
- Proyek versi 1.1.0 (`versionCode 10100`) stabil, bersih, dan siap digunakan langsung.
