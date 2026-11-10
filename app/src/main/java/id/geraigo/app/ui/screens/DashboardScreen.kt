package id.geraigo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.Sale
import id.geraigo.app.ui.components.*
import id.geraigo.app.ui.theme.BrandPurple

private val ChartColors = listOf(
    Color(0xFF6941C6),
    Color(0xFF8F72D7),
    Color(0xFF4E9C8D),
    Color(0xFFE07A5F),
    Color(0xFF3D5A80),
    Color(0xFFF2CC8F)
)

@Composable
fun DashboardScreen(orders: List<OrderReceipt>) {
    val now = System.currentTimeMillis()
    val today = orders.filter { sameDay(it.createdAt, now) }
    val week = orders.filter { it.createdAt >= now - 7L * 86_400_000L }
    val month = orders.filter { monthKey(it.createdAt) == monthKey(now) }

    val daily = (6 downTo 0).map { delta ->
        val day = now - delta * 86_400_000L
        orders.filter { sameDay(it.createdAt, day) }.sumOf { it.total }
    }

    val weeks = (3 downTo 0).map { offset ->
        val start = startOfDay(now) - (offset * 7L + 6L) * 86_400_000L
        orders.filter { it.createdAt in start until (start + 7L * 86_400_000L) }.sumOf { it.total }
    }

    val products = orders
        .flatMap { it.items }
        .groupBy { it.name }
        .mapValues { it.value.sumOf(Sale::total) }
        .entries
        .sortedByDescending { it.value }
        .take(6)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Dashboard", "Ringkasan metrik keuangan dan penjualan")

        val activeKasbon = orders.filter { it.isKasbon }
        val activeKasbonTotal = activeKasbon.sumOf { it.total }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Kas masuk hari ini", rupiah(today.filter { !it.isKasbon }.sumOf { it.total }), Modifier.weight(1f))
            MetricCard("Transaksi hari ini", "${today.size}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val avgToday = if (today.isEmpty()) 0L else today.sumOf { it.total } / today.size
            MetricCard("Rata-rata transaksi", rupiah(avgToday), Modifier.weight(1f))
            MetricCard("Kasbon belum lunas", rupiah(activeKasbonTotal), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("7 hari terakhir", rupiah(week.sumOf { it.total }), Modifier.weight(1f))
            MetricCard("Bulan ini", rupiah(month.sumOf { it.total }), Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        ChartSection("Pemasukan harian · 7 hari") {
            LineChart(daily)
        }

        Spacer(Modifier.height(14.dp))
        ChartSection("Perbandingan pemasukan mingguan") {
            BarChart(weeks)
        }

        Spacer(Modifier.height(14.dp))
        ChartSection("Persentase penjualan per item") {
            val totalProductsValue = products.sumOf { it.value }
            if (products.isEmpty() || totalProductsValue <= 0) {
                Text(
                    "Belum ada data penjualan",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                    fontSize = 13.sp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PieChart(
                        values = products.map { it.value },
                        modifier = Modifier.size(130.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        products.forEachIndexed { i, entry ->
                            val pct = if (totalProductsValue > 0) {
                                ((entry.value.toDouble() / totalProductsValue) * 100).toInt()
                            } else 0
                            Row(
                                Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ChartColors[i % ChartColors.size])
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    entry.key.ifBlank { "Barang" },
                                    Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    "$pct%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ChartSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(8.dp))
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun LineChart(values: List<Long>) {
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(8.dp)
    ) {
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val pts = values.mapIndexed { i, v ->
            val ratio = (v.toFloat() / max).coerceIn(0f, 1f)
            androidx.compose.ui.geometry.Offset(i * step, size.height - (ratio * (size.height - 12f)) - 6f)
        }
        for (i in 0 until pts.lastIndex) {
            drawLine(BrandPurple, pts[i], pts[i + 1], 3.dp.toPx(), cap = StrokeCap.Round)
        }
        pts.forEach { pt ->
            drawCircle(BrandPurple, 3.5.dp.toPx(), pt)
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (6 downTo 0).forEach {
            Text(if (it == 0) "Hari ini" else "-${it}h", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BarChart(values: List<Long>) {
    val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Row(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, value ->
            val ratio = (value.toFloat() / max).coerceIn(0.04f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(shortMoney(value), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(32.dp)
                        .fillMaxHeight(ratio)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(ChartColors[i % ChartColors.size])
                )
                Spacer(Modifier.height(4.dp))
                Text("M-${3 - i}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PieChart(values: List<Long>, modifier: Modifier = Modifier) {
    val sum = values.sum().coerceAtLeast(1L)
    Canvas(modifier) {
        var startAngle = -90f
        values.forEachIndexed { i, value ->
            val sweep = (value.toFloat() / sum) * 360f
            if (sweep > 0f) {
                drawArc(
                    color = ChartColors[i % ChartColors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true
                )
                startAngle += sweep
            }
        }
    }
}
