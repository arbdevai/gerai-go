package id.geraigo.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.geraigo.app.ui.theme.BrandPurple
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

fun rupiah(value: Long): String = "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)

fun dateLabel(time: Long): String = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(time))

fun dateTimeLabel(time: Long): String = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("id", "ID")).format(Date(time))

fun shortMoney(value: Long): String = when {
    value >= 1_000_000 -> "%.1f jt".format(Locale.US, value / 1_000_000.0)
    value >= 1000 -> "%.0f rb".format(Locale.US, value / 1000.0)
    else -> value.toString()
}

fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun sameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

fun monthKey(time: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(time))

@Composable
fun StatusBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val foreground = if (isError) Color(0xFFD32F2F) else Color(0xFF2E7D32)
    val background = if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)

    Surface(
        color = background,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(19.dp)
            )
            Text(
                message,
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                color = foreground,
                fontSize = 13.sp
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Tutup pesan",
                    tint = foreground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FloatingNavDock(
    selected: Int,
    onSelected: (Int) -> Unit,
    onAdd: () -> Unit
) {
    val labels = listOf("Penjualan", "Riwayat", "Laporan", "Pengaturan")
    val icons = listOf(
        Icons.Default.AddCard,
        Icons.Default.ReceiptLong,
        Icons.Default.Insights,
        Icons.Default.Storefront
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val slots = listOf(0, 1, -1, 2, 3)
            slots.forEach { index ->
                if (index == -1) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingActionButton(
                            onClick = onAdd,
                            modifier = Modifier.size(50.dp),
                            shape = CircleShape,
                            containerColor = BrandPurple,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Add, "Tambah transaksi")
                        }
                    }
                    return@forEach
                }
                val label = labels[index]
                val active = selected == index
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = { onSelected(index) },
                        color = if (active) BrandPurple.copy(alpha = 0.12f) else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.94f)
                            .animateContentSize(tween(180))
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(vertical = 3.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                icons[index],
                                contentDescription = label,
                                tint = if (active) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                label,
                                color = if (active) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        title,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(4.dp))
    Text(
        subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(BrandPurple.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = BrandPurple)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
