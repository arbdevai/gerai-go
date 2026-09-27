package id.geraigo.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.withTransaction
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.ClipData
import android.graphics.BitmapFactory
import coil.compose.AsyncImage
import id.geraigo.app.data.*
import id.geraigo.app.printer.ThermalPrinter
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID
import kotlin.math.roundToLong

private val Purple = Color(0xFF6941C6)
private val Ink = Color(0xFF201A2B)
private val Muted = Color(0xFF888393)
private val AppBackground = Color(0xFFF7F6FA)
private val ChartColors = listOf(Purple, Color(0xFF8F72D7), Color(0xFFB9A6E8), Color(0xFF4E9C8D), Color(0xFFEC9B58), Color(0xFF4B86C6))
private fun rupiah(value: Long) = "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = GeraiDatabase.get(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Purple, background = AppBackground, surface = Color.White)) { GeraiApp(this, db) } }
    }
}

@Composable private fun GeraiApp(activity: ComponentActivity, db: GeraiDatabase) {
    val dao = db.sales(); val settingsDao = db.settings(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val sales by dao.observeAll().collectAsState(initial = emptyList())
    val settings by settingsDao.observe().collectAsState(initial = null)
    val favorites by dao.observeFavorites().collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }; var selectedDate by remember { mutableStateOf<Long?>(null) }
    var editingOrder by remember { mutableStateOf<OrderReceipt?>(null) }; var quickItem by remember { mutableStateOf<Pair<String, Long>?>(null) }; var formOpen by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<OrderReceipt?>(null) }; var deleteTarget by remember { mutableStateOf<OrderReceipt?>(null) }
    var pendingPrint by remember { mutableStateOf<OrderReceipt?>(null) }; var showPrinters by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var statusError by remember { mutableStateOf(false) }
    fun report(message: String, error: Boolean = false) { statusMessage = message; statusError = error }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) showPrinters = true else report("Izin Bluetooth ditolak. Aktifkan izin ini di Pengaturan Android untuk mencetak.", true) }
    val saveNoteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val sale = receipt
        if (uri != null && sale != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(receiptText(sale, settings ?: StoreSettings()).toByteArray()) } ?: error("Lokasi penyimpanan tidak dapat dibuka") }.onSuccess { report("Nota berhasil disimpan.") }.onFailure { report("Nota gagal disimpan. Periksa izin dan ruang penyimpanan, lalu coba lagi.", true) }
    }
    val orders = remember(sales) { sales.groupBy { it.receiptKey.ifBlank { it.id.toString() } }.map { (key, items) -> OrderReceipt(key, items) }.sortedByDescending { it.createdAt } }
    val filtered = remember(orders, query, selectedDate) { orders.filter { order ->
        (order.items.any { it.name.contains(query, true) || it.note.contains(query, true) } || order.key.contains(query, true)) && (selectedDate == null || sameDay(order.createdAt, selectedDate!!))
    } }
    fun openPrinter(sale: OrderReceipt?) {
        pendingPrint = sale
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else if (sale != null && !(settings ?: StoreSettings()).printerAddress.isNullOrBlank()) {
            scope.launch { runCatching { ThermalPrinter.print(context, (settings ?: StoreSettings()).printerAddress, sale, settings ?: StoreSettings()) }.onSuccess { report("Nota berhasil dicetak.") }.onFailure { report("Cetak gagal. Periksa koneksi printer atau pilih printer lain di Pengaturan.", true) } }
        } else showPrinters = true
    }
    Scaffold(containerColor = AppBackground, bottomBar = { if (receipt == null && !formOpen) FloatingNavDock(selected = tab, onSelected = { tab = it }, onAdd = { editingOrder = null; quickItem = null; formOpen = true }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
          if (statusMessage.isNotBlank()) StatusBanner(statusMessage, statusError) { statusMessage = "" }
          Box(Modifier.weight(1f).fillMaxWidth()) {
          when {
            receipt != null -> {
                val sale = receipt!!
                ReceiptDialog(sale, settings ?: StoreSettings(), onClose = { receipt = null }, onPrint = { openPrinter(sale) }, onShareText = { shareReceiptText(context, sale, settings ?: StoreSettings()) }, onShareImage = { shareReceiptImage(context, sale, settings ?: StoreSettings()) }, onSave = { saveNoteLauncher.launch("nota-${sale.key}.txt") })
            }
            formOpen -> SaleForm(dao, editingOrder, quickItem, settings ?: StoreSettings(), onClose = { formOpen = false }, onSaved = { receipt = it; formOpen = false; quickItem = null })
            else -> when (tab) {
            0 -> CashPage(orders, favorites, onFavorite = { name -> scope.launch { quickItem = name to (dao.latestPrice(name) ?: 0); editingOrder = null; formOpen = true } }, onReceipt = { receipt = it })
            1 -> HistoryPage(filtered, query, selectedDate, { query = it }, { selectedDate = it }, { receipt = it }, { order -> editingOrder = order; quickItem = null; formOpen = true }, { deleteTarget = it }, { order -> scope.launch { val favorite = !order.items.all { it.favorite }; order.items.forEach { dao.update(it.copy(favorite = favorite)) } } })
            2 -> DashboardPage(orders)
            else -> SettingsPage(activity, orders, settings ?: StoreSettings(), onChoosePrinter = { openPrinter(null) }, onSave = { scope.launch { runCatching { settingsDao.save(it) }.onSuccess { report("Pengaturan berhasil disimpan.") }.onFailure { report("Pengaturan gagal disimpan. Coba lagi.", true) } } }, onBackup = { data, uri ->
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } ?: error("Lokasi penyimpanan tidak dapat dibuka") }.onSuccess { report("Backup berhasil disimpan.") }.onFailure { report("Backup gagal disimpan. Periksa ruang penyimpanan, lalu coba lagi.", true) }
            }, onRestore = { uri -> scope.launch {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("File tidak dapat dibaca")
                    val json = JSONObject(raw); val incoming = json.getJSONArray("transactions"); val restored = ArrayList<Sale>()
                    for (i in 0 until incoming.length()) { val x = incoming.getJSONObject(i); restored.add(Sale(id = 0, name = x.getString("name"), price = x.getLong("price"), quantity = x.getInt("quantity"), note = x.optString("note"), createdAt = x.getLong("createdAt"), favorite = x.optBoolean("favorite"), receiptKey = x.optString("receiptKey", UUID.randomUUID().toString()), taxAmount = x.optLong("taxAmount"), adminAmount = x.optLong("adminAmount"), taxPercent = x.optDouble("taxPercent"), adminPercent = x.optDouble("adminPercent"))) }
                    val pref = json.optJSONObject("settings")?.let { restoreImages(context, it) }
                    db.withTransaction { dao.clear(); dao.insertAll(restored); if (pref != null) settingsDao.save(pref) }
                }.onSuccess { report("Backup berhasil dipulihkan.") }.onFailure { report("Backup tidak dapat dipulihkan. Pastikan file Gerai Go tidak rusak dan coba lagi.", true) }
            } })
            }
          }
        }
    }
    }
    if (deleteTarget != null) AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Hapus transaksi?") }, text = { Text("${deleteTarget?.items?.size ?: 0} barang dalam nota ini akan dihapus permanen dari riwayat.") }, confirmButton = { TextButton(onClick = { val item = deleteTarget!!; scope.launch { dao.deleteReceipt(item.key) }; deleteTarget = null }) { Text("Hapus", color = Color(0xFFB42318)) } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } })
    if (showPrinters) PrinterDialog(activity, onDismiss = { showPrinters = false }, onSelect = { address ->
        showPrinters = false
        val sale = pendingPrint
        scope.launch {
            if (sale == null) runCatching { settingsDao.save((settings ?: StoreSettings()).copy(printerAddress = address)) }.onSuccess { report("Printer Bluetooth tersimpan.") }.onFailure { report("Pilihan printer gagal disimpan.", true) }
            else runCatching { ThermalPrinter.print(context, address, sale, settings ?: StoreSettings()); settingsDao.save((settings ?: StoreSettings()).copy(printerAddress = address)) }.onSuccess { report("Nota berhasil dicetak. Printer ini disimpan untuk transaksi berikutnya.") }.onFailure { report("Nota gagal dicetak. Pastikan printer menyala, sudah dipasangkan, dan kertas tersedia.", true) }
        }
    })
}

@Composable private fun StatusBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val foreground = if (isError) Color(0xFF9F2727) else Color(0xFF176B50)
    val background = if (isError) Color(0xFFFFF0EF) else Color(0xFFEAF7F1)
    Surface(color = background, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null, tint = foreground, modifier = Modifier.size(19.dp))
            Text(message, Modifier.weight(1f).padding(horizontal = 10.dp), color = foreground, fontSize = 13.sp)
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Tutup pesan", tint = foreground, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable private fun FloatingNavDock(selected: Int, onSelected: (Int) -> Unit, onAdd: () -> Unit) {
    val labels = listOf("Kas", "Riwayat", "Dashboard", "Pengaturan")
    val icons = listOf(Icons.Default.AddCard, Icons.Default.ReceiptLong, Icons.Default.Insights, Icons.Default.Storefront)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 9.dp).shadow(16.dp, RoundedCornerShape(30.dp)),
        color = Color.White,
        shape = RoundedCornerShape(30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDEAF3))
    ) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 5.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val slots = listOf(0, 1, -1, 2, 3)
            slots.forEach { index ->
                if (index == -1) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        FloatingActionButton(onClick = onAdd, modifier = Modifier.size(54.dp), shape = CircleShape, containerColor = Purple, contentColor = Color.White) { Icon(Icons.Default.Add, "Tambah transaksi") }
                    }
                    return@forEach
                }
                val label = labels[index]
                val active = selected == index
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = { onSelected(index) },
                        color = if (active) Color(0xFFF0EAFE) else Color.Transparent,
                        shape = RoundedCornerShape(19.dp),
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(.96f).animateContentSize(tween(180))
                    ) {
                        Column(Modifier.fillMaxSize().padding(vertical = 3.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icons[index], contentDescription = label, tint = if (active) Purple else Color(0xFF817D8A), modifier = Modifier.size(19.dp))
                            Spacer(Modifier.height(2.dp)); Text(label, color = if (active) Purple else Color(0xFF817D8A), fontSize = 9.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ScreenHeader(title: String, subtitle: String) {
    Spacer(Modifier.height(20.dp)); Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink); Spacer(Modifier.height(4.dp)); Text(subtitle, color = Muted, fontSize = 14.sp); Spacer(Modifier.height(18.dp))
}

@Composable private fun CashPage(sales: List<OrderReceipt>, favorites: List<String>, onFavorite: (String) -> Unit, onReceipt: (OrderReceipt) -> Unit) {
    val today = sales.filter { sameDay(it.createdAt, System.currentTimeMillis()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Kas hari ini", "Semua transaksi tersimpan di perangkat")
        Surface(shape = RoundedCornerShape(22.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF6941C6), Color(0xFF5635A5)))).padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("TOTAL PEMASUKAN", Modifier.weight(1f), color = Color.White.copy(alpha = .78f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp); Icon(Icons.Default.TrendingUp, null, tint = Color.White.copy(alpha = .85f)) }
                Spacer(Modifier.height(8.dp)); Text(rupiah(today.sumOf { it.total }), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
                Spacer(Modifier.height(15.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${today.size} transaksi", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(dateLabel(System.currentTimeMillis()), color = Color.White.copy(alpha = .72f), fontSize = 11.sp) }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, null, tint = Purple, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Favorit", color = Ink, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(8.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(favorites) { name -> SuggestionChip(onClick = { onFavorite(name) }, label = { Text(name) }, icon = { Icon(Icons.Default.Bolt, null, Modifier.size(16.dp)) }) } }
        }
        Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Terbaru", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink); Spacer(Modifier.weight(1f)); TextButton(onClick = { }) { Text("${sales.size} transaksi") } }
        if (sales.isEmpty()) EmptyState("Belum ada transaksi", "Tekan tombol + di tengah untuk mulai mencatat.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) { items(sales.take(12), key = { it.key }) { sale -> SaleRow(sale, { onReceipt(sale) }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HistoryPage(sales: List<OrderReceipt>, query: String, selectedDate: Long?, onQuery: (String) -> Unit, onDate: (Long?) -> Unit, onReceipt: (OrderReceipt) -> Unit, onEdit: (OrderReceipt) -> Unit, onDelete: (OrderReceipt) -> Unit, onToggleFavorite: (OrderReceipt) -> Unit) {
    var dateDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Riwayat", "Cari dan kelola transaksi")
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Cari nama atau catatan") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp), singleLine = true)
        Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selectedDate == null, { onDate(null) }, label = { Text("Semua tanggal") })
            Spacer(Modifier.width(7.dp)); FilterChip(selectedDate != null, { dateDialog = true }, label = { Text(selectedDate?.let { dateLabel(it) } ?: "Pilih tanggal") }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) })
            Spacer(Modifier.weight(1f)); Text("${sales.size}", color = Muted, fontSize = 12.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) { items(sales, key = { it.key }) { sale ->
            var menuOpen by remember(sale.key) { mutableStateOf(false) }
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SaleRow(sale, { onReceipt(sale) }, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Aksi transaksi", tint = Muted) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Lihat nota") }, leadingIcon = { Icon(Icons.Default.ReceiptLong, null) }, onClick = { menuOpen = false; onReceipt(sale) })
                            DropdownMenuItem(text = { Text("Edit transaksi") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; onEdit(sale) })
                            DropdownMenuItem(text = { Text(if (sale.items.all { it.favorite }) "Hapus dari favorit" else "Jadikan favorit") }, leadingIcon = { Icon(if (sale.items.all { it.favorite }) Icons.Default.Star else Icons.Default.StarBorder, null) }, onClick = { menuOpen = false; onToggleFavorite(sale) })
                            DropdownMenuItem(text = { Text("Hapus transaksi", color = Color(0xFFB42318)) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFB42318)) }, onClick = { menuOpen = false; onDelete(sale) })
                        }
                    }
                }
            }
        } }
    }
    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton(onClick = { onDate(picker.selectedDateMillis); dateDialog = false }) { Text("Pilih") } }, dismissButton = { TextButton(onClick = { onDate(null); dateDialog = false }) { Text("Hapus filter") } }) { DatePicker(state = picker) }
    }
}

@Composable private fun SaleRow(sale: OrderReceipt, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = modifier) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF1EDFC)), contentAlignment = Alignment.Center) { Icon(if (sale.items.any { it.favorite }) Icons.Default.Star else Icons.Default.Receipt, null, tint = Purple, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(sale.items.firstOrNull()?.name.orEmpty() + if (sale.items.size > 1) " +${sale.items.size - 1} barang" else "", color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1); Text("${sale.items.size} item · ${dateTimeLabel(sale.createdAt)}", color = Muted, fontSize = 11.sp, maxLines = 1) }
        Text(rupiah(sale.total), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    } }
}

@Composable private fun DashboardPage(sales: List<OrderReceipt>) {
    val now = System.currentTimeMillis(); val today = sales.filter { sameDay(it.createdAt, now) }; val week = sales.filter { it.createdAt >= now - 7L * 86400000 }; val month = sales.filter { monthKey(it.createdAt) == monthKey(now) }
    val daily = (6 downTo 0).map { delta -> val day = now - delta * 86400000L; sales.filter { sameDay(it.createdAt, day) }.sumOf { it.total } }
    val weeks = (3 downTo 0).map { offset -> val start = startOfDay(now) - (offset * 7L + 6L) * 86400000; sales.filter { it.createdAt >= start && it.createdAt < start + 7L * 86400000 }.sumOf { it.total } }
    val products = sales.flatMap { it.items }.groupBy { it.name }.mapValues { it.value.sumOf(Sale::total) }.entries.sortedByDescending { it.value }.take(6)
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
        ScreenHeader("Dashboard", "Ringkasan keuangan usaha")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { MetricCard("Pendapatan hari ini", rupiah(today.sumOf { it.total }), Modifier.weight(1f)); MetricCard("Transaksi hari ini", "${today.size}", Modifier.weight(1f)) }
        Spacer(Modifier.height(9.dp)); Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { MetricCard("Rata-rata transaksi", rupiah(if (today.isEmpty()) 0 else today.sumOf { it.total } / today.size), Modifier.weight(1f)); MetricCard("Minggu ini", rupiah(week.sumOf { it.total }), Modifier.weight(1f)) }
        Spacer(Modifier.height(9.dp)); MetricCard("Bulan ini", rupiah(month.sumOf { it.total }), Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp)); ChartSection("Pemasukan harian · 7 hari") { LineChart(daily) }
        Spacer(Modifier.height(14.dp)); ChartSection("Perbandingan pemasukan mingguan") { BarChart(weeks) }
        Spacer(Modifier.height(14.dp)); ChartSection("Persentase transaksi per item") {
            if (products.isEmpty()) Text("Belum ada data transaksi", color = Muted, modifier = Modifier.padding(8.dp)) else Row(verticalAlignment = Alignment.CenterVertically) {
                PieChart(products.map { it.value }, Modifier.size(138.dp)); Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { products.forEachIndexed { i, entry -> Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(ChartColors[i % ChartColors.size])); Spacer(Modifier.width(6.dp)); Text(entry.key, Modifier.weight(1f), fontSize = 11.sp, color = Ink, maxLines = 1); Text("${(entry.value * 100 / products.sumOf { it.value }).coerceAtLeast(1)}%", fontSize = 11.sp, color = Muted) } } }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier) { Surface(color = Color.White, shape = RoundedCornerShape(17.dp), modifier = modifier) { Column(Modifier.padding(13.dp)) { Text(label, color = Muted, fontSize = 11.sp, maxLines = 1); Spacer(Modifier.height(6.dp)); Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1) } } }
@Composable private fun ChartSection(title: String, content: @Composable ColumnScope.() -> Unit) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink); Spacer(Modifier.height(8.dp)); Surface(color = Color.White, shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp), content = content) } }

@Composable private fun LineChart(values: List<Long>) {
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(8.dp)) {
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val pts = values.mapIndexed { i, v -> androidx.compose.ui.geometry.Offset(i * step, size.height - v.toFloat() / max * size.height) }
        for (i in 0 until pts.lastIndex) drawLine(Purple, pts[i], pts[i + 1], 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        pts.forEach { drawCircle(Purple, 4.dp.toPx(), it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { (6 downTo 0).forEach { Text(if (it == 0) "Hari ini" else "-${it}h", color = Muted, fontSize = 10.sp) } }
}

@Composable private fun BarChart(values: List<Long>) {
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(130.dp).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, value -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
            Text(shortMoney(value), color = Muted, fontSize = 9.sp, maxLines = 1); Spacer(Modifier.height(5.dp)); Box(Modifier.width(34.dp).fillMaxHeight((value.toFloat() / max).coerceAtLeast(.025f)).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)).background(ChartColors[i % ChartColors.size])); Spacer(Modifier.height(5.dp)); Text("M-${3-i}", color = Muted, fontSize = 10.sp)
        } }
    }
}

@Composable private fun PieChart(values: List<Long>, modifier: Modifier = Modifier) {
    val sum = values.sum().coerceAtLeast(1)
    Canvas(modifier) { var start = -90f; values.forEachIndexed { i, value -> val sweep = value.toFloat() / sum * 360f; drawArc(ChartColors[i % ChartColors.size], start, sweep, true); start += sweep } }
}

private data class DraftItem(val key: String = UUID.randomUUID().toString(), val name: String = "", val price: String = "", val quantity: String = "1", val favorite: Boolean = false)

@Composable private fun SaleForm(dao: SaleDao, initial: OrderReceipt?, quickItem: Pair<String, Long>?, settings: StoreSettings, onClose: () -> Unit, onSaved: (OrderReceipt) -> Unit) {
    var items by remember(initial?.key) { mutableStateOf(initial?.items?.map { DraftItem(name = it.name, price = rupiah(it.price), quantity = it.quantity.toString(), favorite = it.favorite) } ?: listOf(DraftItem(name = quickItem?.first.orEmpty(), price = quickItem?.second?.takeIf { it > 0 }?.let(::rupiah).orEmpty()))) }
    var note by remember(initial?.key) { mutableStateOf(initial?.note.orEmpty()) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }; var focusedItem by remember { mutableIntStateOf(-1) }; var formError by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    fun changeItem(index: Int, value: DraftItem) { items = items.toMutableList().also { it[index] = value } }
    val subtotal = items.sumOf { (it.price.filter(Char::isDigit).toLongOrNull() ?: 0L) * (it.quantity.toIntOrNull() ?: 0) }
    val tax = if (settings.taxEnabled) (subtotal * settings.taxPercent / 100.0).roundToLong() else 0L
    val admin = if (settings.adminFeeEnabled) (subtotal * settings.adminFeePercent / 100.0).roundToLong() else 0L
    Column(Modifier.fillMaxSize().background(AppBackground).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Kembali", tint = Ink) }; Column { Text(if (initial == null) "Transaksi baru" else "Edit transaksi", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink); Text("Tambah beberapa barang dalam satu nota", color = Muted, fontSize = 12.sp) } }
        items.forEachIndexed { index, item ->
            Spacer(Modifier.height(10.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Barang ${index + 1}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink); IconButton(onClick = { changeItem(index, item.copy(favorite = !item.favorite)) }) { Icon(if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorit", tint = if (item.favorite) Color(0xFFE8A317) else Muted) }; if (items.size > 1) IconButton(onClick = { items = items.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.DeleteOutline, "Hapus barang", tint = Muted) } }
                    OutlinedTextField(item.name, { value -> changeItem(index, item.copy(name = value)); focusedItem = index; scope.launch { suggestions = if (value.length > 1) dao.suggestions(value) else emptyList() } }, modifier = Modifier.fillMaxWidth(), label = { Text("Nama barang") }, placeholder = { Text("Cari riwayat atau ketik nama") }, shape = RoundedCornerShape(14.dp), singleLine = true)
                    if (focusedItem == index) suggestions.forEach { name -> TextButton(onClick = { changeItem(index, item.copy(name = name)); focusedItem = -1; suggestions = emptyList(); scope.launch { dao.latestPrice(name)?.let { changeItem(index, items[index].copy(price = rupiah(it))) } } }) { Icon(Icons.Default.History, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(name) } }
                    Spacer(Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(item.price, { input -> changeItem(index, item.copy(price = input.filter(Char::isDigit).toLongOrNull()?.let(::rupiah).orEmpty())) }, modifier = Modifier.weight(1f), label = { Text("Harga satuan") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp), singleLine = true)
                        OutlinedTextField(item.quantity, { input -> changeItem(index, item.copy(quantity = input.filter(Char::isDigit).take(5))) }, modifier = Modifier.width(100.dp), label = { Text("Jumlah") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp), singleLine = true)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) { Text("Jumlah", color = Muted, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(rupiah((item.price.filter(Char::isDigit).toLongOrNull() ?: 0L) * (item.quantity.toIntOrNull() ?: 0)), color = Ink, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        TextButton(onClick = { items = items + DraftItem() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Tambah barang") }
        OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Catatan nota (opsional)") }, shape = RoundedCornerShape(14.dp), maxLines = 2)
        if (formError.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFB42318), modifier = Modifier.size(18.dp)); Text(formError, Modifier.padding(start = 8.dp), color = Color(0xFFB42318), fontSize = 12.sp) }
        Spacer(Modifier.height(14.dp)); Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) {
            ReceiptDataLine("Subtotal", rupiah(subtotal), size = 12.sp)
            if (settings.taxEnabled) ReceiptDataLine("Pajak ${settings.taxPercent}%", rupiah(tax), size = 12.sp)
            if (settings.adminFeeEnabled) ReceiptDataLine("Biaya admin ${settings.adminFeePercent}%", rupiah(admin), size = 12.sp)
            HorizontalDivider(Modifier.padding(vertical = 8.dp)); ReceiptDataLine("TOTAL", rupiah(subtotal + tax + admin), bold = true, size = 16.sp)
        } }
        Spacer(Modifier.height(12.dp)); Button(onClick = {
            formError = when {
                items.isEmpty() -> "Tambahkan minimal satu barang."
                items.any { it.name.isBlank() } -> "Nama setiap barang perlu diisi."
                items.any { (it.price.filter(Char::isDigit).toLongOrNull() ?: 0) <= 0 } -> "Harga setiap barang harus lebih dari Rp 0."
                items.any { (it.quantity.toIntOrNull() ?: 0) <= 0 } -> "Jumlah setiap barang harus minimal 1."
                else -> ""
            }
            if (formError.isBlank()) {
                val key = initial?.key ?: UUID.randomUUID().toString().replace("-", ""); val createdAt = initial?.createdAt ?: System.currentTimeMillis()
                val rows = items.mapIndexed { index, item -> Sale(name = item.name.trim(), price = item.price.filter(Char::isDigit).toLong(), quantity = item.quantity.toInt(), note = if (index == 0) note.trim() else "", createdAt = createdAt, favorite = item.favorite, receiptKey = key, taxAmount = if (index == 0) tax else 0, adminAmount = if (index == 0) admin else 0, taxPercent = if (index == 0 && settings.taxEnabled) settings.taxPercent else 0.0, adminPercent = if (index == 0 && settings.adminFeeEnabled) settings.adminFeePercent else 0.0) }
                scope.launch { runCatching { if (initial == null) dao.insertAll(rows) else dao.replaceReceipt(key, rows) }.onSuccess { onSaved(OrderReceipt(key, rows)) }.onFailure { formError = "Transaksi gagal disimpan. Data sebelumnya tetap aman; coba lagi." } }
            }
        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) { Text(if (initial == null) "Simpan dan lihat nota" else "Simpan perubahan") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable private fun ReceiptDialog(sale: OrderReceipt, settings: StoreSettings, onClose: () -> Unit, onPrint: () -> Unit, onShareText: () -> Unit, onShareImage: () -> Unit, onSave: () -> Unit) {
        Column(Modifier.fillMaxSize().background(AppBackground).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Nota tersimpan", color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp); Text("Pratinjau sesuai lebar kertas ${settings.paperWidth} mm", color = Muted, fontSize = 12.sp) }
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Kembali", tint = Ink) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                val paperShape = remember { ReceiptPaperShape() }
                val desiredWidth = if (settings.paperWidth == 80) 340.dp else 246.dp
                Column(
                    Modifier.width(minOf(maxWidth, desiredWidth)).shadow(12.dp, paperShape).clip(paperShape).background(Color.White).padding(horizontal = 20.dp, vertical = 19.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (settings.showLogo && settings.logoUri.isNotBlank()) {
                        AsyncImage(settings.logoUri, contentDescription = "Logo ${settings.name}", modifier = Modifier.size((settings.imageWidth / 3).coerceIn(42, 90).dp).clip(RoundedCornerShape(5.dp)))
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFF0EAFE)), contentAlignment = Alignment.Center) { Text((settings.name.ifBlank { "G" }).take(1).uppercase(), color = Purple, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(7.dp))
                    }
                    Text(settings.name.ifBlank { "Gerai Go" }.uppercase(), color = Color(0xFF17151B), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                    if (settings.address.isNotBlank()) Text(settings.address, Modifier.padding(top = 4.dp), color = Color(0xFF39363D), fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                    if (settings.contact.isNotBlank()) Text(settings.contact, Modifier.padding(top = 2.dp), color = Color(0xFF39363D), fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                    if (settings.header.isNotBlank()) Text(settings.header, Modifier.padding(top = 7.dp), color = Color(0xFF39363D), fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(12.dp)); ReceiptDottedRule(); Spacer(Modifier.height(9.dp))
                    Text("NOTA PENJUALAN", color = Color(0xFF252229), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(6.dp)); ReceiptDataLine("No. nota", "GG-${sale.key.takeLast(6).uppercase()}")
                    ReceiptDataLine("Waktu", SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale("id", "ID")).format(Date(sale.createdAt)))
                    Spacer(Modifier.height(8.dp)); ReceiptDottedRule(); Spacer(Modifier.height(9.dp))
                    sale.items.forEach { item ->
                        Text(item.name, Modifier.fillMaxWidth(), color = Color(0xFF17151B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(3.dp)); ReceiptDataLine("${item.quantity} x ${rupiah(item.price)}", rupiah(item.total))
                    }
                    if (sale.note.isNotBlank()) Text("Catatan: ${sale.note}", Modifier.fillMaxWidth().padding(top = 7.dp), color = Color(0xFF4A464E), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(10.dp)); ReceiptDottedRule(); Spacer(Modifier.height(8.dp))
                    ReceiptDataLine("Subtotal", rupiah(sale.subtotal))
                    if (sale.taxAmount > 0) ReceiptDataLine("Pajak ${sale.taxPercent}%", rupiah(sale.taxAmount))
                    if (sale.adminAmount > 0) ReceiptDataLine("Biaya admin ${sale.adminPercent}%", rupiah(sale.adminAmount))
                    Spacer(Modifier.height(6.dp)); ReceiptDataLine("TOTAL", rupiah(sale.total), bold = true, size = 14.sp)
                    Spacer(Modifier.height(9.dp)); ReceiptDottedRule()
                    if (settings.showExtraImage && settings.extraImageUri.isNotBlank()) {
                        Spacer(Modifier.height(11.dp)); AsyncImage(settings.extraImageUri, contentDescription = "Gambar tambahan nota", modifier = Modifier.width((settings.imageWidth / 2).coerceIn(90, 240).dp).heightIn(max = 120.dp))
                    }
                    if (settings.footer.isNotBlank()) Text(settings.footer, Modifier.fillMaxWidth().padding(top = 12.dp), color = Color(0xFF39363D), fontSize = 10.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                    Text("Simpan nota ini sebagai bukti transaksi", Modifier.padding(top = 5.dp), color = Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = onShareText, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 5.dp)) { Icon(Icons.Default.TextSnippet, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Teks", fontSize = 11.sp, maxLines = 1) }
                OutlinedButton(onClick = onShareImage, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 5.dp)) { Icon(Icons.Default.Image, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Gambar", fontSize = 11.sp, maxLines = 1) }
                OutlinedButton(onClick = onSave, modifier = Modifier.width(86.dp).height(46.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 5.dp)) { Icon(Icons.Default.SaveAlt, null, Modifier.size(17.dp)); Spacer(Modifier.width(3.dp)); Text("Simpan", fontSize = 11.sp, maxLines = 1) }
            }
            Button(onClick = onPrint, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp), shape = RoundedCornerShape(17.dp)) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("Cetak nota") }
        }
}

@Composable private fun ReceiptDataLine(left: String, right: String, bold: Boolean = false, size: TextUnit = 10.sp) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(left, Modifier.weight(1f), color = Color(0xFF343139), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace, maxLines = 1)
        Text(right, color = Color(0xFF17151B), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable private fun ReceiptDottedRule() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawLine(Color(0xFF89858D), androidx.compose.ui.geometry.Offset.Zero.copy(y = size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))) }
}

private class ReceiptPaperShape : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val tooth = with(density) { 7.dp.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - tooth)
            var x = size.width; var step = 0
            while (x > 0f) { x = (x - tooth).coerceAtLeast(0f); lineTo(x, if (step++ % 2 == 0) size.height else size.height - tooth) }
            lineTo(0f, size.height - tooth); close()
        }
        return Outline.Generic(path)
    }
}

@Composable private fun PrinterDialog(activity: ComponentActivity, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val devices = remember { runCatching { ThermalPrinter.pairedDevices(context) }.getOrDefault(emptyList()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pilih printer Bluetooth") }, text = { Column {
        Text("Pasangkan printer di pengaturan Bluetooth Android terlebih dahulu.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) Text("Belum ada perangkat yang dipasangkan.", color = Muted)
        devices.forEach { device -> TextButton(onClick = { onSelect(device.address) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text(device.name ?: "Printer Bluetooth") } }
        Text("Gunakan printer ESC/POS thermal 58 mm atau 80 mm.", color = Muted, fontSize = 11.sp)
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } })
}

@Composable private fun SettingsPage(activity: ComponentActivity, sales: List<OrderReceipt>, settings: StoreSettings, onChoosePrinter: () -> Unit, onSave: (StoreSettings) -> Unit, onBackup: (String, Uri) -> Unit, onRestore: (Uri) -> Unit) {
    val context = LocalContext.current
    var model by remember(settings) { mutableStateOf(settings) }
    val chooseLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; model = model.copy(logoUri = it.toString(), showLogo = true) } }
    val chooseExtra = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; model = model.copy(extraImageUri = it.toString(), showExtraImage = true) } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) onBackup(backupJson(context, sales.flatMap { it.items }, model), uri) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onRestore(uri) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
        ScreenHeader("Pengaturan", "Identitas toko, nota, dan data")
        Text("Informasi toko", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp)
        SettingInput("Nama toko", model.name) { model = model.copy(name = it) }
        SettingInput("Alamat", model.address, singleLine = false) { model = model.copy(address = it) }
        SettingInput("Nomor kontak", model.contact, keyboard = KeyboardType.Phone) { model = model.copy(contact = it) }
        Spacer(Modifier.height(12.dp)); Text("Tampilan nota", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp)
        SettingInput("Teks header", model.header, singleLine = false) { model = model.copy(header = it) }
        SettingInput("Footer nota", model.footer, singleLine = false) { model = model.copy(footer = it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Logo toko", Modifier.weight(1f), color = Ink); if (model.logoUri.isNotBlank()) AsyncImage(model.logoUri, "Logo toko", Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))); TextButton(onClick = { chooseLogo.launch(arrayOf("image/*")) }) { Text("Pilih gambar") } }
        SettingSwitch("Tampilkan logo", model.showLogo) { model = model.copy(showLogo = it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Gambar tambahan nota", Modifier.weight(1f), color = Ink); TextButton(onClick = { chooseExtra.launch(arrayOf("image/*")) }) { Text("Pilih gambar") } }
        SettingSwitch("Tampilkan gambar tambahan", model.showExtraImage) { model = model.copy(showExtraImage = it) }
        Text("Ukuran gambar cetak", color = Muted, fontSize = 12.sp); Slider(value = model.imageWidth.toFloat(), onValueChange = { model = model.copy(imageWidth = it.toInt()) }, valueRange = 96f..384f, steps = 5)
        Text("Lebar kertas printer", color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { FilterChip(model.paperWidth == 58, { model = model.copy(paperWidth = 58) }, label = { Text("58 mm") }); FilterChip(model.paperWidth == 80, { model = model.copy(paperWidth = 80) }, label = { Text("80 mm") }) }
        Spacer(Modifier.height(14.dp)); Text("Pajak dan biaya", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp)
        SettingSwitch("Aktifkan pajak", model.taxEnabled) { model = model.copy(taxEnabled = it) }
        if (model.taxEnabled) SettingPercent("Persentase pajak", model.taxPercent) { model = model.copy(taxPercent = it) }
        SettingSwitch("Aktifkan biaya admin", model.adminFeeEnabled) { model = model.copy(adminFeeEnabled = it) }
        if (model.adminFeeEnabled) SettingPercent("Persentase biaya admin", model.adminFeePercent) { model = model.copy(adminFeePercent = it) }
        Spacer(Modifier.height(14.dp)); Text("Printer Bluetooth", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp)
        val pairedName = remember(model.printerAddress) { runCatching { ThermalPrinter.pairedDevices(context).firstOrNull { it.address == model.printerAddress }?.name }.getOrNull() }
        Text(if (model.printerAddress.isBlank()) "Belum ada printer dipilih" else pairedName ?: model.printerAddress, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        OutlinedButton(onClick = onChoosePrinter, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Icon(Icons.Default.BluetoothSearching, null); Spacer(Modifier.width(7.dp)); Text("Pilih printer tersanding") }
        Button(onClick = { onSave(model) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Simpan pengaturan nota") }
        Spacer(Modifier.height(20.dp)); Text("Backup lokal", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp); Text("${sales.size} transaksi tersimpan di perangkat", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { export.launch("gerai-go-backup.json") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(4.dp)); Text("Export") }; OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(4.dp)); Text("Import") } }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable private fun SettingPercent(label: String, value: Double, onValue: (Double) -> Unit) {
    var raw by remember(label) { mutableStateOf(value.toString()) }
    OutlinedTextField(value = raw, onValueChange = { input ->
        raw = input.replace(',', '.').filter { it.isDigit() || it == '.' }
        val parsed = raw.toDoubleOrNull()
        if (raw.isBlank()) onValue(0.0) else if (parsed != null) onValue(parsed.coerceIn(0.0, 100.0))
    }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), label = { Text(label) }, suffix = { Text("%") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(13.dp), singleLine = true)
}

@Composable private fun SettingInput(label: String, value: String, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(label) }, shape = RoundedCornerShape(13.dp), singleLine = singleLine, maxLines = if (singleLine) 1 else 3, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = Ink, fontSize = 13.sp); Switch(checked, onChange) } }

@Composable private fun EmptyState(title: String, body: String) { Column(Modifier.fillMaxWidth().padding(top = 45.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFF0EAFE)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ReceiptLong, null, tint = Purple) }; Spacer(Modifier.height(14.dp)); Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(body, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center) } }

private fun shareReceiptText(context: Context, receipt: OrderReceipt, settings: StoreSettings) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Nota ${settings.name}"); putExtra(Intent.EXTRA_TEXT, receiptText(receipt, settings)) }
    context.startActivity(Intent.createChooser(intent, "Bagikan nota sebagai teks"))
}

private fun shareReceiptImage(context: Context, receipt: OrderReceipt, settings: StoreSettings) {
    val file = renderReceiptImage(context, receipt, settings)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_SUBJECT, "Nota ${settings.name}"); putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Nota Gerai Go", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan nota sebagai gambar"))
}

private fun receiptText(receipt: OrderReceipt, settings: StoreSettings) = buildString {
    appendLine(settings.name.ifBlank { "Gerai Go" }); if (settings.address.isNotBlank()) appendLine(settings.address); if (settings.contact.isNotBlank()) appendLine(settings.contact)
    if (settings.header.isNotBlank()) appendLine(settings.header); appendLine("No. nota: GG-${receipt.key.takeLast(6).uppercase()}"); appendLine(dateTimeLabel(receipt.createdAt)); appendLine("------------------------------")
    receipt.items.forEach { appendLine(it.name); appendLine("${it.quantity} x ${rupiah(it.price)} = ${rupiah(it.total)}") }
    if (receipt.note.isNotBlank()) appendLine("Catatan: ${receipt.note}")
    appendLine("------------------------------"); appendLine("Subtotal: ${rupiah(receipt.subtotal)}")
    if (receipt.taxAmount > 0) appendLine("Pajak ${receipt.taxPercent}%: ${rupiah(receipt.taxAmount)}")
    if (receipt.adminAmount > 0) appendLine("Biaya admin ${receipt.adminPercent}%: ${rupiah(receipt.adminAmount)}")
    appendLine("TOTAL: ${rupiah(receipt.total)}"); appendLine(settings.footer)
}

private fun renderReceiptImage(context: Context, receipt: OrderReceipt, settings: StoreSettings): java.io.File {
    val width = if (settings.paperWidth == 80) 576 else 384
    val textSize = 20f
    val lineHeight = 31f
    val headerRows = 14 + (if (settings.showLogo && settings.logoUri.isNotBlank()) 5 else 0) + (if (settings.showExtraImage && settings.extraImageUri.isNotBlank()) 7 else 0)
    val height = ((headerRows + receipt.items.size * 2 + 9) * lineHeight).toInt().coerceAtLeast(400)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; this.textSize = textSize; typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL) }
    val bold = android.graphics.Paint(paint).apply { typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD) }
    var y = 30f
    fun centered(text: String, typeface: android.graphics.Paint = paint) { canvas.drawText(text.take(45), (width - typeface.measureText(text.take(45))) / 2f, y, typeface); y += lineHeight }
    fun rule() { canvas.drawLine(20f, y - 8f, width - 20f, y - 8f, paint); y += 8f }
    if (settings.showLogo && settings.logoUri.isNotBlank()) runCatching {
        val logo = context.contentResolver.openInputStream(Uri.parse(settings.logoUri))?.use(BitmapFactory::decodeStream)
        if (logo != null) { val w = minOf(logo.width, width / 2); val h = (logo.height * w.toFloat() / logo.width).toInt(); val scaled = android.graphics.Bitmap.createScaledBitmap(logo, w, h, true); canvas.drawBitmap(scaled, (width - w) / 2f, y, paint); y += h + 10; scaled.recycle(); logo.recycle() }
    }
    centered(settings.name.ifBlank { "Gerai Go" }.uppercase(), bold)
    if (settings.address.isNotBlank()) centered(settings.address)
    if (settings.contact.isNotBlank()) centered(settings.contact)
    if (settings.header.isNotBlank()) centered(settings.header)
    centered("GG-${receipt.key.takeLast(6).uppercase()}")
    centered(dateTimeLabel(receipt.createdAt)); rule()
    receipt.items.forEach { item ->
        canvas.drawText(item.name.take(42), 20f, y, bold); y += lineHeight
        val left = "${item.quantity} x ${rupiah(item.price)}"; canvas.drawText(left.take(24), 20f, y, paint); canvas.drawText(rupiah(item.total), width - 20f - paint.measureText(rupiah(item.total)), y, paint); y += lineHeight
    }
    if (receipt.note.isNotBlank()) { canvas.drawText("Catatan: ${receipt.note}".take(44), 20f, y, paint); y += lineHeight }
    rule()
    fun totals(label: String, amount: Long, isBold: Boolean = false) { val p = if (isBold) bold else paint; canvas.drawText(label, 20f, y, p); val amountText = rupiah(amount); canvas.drawText(amountText, width - 20f - p.measureText(amountText), y, p); y += lineHeight }
    totals("Subtotal", receipt.subtotal)
    if (receipt.taxAmount > 0) totals("Pajak ${receipt.taxPercent}%", receipt.taxAmount)
    if (receipt.adminAmount > 0) totals("Biaya admin ${receipt.adminPercent}%", receipt.adminAmount)
    totals("TOTAL", receipt.total, true)
    if (settings.showExtraImage && settings.extraImageUri.isNotBlank()) runCatching {
        val extra = context.contentResolver.openInputStream(Uri.parse(settings.extraImageUri))?.use(BitmapFactory::decodeStream)
        if (extra != null) { val w = minOf(extra.width, width - 40); val h = (extra.height * w.toFloat() / extra.width).toInt().coerceAtMost(180); val scaled = android.graphics.Bitmap.createScaledBitmap(extra, w, h, true); canvas.drawBitmap(scaled, (width - w) / 2f, y, paint); y += h + 8; scaled.recycle(); extra.recycle() }
    }
    if (settings.footer.isNotBlank()) centered(settings.footer)
    val directory = java.io.File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = java.io.File(directory, "nota-${receipt.key}.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}

private fun backupJson(context: Context, sales: List<Sale>, settings: StoreSettings): String {
    val list = JSONArray(); sales.forEach { list.put(JSONObject().put("name", it.name).put("price", it.price).put("quantity", it.quantity).put("note", it.note).put("createdAt", it.createdAt).put("favorite", it.favorite).put("receiptKey", it.receiptKey).put("taxAmount", it.taxAmount).put("adminAmount", it.adminAmount).put("taxPercent", it.taxPercent).put("adminPercent", it.adminPercent)) }
    fun image(uri: String): String = if (uri.isBlank()) "" else runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP) } }.getOrNull().orEmpty()
    return JSONObject().put("format", "gerai-go-backup").put("version", 2).put("transactions", list).put("settings", JSONObject().put("name", settings.name).put("address", settings.address).put("contact", settings.contact).put("header", settings.header).put("footer", settings.footer).put("logoUri", settings.logoUri).put("extraImageUri", settings.extraImageUri).put("logoImage", image(settings.logoUri)).put("extraImage", image(settings.extraImageUri)).put("showLogo", settings.showLogo).put("showExtraImage", settings.showExtraImage).put("imageWidth", settings.imageWidth).put("paperWidth", settings.paperWidth).put("taxEnabled", settings.taxEnabled).put("taxPercent", settings.taxPercent).put("adminFeeEnabled", settings.adminFeeEnabled).put("adminFeePercent", settings.adminFeePercent).put("printerAddress", settings.printerAddress)).toString(2)
}
private fun restoreImages(context: Context, j: JSONObject): StoreSettings {
    fun restore(key: String, filename: String, legacyUri: String): String {
        val encoded = j.optString(key)
        if (encoded.isBlank()) return legacyUri
        val file = java.io.File(context.filesDir, filename)
        file.writeBytes(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
        return Uri.fromFile(file).toString()
    }
    return StoreSettings(name = j.optString("name", "Gerai Go"), address = j.optString("address"), contact = j.optString("contact"), header = j.optString("header"), footer = j.optString("footer", "Terima kasih telah berbelanja"), logoUri = restore("logoImage", "backup-logo", j.optString("logoUri")), extraImageUri = restore("extraImage", "backup-extra-image", j.optString("extraImageUri")), showLogo = j.optBoolean("showLogo", true), showExtraImage = j.optBoolean("showExtraImage"), imageWidth = j.optInt("imageWidth", 160), paperWidth = j.optInt("paperWidth", 58), taxEnabled = j.optBoolean("taxEnabled"), taxPercent = j.optDouble("taxPercent"), adminFeeEnabled = j.optBoolean("adminFeeEnabled"), adminFeePercent = j.optDouble("adminFeePercent"), printerAddress = j.optString("printerAddress"))
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply { timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun sameDay(a: Long, b: Long) = startOfDay(a) == startOfDay(b)
private fun monthKey(time: Long) = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(time))
private fun dateLabel(time: Long) = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(time))
private fun dateTimeLabel(time: Long) = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("id", "ID")).format(Date(time))
private fun shortMoney(value: Long) = when { value >= 1_000_000 -> "%.1f jt".format(Locale.US, value / 1_000_000.0); value >= 1000 -> "%.0f rb".format(Locale.US, value / 1000.0); else -> value.toString() }
