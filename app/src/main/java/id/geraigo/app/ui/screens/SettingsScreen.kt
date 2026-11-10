package id.geraigo.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.geraigo.app.BuildConfig
import id.geraigo.app.backup.AutoBackup
import id.geraigo.app.backup.BackupCodec
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import id.geraigo.app.printer.ThermalPrinter
import id.geraigo.app.ui.components.ScreenHeader
import id.geraigo.app.ui.components.dateTimeLabel
import id.geraigo.app.updater.AppUpdateManager
import id.geraigo.app.updater.UpdateUiState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun SettingsScreen(
    activity: ComponentActivity,
    orders: List<OrderReceipt>,
    settings: StoreSettings,
    onChoosePrinter: () -> Unit,
    onNotice: (String) -> Unit,
    onSave: (StoreSettings) -> Unit,
    onBackup: (String, Uri) -> Unit,
    onRestore: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var model by remember(settings) { mutableStateOf(settings) }

    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var updateDialogOpen by remember { mutableStateOf(false) }

    val chooseLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            model = model.copy(logoUri = it.toString(), showLogo = true)
        }
    }

    val chooseExtra = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            model = model.copy(extraImageUri = it.toString(), showExtraImage = true)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            onBackup(BackupCodec.encode(context, orders.flatMap { it.items }, model), uri)
        }
    }

    val driveSetup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                AutoBackup.setDriveUri(context, uri)
            }.onSuccess {
                onNotice("File backup tersambung. Sinkronisasi otomatis dijadwalkan.")
            }.onFailure {
                onNotice("Akses file backup tidak dapat disimpan. Pilih lokasi Drive kembali.")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestore(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Pengaturan", "Toko, struk, dan data")

        // Store identity
        Text("Profil Toko", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        SettingInput("Nama toko", model.name) { model = model.copy(name = it) }
        SettingInput("Alamat", model.address, singleLine = false) { model = model.copy(address = it) }
        SettingInput("Nomor kontak", model.contact, keyboard = KeyboardType.Phone) { model = model.copy(contact = it) }

        Spacer(Modifier.height(16.dp))

        // Receipt layout
        Text("Format Struk", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        SettingInput("Teks header", model.header, singleLine = false) { model = model.copy(header = it) }
        SettingInput("Footer nota", model.footer, singleLine = false) { model = model.copy(footer = it) }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Logo toko", Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            if (model.logoUri.isNotBlank()) {
                AsyncImage(
                    model.logoUri,
                    contentDescription = "Logo toko",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            TextButton(onClick = { chooseLogo.launch(arrayOf("image/*")) }) {
                Text("Pilih gambar")
            }
        }
        SettingSwitch("Tampilkan logo pada nota", model.showLogo) { model = model.copy(showLogo = it) }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Gambar promosi / footer", Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            TextButton(onClick = { chooseExtra.launch(arrayOf("image/*")) }) {
                Text("Pilih gambar")
            }
        }
        SettingSwitch("Tampilkan gambar tambahan", model.showExtraImage) { model = model.copy(showExtraImage = it) }

        Spacer(Modifier.height(8.dp))
        Text("Ukuran gambar cetak", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Slider(
            value = model.imageWidth.toFloat(),
            onValueChange = { model = model.copy(imageWidth = it.toInt()) },
            valueRange = 96f..384f,
            steps = 5
        )

        Text("Lebar kertas printer", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = model.paperWidth == 58,
                onClick = { model = model.copy(paperWidth = 58) },
                label = { Text("58 mm") }
            )
            FilterChip(
                selected = model.paperWidth == 80,
                onClick = { model = model.copy(paperWidth = 80) },
                label = { Text("80 mm") }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Tax & Admin Fees
        Text("Pajak & Admin", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        SettingSwitch("Aktifkan pajak", model.taxEnabled) {
            model = model.copy(taxEnabled = it)
            onNotice(if (it) "Pajak aktif untuk transaksi baru setelah disimpan." else "Pajak nonaktif untuk transaksi baru.")
        }
        if (model.taxEnabled) {
            SettingPercent("Persentase pajak", model.taxPercent) { model = model.copy(taxPercent = it) }
        }

        SettingSwitch("Aktifkan biaya admin", model.adminFeeEnabled) {
            model = model.copy(adminFeeEnabled = it)
            onNotice(if (it) "Biaya admin aktif untuk transaksi baru setelah disimpan." else "Biaya admin nonaktif untuk transaksi baru.")
        }
        if (model.adminFeeEnabled) {
            SettingPercent("Persentase biaya admin", model.adminFeePercent) { model = model.copy(adminFeePercent = it) }
        }
        Text(
            "Nilai pajak dan admin berlaku untuk transaksi baru. Transaksi lama tetap memakai nilai saat dibuat.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Bluetooth Printer
        Text("Printer Bluetooth", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        val pairedName = remember(model.printerAddress) {
            runCatching {
                ThermalPrinter.pairedDevices(context).firstOrNull { it.address == model.printerAddress }?.name
            }.getOrNull()
        }
        Text(
            if (model.printerAddress.isBlank()) "Belum ada printer dipilih" else pairedName ?: model.printerAddress,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        OutlinedButton(
            onClick = onChoosePrinter,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.BluetoothSearching, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pilih Printer Bluetooth")
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSave(model) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Simpan Pengaturan")
        }

        Spacer(Modifier.height(24.dp))

        // Backup
        Text("Cadangan Data (Backup)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        Text(
            "${orders.size} transaksi · Salinan otomatis berkala tersimpan di Documents/Gerai Go.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(Modifier.height(10.dp))
        Text("Google Drive", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        val driveStatus = AutoBackup.driveStatus(context)
        val driveMessage = when {
            AutoBackup.driveUri(context) == null -> "Belum tersambung. Buat/pilih berkas di Google Drive untuk mengaktifkan sinkronisasi otomatis."
            driveStatus == "ok" -> "Tersambung · Terakhir disinkronkan ${dateTimeLabel(AutoBackup.lastSync(context))}"
            driveStatus.isNotBlank() -> "Status sinkronisasi: $driveStatus"
            else -> "Tersambung · Sinkronisasi berjalan otomatis saat jaringan internet aktif."
        }
        Text(
            driveMessage,
            color = if (driveStatus.isNotBlank() && driveStatus != "ok") Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        OutlinedButton(
            onClick = { driveSetup.launch("gerai-go-backup.json") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (AutoBackup.driveUri(context) == null) "Pilih file backup di Drive" else "Ganti file backup di Drive")
        }

        if (AutoBackup.driveUri(context) != null) {
            TextButton(
                onClick = {
                    runCatching {
                        val uri = AutoBackup.driveUri(context)
                        if (uri != null) {
                            context.contentResolver.releasePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                        AutoBackup.setDriveUri(context, null)
                    }.onSuccess {
                        onNotice("Sinkronisasi Google Drive dinonaktifkan.")
                    }.onFailure {
                        onNotice("Akses Drive tidak dapat dilepas.")
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Nonaktifkan Drive")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { exportLauncher.launch("gerai-go-backup.json") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export JSON")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import JSON")
            }
        }

        Spacer(Modifier.height(24.dp))

        // In-App Updater
        Text("Pembaruan Aplikasi", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        Text(
            "Versi saat ini: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        OutlinedButton(
            onClick = {
                updateDialogOpen = true
                scope.launch {
                    updateState = UpdateUiState.Checking
                    updateState = AppUpdateManager.checkUpdate(context)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cek Versi Baru")
        }

        Spacer(Modifier.height(100.dp))
    }

    if (updateDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (updateState !is UpdateUiState.Downloading) {
                    updateDialogOpen = false
                }
            },
            title = {
                Text("Pembaruan Aplikasi", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    when (val state = updateState) {
                        UpdateUiState.Idle, UpdateUiState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Memeriksa versi baru...", fontSize = 13.sp)
                            }
                        }
                        is UpdateUiState.Available -> {
                            Text("Versi ${state.versionName} tersedia.", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            val apkSizeMb = String.format(Locale.US, "%.1f", state.apkSize / (1024f * 1024f))
                            Text(
                                "Ukuran: $apkSizeMb MB",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            if (state.releaseNotes.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Apa yang baru:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(
                                    state.releaseNotes,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4
                                )
                            }
                        }
                        is UpdateUiState.Downloading -> {
                            Text("Mengunduh pembaruan...", fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            val pct = (state.progress * 100).toInt()
                            val downloadedMb = String.format(Locale.US, "%.1f", state.downloadedBytes / (1024f * 1024f))
                            val totalMb = String.format(Locale.US, "%.1f", state.totalBytes / (1024f * 1024f))
                            Text(
                                "$pct% ($downloadedMb MB / $totalMb MB)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is UpdateUiState.ReadyToInstall -> {
                            Text(
                                "Pembaruan siap dipasang.",
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        is UpdateUiState.UpToDate -> {
                            Text(
                                "Aplikasi sudah versi terbaru (v${state.currentVersion}).",
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        is UpdateUiState.Error -> {
                            Text(
                                "Pemeriksaan gagal: ${state.message}",
                                fontSize = 13.sp,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (val state = updateState) {
                    is UpdateUiState.Available -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    updateState = UpdateUiState.Downloading(0f, 0L, state.apkSize)
                                    val result = AppUpdateManager.downloadApk(
                                        context = context,
                                        downloadUrl = state.downloadUrl,
                                        expectedBytes = state.apkSize
                                    ) { progress, downloaded, total ->
                                        updateState = UpdateUiState.Downloading(progress, downloaded, total)
                                    }
                                    result.onSuccess { apkFile ->
                                        updateState = UpdateUiState.ReadyToInstall(apkFile, state.versionName)
                                    }.onFailure { exc ->
                                        updateState = UpdateUiState.Error(exc.message ?: "Gagal mengunduh APK.")
                                    }
                                }
                            }
                        ) {
                            Text("Unduh Update")
                        }
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Button(
                            onClick = {
                                AppUpdateManager.installApk(context, state.apkFile)
                                updateDialogOpen = false
                            }
                        ) {
                            Text("Pasang Sekarang")
                        }
                    }
                    else -> {
                        if (updateState !is UpdateUiState.Downloading) {
                            TextButton(onClick = { updateDialogOpen = false }) {
                                Text("Tutup")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (updateState is UpdateUiState.Available) {
                    TextButton(onClick = { updateDialogOpen = false }) {
                        Text("Nanti")
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingPercent(label: String, value: Double, onValue: (Double) -> Unit) {
    var raw by remember(label, value) { mutableStateOf(if (value == 0.0) "" else value.toString()) }
    OutlinedTextField(
        value = raw,
        onValueChange = { input ->
            val cleaned = input.replace(',', '.').filter { it.isDigit() || it == '.' }
            raw = cleaned
            val parsed = cleaned.toDoubleOrNull()
            if (cleaned.isBlank()) onValue(0.0) else if (parsed != null) onValue(parsed.coerceIn(0.0, 100.0))
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        label = { Text(label) },
        suffix = { Text("%") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        singleLine = true
    )
}

@Composable
private fun SettingInput(
    label: String,
    value: String,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard)
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
