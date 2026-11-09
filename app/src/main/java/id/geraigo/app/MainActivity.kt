package id.geraigo.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import id.geraigo.app.backup.AutoBackup
import id.geraigo.app.backup.BackupCodec
import id.geraigo.app.data.GeraiDatabase
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import id.geraigo.app.printer.ThermalPrinter
import id.geraigo.app.ui.components.FloatingNavDock
import id.geraigo.app.ui.components.StatusBanner
import id.geraigo.app.ui.components.sameDay
import id.geraigo.app.ui.screens.*
import id.geraigo.app.ui.theme.GeraiGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = GeraiDatabase.get(this)
        AutoBackup.ensurePeriodic(this)
        setContent {
            GeraiGoTheme {
                GeraiApp(activity = this, db = db)
            }
        }
    }
}

@Composable
private fun GeraiApp(activity: ComponentActivity, db: GeraiDatabase) {
    val dao = db.sales()
    val settingsDao = db.settings()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val salesState by dao.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val settingsState by settingsDao.observe().collectAsStateWithLifecycle(initialValue = null)
    val favorites by dao.observeFavorites().collectAsStateWithLifecycle(initialValue = emptyList())

    val sales = salesState ?: emptyList()
    val settings = settingsState ?: StoreSettings()
    val isLoading = salesState == null

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var editingOrder by remember { mutableStateOf<OrderReceipt?>(null) }
    var quickItem by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<OrderReceipt?>(null) }
    var deleteTarget by remember { mutableStateOf<OrderReceipt?>(null) }
    var pendingPrint by remember { mutableStateOf<OrderReceipt?>(null) }
    var showPrinters by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var statusError by remember { mutableStateOf(false) }
    var statusEvent by remember { mutableIntStateOf(0) }

    BackHandler(enabled = receipt != null || formOpen) {
        if (receipt != null) {
            receipt = null
        } else {
            formOpen = false
        }
    }

    fun report(message: String, error: Boolean = false) {
        statusMessage = message
        statusError = error
        statusEvent++
    }

    LaunchedEffect(statusEvent) {
        if (statusMessage.isNotBlank()) {
            delay(if (statusError) 5000L else 3200L)
            statusMessage = ""
        }
    }

    // Auto backup local copy on sales/settings update
    LaunchedEffect(sales, settingsState) {
        if (salesState != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    AutoBackup.saveLocal(
                        context,
                        BackupCodec.encode(context, sales, settings).toByteArray(Charsets.UTF_8)
                    )
                }
            }
            if (AutoBackup.driveUri(context) != null) {
                AutoBackup.request(context)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showPrinters = true
        } else {
            report("Izin Bluetooth ditolak. Aktifkan izin Bluetooth di Pengaturan Android.", true)
        }
    }

    val saveNoteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val currentReceipt = receipt
        if (uri != null && currentReceipt != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(buildReceiptText(currentReceipt, settings).toByteArray())
                } ?: error("Lokasi file tidak dapat dibuka")
            }.onSuccess {
                report("Nota berhasil disimpan ke berkas teks.")
            }.onFailure {
                report("Nota gagal disimpan. Coba lagi.", true)
            }
        }
    }

    val orders = remember(sales) {
        sales.groupBy { it.receiptKey.ifBlank { it.id.toString() } }
            .map { (key, items) -> OrderReceipt(key, items) }
            .sortedByDescending { it.createdAt }
    }

    val filtered = remember(orders, query, selectedDate) {
        orders.filter { order ->
            val matchQuery = query.isBlank() ||
                order.items.any { it.name.contains(query, ignoreCase = true) || it.note.contains(query, ignoreCase = true) } ||
                order.key.contains(query, ignoreCase = true)
            val matchDate = selectedDate == null || sameDay(order.createdAt, selectedDate!!)
            matchQuery && matchDate
        }
    }

    fun openPrinter(saleToPrint: OrderReceipt?) {
        pendingPrint = saleToPrint
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else if (saleToPrint != null && settings.printerAddress.isNotBlank()) {
            scope.launch {
                runCatching {
                    ThermalPrinter.print(context, settings.printerAddress, saleToPrint, settings)
                }.onSuccess {
                    report("Nota berhasil dicetak.")
                }.onFailure {
                    report("Cetak gagal. Periksa koneksi printer atau pilih printer lain.", true)
                }
            }
        } else {
            showPrinters = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (receipt == null && !formOpen) {
                FloatingNavDock(
                    selected = tab,
                    onSelected = { tab = it },
                    onAdd = {
                        editingOrder = null
                        quickItem = null
                        formOpen = true
                    }
                )
            }
        }
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedVisibility(
                visible = statusMessage.isNotBlank(),
                enter = fadeIn(tween(170)) + expandVertically(tween(170)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
            ) {
                StatusBanner(statusMessage, statusError) { statusMessage = "" }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    receipt != null -> {
                        val activeReceipt = receipt!!
                        ReceiptDialog(
                            sale = activeReceipt,
                            settings = settings,
                            onClose = { receipt = null },
                            onPrint = { openPrinter(activeReceipt) },
                            onShareText = { shareReceiptText(context, activeReceipt, settings) },
                            onShareImage = { shareReceiptImage(context, activeReceipt, settings) },
                            onSave = { saveNoteLauncher.launch("nota-${activeReceipt.key}.txt") }
                        )
                    }
                    formOpen -> {
                        SaleFormDialog(
                            dao = dao,
                            initial = editingOrder,
                            quickItem = quickItem,
                            settings = settings,
                            onClose = { formOpen = false },
                            onSaved = { savedOrder ->
                                receipt = savedOrder
                                formOpen = false
                                quickItem = null
                            }
                        )
                    }
                    else -> {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { slideDistance -> slideDistance / 32 }) togetherWith
                                    (fadeOut(tween(130)) + slideOutHorizontally(tween(130)) { slideDistance -> -slideDistance / 32 })
                            },
                            label = "tab-navigation"
                        ) { currentTab ->
                            when (currentTab) {
                                0 -> CashScreen(
                                    orders = orders,
                                    favorites = favorites,
                                    isLoading = isLoading,
                                    onFavorite = { name ->
                                        scope.launch {
                                            val price = dao.latestPrice(name) ?: 0L
                                            quickItem = name to price
                                            editingOrder = null
                                            formOpen = true
                                        }
                                    },
                                    onReceipt = { receipt = it }
                                )
                                1 -> HistoryScreen(
                                    orders = filtered,
                                    query = query,
                                    selectedDate = selectedDate,
                                    onQuery = { query = it },
                                    onDate = { selectedDate = it },
                                    onReceipt = { receipt = it },
                                    onEdit = { order ->
                                        editingOrder = order
                                        quickItem = null
                                        formOpen = true
                                    },
                                    onDelete = { deleteTarget = it },
                                    onToggleFavorite = { order ->
                                        scope.launch {
                                            val newFav = !order.items.all { it.favorite }
                                            order.items.forEach { saleItem ->
                                                dao.update(saleItem.copy(favorite = newFav))
                                            }
                                        }
                                    }
                                )
                                2 -> DashboardScreen(orders = orders)
                                else -> SettingsScreen(
                                    activity = activity,
                                    orders = orders,
                                    settings = settings,
                                    onChoosePrinter = { openPrinter(null) },
                                    onNotice = { report(it) },
                                    onSave = { updatedSettings ->
                                        scope.launch {
                                            runCatching {
                                                settingsDao.save(updatedSettings)
                                            }.onSuccess {
                                                report("Pengaturan berhasil disimpan.")
                                            }.onFailure {
                                                report("Pengaturan gagal disimpan.", true)
                                            }
                                        }
                                    },
                                    onBackup = { jsonString, uri ->
                                        runCatching {
                                            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                                                outStream.write(jsonString.toByteArray(Charsets.UTF_8))
                                            } ?: error("Lokasi penyimpanan tidak dapat dibuka")
                                        }.onSuccess {
                                            report("Backup berhasil disimpan.")
                                        }.onFailure {
                                            report("Backup gagal disimpan.", true)
                                        }
                                    },
                                    onRestore = { uri ->
                                        scope.launch {
                                            runCatching {
                                                val raw = context.contentResolver.openInputStream(uri)
                                                    ?.bufferedReader()?.use { it.readText() }
                                                    ?: error("File tidak dapat dibaca")
                                                val (restoredSales, restoredSettings) = BackupCodec.decode(context, raw)
                                                db.withTransaction {
                                                    dao.clear()
                                                    dao.insertAll(restoredSales)
                                                    if (restoredSettings != null) {
                                                        settingsDao.save(restoredSettings)
                                                    }
                                                }
                                            }.onSuccess {
                                                report("Data berhasil dipulihkan dari backup.")
                                            }.onFailure {
                                                report("Data gagal dipulihkan. Pastikan format berkas benar.", true)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus transaksi?", fontSize = 17.sp) },
            text = {
                Text(
                    "${target.items.size} barang dalam nota ini akan dihapus permanen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            dao.deleteReceipt(target.key)
                        }
                        deleteTarget = null
                    }
                ) {
                    Text("Hapus", color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showPrinters) {
        PrinterDialog(
            onDismiss = { showPrinters = false },
            onSelect = { selectedAddress ->
                showPrinters = false
                val currentSale = pendingPrint
                scope.launch {
                    if (currentSale == null) {
                        runCatching {
                            settingsDao.save(settings.copy(printerAddress = selectedAddress))
                        }.onSuccess {
                            report("Printer Bluetooth berhasil disimpan.")
                        }.onFailure {
                            report("Pilihan printer gagal disimpan.", true)
                        }
                    } else {
                        runCatching {
                            ThermalPrinter.print(context, selectedAddress, currentSale, settings)
                            settingsDao.save(settings.copy(printerAddress = selectedAddress))
                        }.onSuccess {
                            report("Nota berhasil dicetak dan printer disimpan.")
                        }.onFailure {
                            report("Nota gagal dicetak. Pastikan printer menyala dan kertas tersedia.", true)
                        }
                    }
                }
            }
        )
    }
}
