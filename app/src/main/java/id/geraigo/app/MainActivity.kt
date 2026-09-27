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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.room.withTransaction
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import id.geraigo.app.data.*
import id.geraigo.app.printer.ThermalPrinter
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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
    var editing by remember { mutableStateOf<Sale?>(null) }; var formOpen by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<Sale?>(null) }; var deleteTarget by remember { mutableStateOf<Sale?>(null) }
    var pendingPrint by remember { mutableStateOf<Sale?>(null) }; var showPrinters by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) showPrinters = true else toast = "Izin Bluetooth diperlukan untuk mencetak" }
    val saveNoteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val sale = receipt
        if (uri != null && sale != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(receiptText(sale, settings ?: StoreSettings()).toByteArray()) } }.onSuccess { toast = "Nota disimpan" }.onFailure { toast = "Gagal menyimpan nota" }
    }
    val filtered = remember(sales, query, selectedDate) { sales.filter { sale ->
        (sale.name.contains(query, true) || sale.note.contains(query, true)) && (selectedDate == null || sameDay(sale.createdAt, selectedDate!!))
    } }
    fun openPrinter(sale: Sale) {
        pendingPrint = sale
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else showPrinters = true
    }
    Scaffold(containerColor = AppBackground, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            listOf("Kas", "Riwayat", "Dashboard", "Pengaturan").forEachIndexed { i, label ->
                val icons = listOf(Icons.Default.AddCard, Icons.Default.ReceiptLong, Icons.Default.BarChart, Icons.Default.Settings)
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null) }, label = { Text(label, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Purple, selectedTextColor = Purple, indicatorColor = Color(0xFFF0EAFE)))
            }
        }
    }, floatingActionButton = { if (tab == 0) FloatingActionButton(onClick = { editing = null; formOpen = true }, containerColor = Purple, contentColor = Color.White) { Icon(Icons.Default.Add, "Transaksi baru") } }) { padding ->
        when (tab) {
            0 -> CashPage(sales, favorites, onAdd = { editing = null; formOpen = true }, onFavorite = { name -> scope.launch { val prev = dao.latest(name); editing = Sale(name = name, price = dao.latestPrice(name) ?: 0, quantity = 1, favorite = true); formOpen = true } }, onReceipt = { receipt = it })
            1 -> HistoryPage(filtered, query, selectedDate, { query = it }, { selectedDate = it }, { receipt = it }, { sale -> editing = sale; formOpen = true }, { deleteTarget = it })
            2 -> DashboardPage(sales)
            else -> SettingsPage(activity, sales, settings ?: StoreSettings(), onSave = { scope.launch { settingsDao.save(it); toast = "Pengaturan disimpan" } }, onBackup = { data, uri ->
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } }.onSuccess { toast = "Backup berhasil disimpan" }.onFailure { toast = "Gagal menyimpan backup" }
            }, onRestore = { uri -> scope.launch {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("File tidak dapat dibaca")
                    val json = JSONObject(raw); val incoming = json.getJSONArray("transactions"); val restored = ArrayList<Sale>()
                    for (i in 0 until incoming.length()) { val x = incoming.getJSONObject(i); restored.add(Sale(id = 0, name = x.getString("name"), price = x.getLong("price"), quantity = x.getInt("quantity"), note = x.optString("note"), createdAt = x.getLong("createdAt"), favorite = x.optBoolean("favorite"))) }
                    val pref = json.optJSONObject("settings")?.let { restoreImages(context, it) }
                    db.withTransaction { dao.clear(); dao.insertAll(restored); if (pref != null) settingsDao.save(pref) }
                }.onSuccess { toast = "Backup berhasil dipulihkan" }.onFailure { toast = "File backup tidak valid: ${it.message ?: "gagal dibaca"}" }
            } })
        }
    }
    if (formOpen) SaleForm(dao, editing, onClose = { formOpen = false }, onSaved = { receipt = it; formOpen = false })
    receipt?.let { sale -> ReceiptDialog(sale, settings ?: StoreSettings(), onClose = { receipt = null }, onPrint = { openPrinter(sale) }, onShare = { shareReceipt(context, sale, settings ?: StoreSettings()) }, onSave = { saveNoteLauncher.launch("nota-${sale.id}.txt") }) }
    if (deleteTarget != null) AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Hapus transaksi?") }, text = { Text("${deleteTarget?.name} akan dihapus permanen dari riwayat.") }, confirmButton = { TextButton(onClick = { val item = deleteTarget!!; scope.launch { dao.delete(item.id) }; deleteTarget = null }) { Text("Hapus", color = Color(0xFFB42318)) } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } })
    if (showPrinters) PrinterDialog(activity, onDismiss = { showPrinters = false }, onSelect = { address ->
        showPrinters = false; val sale = pendingPrint ?: return@PrinterDialog
        scope.launch { runCatching { ThermalPrinter.print(context, address, sale, settings ?: StoreSettings()) }.onSuccess { toast = "Nota terkirim ke printer" }.onFailure { toast = "Cetak gagal: ${it.message ?: "periksa koneksi"}" } }
    })
    if (toast.isNotBlank()) LaunchedEffect(toast) { kotlinx.coroutines.delay(2600); toast = "" }
    if (toast.isNotBlank()) SnackbarHost(hostState = remember { SnackbarHostState() }, modifier = Modifier.padding(bottom = 78.dp)) { Snackbar { Text(toast) } }
}

@Composable private fun ScreenHeader(title: String, subtitle: String) {
    Spacer(Modifier.height(20.dp)); Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink); Spacer(Modifier.height(4.dp)); Text(subtitle, color = Muted, fontSize = 14.sp); Spacer(Modifier.height(18.dp))
}

@Composable private fun CashPage(sales: List<Sale>, favorites: List<String>, onAdd: () -> Unit, onFavorite: (String) -> Unit, onReceipt: (Sale) -> Unit) {
    val today = sales.filter { sameDay(it.createdAt, System.currentTimeMillis()) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Catat transaksi", "Catat penjualan dengan cepat")
        Surface(shape = RoundedCornerShape(24.dp), color = Purple, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
            Text("Pemasukan hari ini", color = Color.White.copy(alpha = .78f), fontSize = 13.sp); Text(rupiah(today.sumOf { it.total }), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp)); Text("${today.size} transaksi  ·  Hari ini", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
        } }
        if (favorites.isNotEmpty()) {
            Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, null, tint = Purple, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Favorit", color = Ink, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(8.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(favorites) { name -> SuggestionChip(onClick = { onFavorite(name) }, label = { Text(name) }, icon = { Icon(Icons.Default.Bolt, null, Modifier.size(16.dp)) }) } }
        }
        Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Terbaru", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink); Spacer(Modifier.weight(1f)); TextButton(onClick = { }) { Text("${sales.size} transaksi") } }
        if (sales.isEmpty()) EmptyState("Belum ada transaksi", "Mulai dengan mencatat penjualan pertama.", onAdd)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) { items(sales.take(12), key = { it.id }) { sale -> SaleRow(sale, { onReceipt(sale) }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HistoryPage(sales: List<Sale>, query: String, selectedDate: Long?, onQuery: (String) -> Unit, onDate: (Long?) -> Unit, onReceipt: (Sale) -> Unit, onEdit: (Sale) -> Unit, onDelete: (Sale) -> Unit) {
    var dateDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Riwayat", "Cari dan kelola transaksi")
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Cari nama atau catatan") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp), singleLine = true)
        Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selectedDate == null, { onDate(null) }, label = { Text("Semua tanggal") })
            Spacer(Modifier.width(7.dp)); FilterChip(selectedDate != null, { dateDialog = true }, label = { Text(selectedDate?.let { dateLabel(it) } ?: "Pilih tanggal") }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) })
            Spacer(Modifier.weight(1f)); Text("${sales.size}", color = Muted, fontSize = 12.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) { items(sales, key = { it.id }) { sale ->
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                SaleRow(sale, { onReceipt(sale) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onEdit(sale) }) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit") }
                    TextButton(onClick = { onDelete(sale) }) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp), tint = Color(0xFFB42318)); Spacer(Modifier.width(4.dp)); Text("Hapus", color = Color(0xFFB42318)) }
                }
            } }
        } }
    }
    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton(onClick = { onDate(picker.selectedDateMillis); dateDialog = false }) { Text("Pilih") } }, dismissButton = { TextButton(onClick = { onDate(null); dateDialog = false }) { Text("Hapus filter") } }) { DatePicker(state = picker) }
    }
}

@Composable private fun SaleRow(sale: Sale, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = modifier) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF1EDFC)), contentAlignment = Alignment.Center) { Icon(if (sale.favorite) Icons.Default.Star else Icons.Default.Receipt, null, tint = Purple, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(sale.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1); Text("${sale.quantity} × ${rupiah(sale.price)} · ${dateTimeLabel(sale.createdAt)}", color = Muted, fontSize = 11.sp, maxLines = 1) }
        Text(rupiah(sale.total), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    } }
}

@Composable private fun DashboardPage(sales: List<Sale>) {
    val now = System.currentTimeMillis(); val today = sales.filter { sameDay(it.createdAt, now) }; val week = sales.filter { it.createdAt >= now - 7L * 86400000 }; val month = sales.filter { monthKey(it.createdAt) == monthKey(now) }
    val daily = (6 downTo 0).map { delta -> val day = now - delta * 86400000L; sales.filter { sameDay(it.createdAt, day) }.sumOf { it.total } }
    val weeks = (3 downTo 0).map { offset -> val start = startOfDay(now) - (offset * 7L + 6L) * 86400000; sales.filter { it.createdAt >= start && it.createdAt < start + 7L * 86400000 }.sumOf { it.total } }
    val products = sales.groupBy { it.name }.mapValues { it.value.sumOf(Sale::total) }.entries.sortedByDescending { it.value }.take(6)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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

@Composable private fun SaleForm(dao: SaleDao, initial: Sale?, onClose: () -> Unit, onSaved: (Sale) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }; var price by remember(initial?.id) { mutableStateOf(initial?.price?.toString() ?: "") }; var quantity by remember(initial?.id) { mutableStateOf(initial?.quantity?.toString() ?: "1") }; var note by remember(initial?.id) { mutableStateOf(initial?.note ?: "") }; var favorite by remember(initial?.id) { mutableStateOf(initial?.favorite ?: false) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }; val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose) { Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) { Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(if (initial == null) "Transaksi baru" else "Edit transaksi", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink); IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Tutup") } }
        OutlinedTextField(name, { name = it; scope.launch { suggestions = if (it.length > 1) dao.suggestions(it) else emptyList() } }, label = { Text("Nama barang/transaksi") }, placeholder = { Text("Ketik nama atau pilih saran") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true, trailingIcon = { IconButton(onClick = { favorite = !favorite }) { Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorit", tint = if (favorite) Color(0xFFE8A317) else Muted) } })
        suggestions.forEach { item -> TextButton(onClick = { name = item; suggestions = emptyList(); scope.launch { dao.latestPrice(item)?.let { price = it.toString() } } }) { Icon(Icons.Default.History, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(item) } }
        Spacer(Modifier.height(8.dp)); OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Harga satuan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        Spacer(Modifier.height(8.dp)); OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(5) }, label = { Text("Jumlah") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        Spacer(Modifier.height(8.dp)); OutlinedTextField(note, { note = it }, label = { Text("Catatan (opsional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), maxLines = 2)
        val total = (price.toLongOrNull() ?: 0) * (quantity.toIntOrNull() ?: 1)
        Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Total", color = Muted); Spacer(Modifier.weight(1f)); Text(rupiah(total), fontWeight = FontWeight.Bold, color = Ink, fontSize = 19.sp) }
        Spacer(Modifier.height(14.dp)); Button(onClick = {
            val sale = Sale(id = initial?.id ?: 0, name = name.trim(), price = price.toLongOrNull() ?: 0, quantity = quantity.toIntOrNull() ?: 1, note = note.trim(), createdAt = initial?.createdAt ?: System.currentTimeMillis(), favorite = favorite)
            scope.launch { if (initial == null) onSaved(sale.copy(id = dao.insert(sale))) else { dao.update(sale); onSaved(sale) } }
        }, enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && (quantity.toIntOrNull() ?: 0) > 0, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text(if (initial == null) "Simpan transaksi" else "Simpan perubahan") }
    } } }
}

@Composable private fun ReceiptDialog(sale: Sale, settings: StoreSettings, onClose: () -> Unit, onPrint: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    Dialog(onDismissRequest = onClose) { Surface(color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (settings.showLogo && settings.logoUri.isNotBlank()) AsyncImage(settings.logoUri, contentDescription = "Logo toko", modifier = Modifier.size((settings.imageWidth / 4).coerceIn(48, 96).dp).clip(RoundedCornerShape(10.dp)))
        Text(settings.name.ifBlank { "Gerai Go" }, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
        if (settings.address.isNotBlank()) Text(settings.address, fontSize = 12.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (settings.contact.isNotBlank()) Text(settings.contact, fontSize = 12.sp, color = Muted)
        if (settings.header.isNotBlank()) Text(settings.header, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Ink)
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFECE9F0)); Row(Modifier.fillMaxWidth()) { Text(sale.name, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.SemiBold); Text("${sale.quantity} × ${rupiah(sale.price)}", color = Muted, fontSize = 12.sp) }
        if (sale.note.isNotBlank()) Text(sale.note, Modifier.fillMaxWidth().padding(top = 7.dp), color = Muted, fontSize = 12.sp)
        Text(dateTimeLabel(sale.createdAt), Modifier.fillMaxWidth().padding(top = 7.dp), color = Muted, fontSize = 11.sp)
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFECE9F0)); Row(Modifier.fillMaxWidth()) { Text("TOTAL", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(rupiah(sale.total), fontWeight = FontWeight.Bold, color = Purple, fontSize = 18.sp) }
        if (settings.showExtraImage && settings.extraImageUri.isNotBlank()) AsyncImage(settings.extraImageUri, contentDescription = "Gambar tambahan nota", modifier = Modifier.padding(top = 12.dp).width((settings.imageWidth / 2).coerceIn(90, 240).dp).heightIn(max = 130.dp))
        if (settings.footer.isNotBlank()) Text(settings.footer, Modifier.padding(top = 13.dp), color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.SaveAlt, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Simpan") }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Bagikan") }
        }
        Button(onClick = onPrint, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(7.dp)); Text("Cetak thermal") }
        TextButton(onClick = onClose) { Text("Tutup") }
    } } }
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

@Composable private fun SettingsPage(activity: ComponentActivity, sales: List<Sale>, settings: StoreSettings, onSave: (StoreSettings) -> Unit, onBackup: (String, Uri) -> Unit, onRestore: (Uri) -> Unit) {
    val context = LocalContext.current
    var model by remember(settings) { mutableStateOf(settings) }
    val chooseLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; model = model.copy(logoUri = it.toString(), showLogo = true) } }
    val chooseExtra = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; model = model.copy(extraImageUri = it.toString(), showExtraImage = true) } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) onBackup(backupJson(context, sales, model), uri) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onRestore(uri) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
        Button(onClick = { onSave(model) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Simpan pengaturan nota") }
        Spacer(Modifier.height(20.dp)); Text("Backup lokal", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp); Text("${sales.size} transaksi tersimpan di perangkat", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { export.launch("gerai-go-backup.json") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(4.dp)); Text("Export") }; OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(4.dp)); Text("Import") } }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable private fun SettingInput(label: String, value: String, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(label) }, shape = RoundedCornerShape(13.dp), singleLine = singleLine, maxLines = if (singleLine) 1 else 3, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = Ink, fontSize = 13.sp); Switch(checked, onChange) } }

@Composable private fun EmptyState(title: String, body: String, action: () -> Unit) { Column(Modifier.fillMaxWidth().padding(top = 45.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFF0EAFE)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ReceiptLong, null, tint = Purple) }; Spacer(Modifier.height(14.dp)); Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(body, color = Muted, fontSize = 12.sp); Spacer(Modifier.height(13.dp)); TextButton(onClick = action) { Text("Catat transaksi", color = Purple) } } }

private fun shareReceipt(context: Context, sale: Sale, settings: StoreSettings) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Nota ${settings.name}"); putExtra(Intent.EXTRA_TEXT, receiptText(sale, settings)) }
    context.startActivity(Intent.createChooser(intent, "Bagikan nota"))
}
private fun receiptText(sale: Sale, settings: StoreSettings) = buildString {
    appendLine(settings.name.ifBlank { "Gerai Go" }); if (settings.address.isNotBlank()) appendLine(settings.address); if (settings.contact.isNotBlank()) appendLine(settings.contact)
    if (settings.header.isNotBlank()) appendLine(settings.header); appendLine("------------------------------"); appendLine(sale.name); appendLine("${sale.quantity} x ${rupiah(sale.price)} = ${rupiah(sale.total)}")
    if (sale.note.isNotBlank()) appendLine("Catatan: ${sale.note}"); appendLine("TOTAL: ${rupiah(sale.total)}"); appendLine(dateTimeLabel(sale.createdAt)); appendLine(settings.footer)
}

private fun backupJson(context: Context, sales: List<Sale>, settings: StoreSettings): String {
    val list = JSONArray(); sales.forEach { list.put(JSONObject().put("name", it.name).put("price", it.price).put("quantity", it.quantity).put("note", it.note).put("createdAt", it.createdAt).put("favorite", it.favorite)) }
    fun image(uri: String): String = if (uri.isBlank()) "" else runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP) } }.getOrNull().orEmpty()
    return JSONObject().put("format", "gerai-go-backup").put("version", 1).put("transactions", list).put("settings", JSONObject().put("name", settings.name).put("address", settings.address).put("contact", settings.contact).put("header", settings.header).put("footer", settings.footer).put("logoUri", settings.logoUri).put("extraImageUri", settings.extraImageUri).put("logoImage", image(settings.logoUri)).put("extraImage", image(settings.extraImageUri)).put("showLogo", settings.showLogo).put("showExtraImage", settings.showExtraImage).put("imageWidth", settings.imageWidth).put("paperWidth", settings.paperWidth)).toString(2)
}
private fun restoreImages(context: Context, j: JSONObject): StoreSettings {
    fun restore(key: String, filename: String, legacyUri: String): String {
        val encoded = j.optString(key)
        if (encoded.isBlank()) return legacyUri
        val file = java.io.File(context.filesDir, filename)
        file.writeBytes(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
        return Uri.fromFile(file).toString()
    }
    return StoreSettings(name = j.optString("name", "Gerai Go"), address = j.optString("address"), contact = j.optString("contact"), header = j.optString("header"), footer = j.optString("footer", "Terima kasih telah berbelanja"), logoUri = restore("logoImage", "backup-logo", j.optString("logoUri")), extraImageUri = restore("extraImage", "backup-extra-image", j.optString("extraImageUri")), showLogo = j.optBoolean("showLogo", true), showExtraImage = j.optBoolean("showExtraImage"), imageWidth = j.optInt("imageWidth", 160), paperWidth = j.optInt("paperWidth", 58))
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply { timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun sameDay(a: Long, b: Long) = startOfDay(a) == startOfDay(b)
private fun monthKey(time: Long) = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(time))
private fun dateLabel(time: Long) = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(time))
private fun dateTimeLabel(time: Long) = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("id", "ID")).format(Date(time))
private fun shortMoney(value: Long) = when { value >= 1_000_000 -> "%.1f jt".format(Locale.US, value / 1_000_000.0); value >= 1000 -> "%.0f rb".format(Locale.US, value / 1000.0); else -> value.toString() }
