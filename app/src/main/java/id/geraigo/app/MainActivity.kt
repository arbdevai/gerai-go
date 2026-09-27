package id.geraigo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import id.geraigo.app.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val Purple = Color(0xFF6941C6)
private val Ink = Color(0xFF201A2B)
private val Muted = Color(0xFF888393)
private val AppBackground = Color(0xFFF7F6FA)
private fun rupiah(value: Long) = "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val dao = GeraiDatabase.get(this).sales()
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Purple, background = AppBackground, surface = Color.White)) { GeraiApp(dao) } }
    }
}

@Composable private fun GeraiApp(dao: SaleDao) {
    var tab by remember { mutableStateOf(0) }
    var sales by remember { mutableStateOf(emptyList<Sale>()) }
    var query by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<Sale?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { dao.observeAll().collect { sales = it } }
    val filtered = remember(sales, query) { sales.filter { it.name.contains(query, true) || it.note.contains(query, true) } }
    Scaffold(containerColor = AppBackground, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            listOf("Kas", "Riwayat", "Dashboard", "Pengaturan").forEachIndexed { i, label ->
                val icon = listOf(Icons.Default.AddCard, Icons.Default.ReceiptLong, Icons.Default.BarChart, Icons.Default.Settings)[i]
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Purple, selectedTextColor = Purple, indicatorColor = Color(0xFFF0EAFE)))
            }
        }
    }, floatingActionButton = { if (tab == 0) FloatingActionButton(onClick = { showForm = true }, containerColor = Purple, contentColor = Color.White) { Icon(Icons.Default.Add, "Tambah transaksi") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            when (tab) {
                0 -> CashPage(sales, onAdd = { showForm = true }, onReceipt = { receipt = it })
                1 -> HistoryPage(filtered, query, { query = it }, onReceipt = { receipt = it }, onDelete = { scope.launch { dao.delete(it.id) } })
                2 -> DashboardPage(sales)
                else -> SettingsPage(sales)
            }
        }
    }
    if (showForm) SaleForm(dao, onClose = { showForm = false }, onSaved = { receipt = it; showForm = false })
    receipt?.let { ReceiptDialog(it, onClose = { receipt = null }) }
}

@Composable private fun Header(title: String, subtitle: String) {
    Spacer(Modifier.height(20.dp)); Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink); Spacer(Modifier.height(4.dp)); Text(subtitle, color = Muted, fontSize = 14.sp); Spacer(Modifier.height(20.dp))
}

@Composable private fun CashPage(sales: List<Sale>, onAdd: () -> Unit, onReceipt: (Sale) -> Unit) {
    Header("Catat transaksi", "Catat penjualan dengan cepat")
    Surface(shape = RoundedCornerShape(24.dp), color = Purple, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Pemasukan hari ini", color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
            val today = sales.filter { sameDay(it.createdAt, System.currentTimeMillis()) }
            Text(rupiah(today.sumOf { it.total }), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); Text("${today.size} transaksi  ·  Hari ini", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(22.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Transaksi terbaru", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink); Spacer(Modifier.weight(1f)); Text("Lihat semua", color = Purple, fontSize = 13.sp) }
    Spacer(Modifier.height(10.dp))
    if (sales.isEmpty()) EmptyState("Belum ada transaksi", "Transaksi yang dicatat akan muncul di sini.", onAdd)
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) { items(sales.take(5), key = { it.id }) { SaleRow(it) { onReceipt(it) } } }
}

@Composable private fun HistoryPage(sales: List<Sale>, query: String, onQuery: (String) -> Unit, onReceipt: (Sale) -> Unit, onDelete: (Sale) -> Unit) {
    Header("Riwayat", "Semua transaksi tersimpan di perangkat")
    OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Cari transaksi") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFFE9E6EF)))
    Spacer(Modifier.height(14.dp)); Text("${sales.size} transaksi", color = Muted, fontSize = 13.sp)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)) { items(sales, key = { it.id }) { sale -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { SaleRow(sale) { onReceipt(sale) } }; IconButton(onClick = { onDelete(sale) }) { Icon(Icons.Default.DeleteOutline, "Hapus", tint = Muted) } } } }
}

@Composable private fun SaleRow(sale: Sale, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF1EDFC)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Receipt, null, tint = Purple, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(sale.name, color = Ink, fontWeight = FontWeight.SemiBold); Text("${sale.quantity} × ${rupiah(sale.price)} · ${timeLabel(sale.createdAt)}", color = Muted, fontSize = 12.sp) }
            Text(rupiah(sale.total), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable private fun DashboardPage(sales: List<Sale>) {
    Header("Dashboard", "Ringkasan keuangan usaha")
    val now = System.currentTimeMillis(); val today = sales.filter { sameDay(it.createdAt, now) }; val week = sales.filter { it.createdAt >= now - 7L * 86400000 }; val month = sales.filter { monthKey(it.createdAt) == monthKey(now) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Hari ini", rupiah(today.sumOf { it.total }), Modifier.weight(1f)); MetricCard("Transaksi", "${today.size}", Modifier.weight(1f)) }
    Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Minggu ini", rupiah(week.sumOf { it.total }), Modifier.weight(1f)); MetricCard("Bulan ini", rupiah(month.sumOf { it.total }), Modifier.weight(1f)) }
    Spacer(Modifier.height(18.dp)); Text("Pemasukan 7 hari", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink); Spacer(Modifier.height(10.dp))
    val daily = (6 downTo 0).map { delta -> val day = now - delta * 86400000L; sales.filter { sameDay(it.createdAt, day) }.sumOf { it.total } }
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { SimpleChart(daily); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { (6 downTo 0).forEach { Text(if (it == 0) "Hari ini" else "-${it}h", color = Muted, fontSize = 10.sp) } } } }
    Spacer(Modifier.height(14.dp)); Text("Komposisi pemasukan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink); Spacer(Modifier.height(10.dp))
    val top = sales.groupBy { it.name }.mapValues { it.value.sumOf(Sale::total) }.entries.sortedByDescending { it.value }.take(3)
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { if (top.isEmpty()) Text("Belum ada data untuk ditampilkan", color = Muted) else top.forEachIndexed { i, entry -> Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(listOf(Purple, Color(0xFF9A7CE0), Color(0xFFC6B7EE))[i])); Spacer(Modifier.width(9.dp)); Text(entry.key, Modifier.weight(1f), color = Ink); Text(rupiah(entry.value), color = Muted, fontSize = 12.sp) } } } }
}

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier) { Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = modifier) { Column(Modifier.padding(15.dp)) { Text(label, color = Muted, fontSize = 12.sp); Spacer(Modifier.height(7.dp)); Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1) } } }

@Composable private fun SimpleChart(values: List<Long>) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(130.dp).padding(vertical = 12.dp)) {
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexed { i, v -> androidx.compose.ui.geometry.Offset(i * step, size.height - (v.toFloat() / max) * size.height) }
        for (i in 0 until points.lastIndex) drawLine(Purple, points[i], points[i + 1], strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        points.forEach { drawCircle(Purple, radius = 4.dp.toPx(), center = it) }
    }
}

@Composable private fun SettingsPage(sales: List<Sale>) {
    Header("Pengaturan", "Atur identitas dan nota toko")
    var shop by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var contact by remember { mutableStateOf("") }; var footer by remember { mutableStateOf("Terima kasih telah berbelanja") }; var saved by remember { mutableStateOf(false) }
    listOf("Nama toko" to shop, "Alamat toko" to address, "Nomor kontak" to contact, "Footer nota" to footer).forEach { (label, value) -> OutlinedTextField(value, { v -> when(label) { "Nama toko" -> shop = v; "Alamat toko" -> address = v; "Nomor kontak" -> contact = v; else -> footer = v }; saved = false }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(14.dp), singleLine = label != "Alamat toko" && label != "Footer nota") }
    Button(onClick = { saved = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(if (saved) "Tersimpan" else "Simpan pengaturan") }
    Spacer(Modifier.height(20.dp)); Text("Data & cadangan", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink); Spacer(Modifier.height(8.dp)); Text("${sales.size} transaksi tersimpan offline di perangkat ini.", color = Muted, fontSize = 13.sp)
    Text("Export dan import cadangan akan tersedia pada pembaruan berikutnya.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable private fun SaleForm(dao: SaleDao, onClose: () -> Unit, onSaved: (Sale) -> Unit) {
    var name by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("1") }; var note by remember { mutableStateOf("") }; var suggestions by remember { mutableStateOf(emptyList<String>()) }; val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose) { Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Transaksi baru", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink); IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Tutup") } }
        OutlinedTextField(name, { name = it; scope.launch { suggestions = if (it.length > 1) dao.suggestions(it) else emptyList() } }, label = { Text("Nama transaksi") }, placeholder = { Text("Contoh: Kopi susu") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        suggestions.forEach { item -> TextButton(onClick = { name = item; suggestions = emptyList() }) { Text(item, color = Purple) } }
        Spacer(Modifier.height(9.dp)); OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Harga satuan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        Spacer(Modifier.height(9.dp)); OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(4) }, label = { Text("Jumlah") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        Spacer(Modifier.height(9.dp)); OutlinedTextField(note, { note = it }, label = { Text("Catatan (opsional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), maxLines = 2)
        val total = (price.toLongOrNull() ?: 0) * (quantity.toIntOrNull() ?: 1)
        Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Total", color = Muted); Spacer(Modifier.weight(1f)); Text(rupiah(total), fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp) }
        Spacer(Modifier.height(14.dp)); Button(onClick = { val sale = Sale(name = name.trim(), price = price.toLongOrNull() ?: 0, quantity = quantity.toIntOrNull() ?: 1, note = note.trim()); scope.launch { val id = dao.insert(sale); onSaved(sale.copy(id = id)) } }, enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && (quantity.toIntOrNull() ?: 0) > 0, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("Simpan transaksi") }
    } } }
}

@Composable private fun ReceiptDialog(sale: Sale, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) { Surface(color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF0EAFE)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Purple) }
        Spacer(Modifier.height(10.dp)); Text("Transaksi tersimpan", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink); Text("Preview nota", color = Muted, fontSize = 13.sp)
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color(0xFFECE9F0)); Row(Modifier.fillMaxWidth()) { Text(sale.name, Modifier.weight(1f), color = Ink); Text("${sale.quantity} × ${rupiah(sale.price)}", color = Muted) }; if (sale.note.isNotBlank()) Text(sale.note, Modifier.fillMaxWidth().padding(top = 8.dp), color = Muted, fontSize = 12.sp)
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFECE9F0)); Row(Modifier.fillMaxWidth()) { Text("TOTAL", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(rupiah(sale.total), fontWeight = FontWeight.Bold, color = Purple, fontSize = 18.sp) }
        Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Selesai") }
        Text("Cetak thermal dan bagikan nota akan segera tersedia", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    } } }
}

@Composable private fun EmptyState(title: String, body: String, action: () -> Unit) { Column(Modifier.fillMaxWidth().padding(top = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFF0EAFE)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ReceiptLong, null, tint = Purple) }; Spacer(Modifier.height(14.dp)); Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(body, color = Muted, fontSize = 12.sp); Spacer(Modifier.height(16.dp)); TextButton(onClick = action) { Text("Catat transaksi", color = Purple) } } }
private fun sameDay(a: Long, b: Long) = Calendar.getInstance().run { timeInMillis = a; val day = get(Calendar.DAY_OF_YEAR); val year = get(Calendar.YEAR); timeInMillis = b; day == get(Calendar.DAY_OF_YEAR) && year == get(Calendar.YEAR) }
private fun monthKey(time: Long) = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(time))
private fun timeLabel(time: Long) = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(time))
