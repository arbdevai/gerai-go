package id.geraigo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.ui.components.*
import id.geraigo.app.ui.theme.BrandPurple

@Composable
fun CashScreen(
    orders: List<OrderReceipt>,
    favorites: List<String>,
    isLoading: Boolean,
    onFavorite: (String) -> Unit,
    onReceipt: (OrderReceipt) -> Unit
) {
    val now = System.currentTimeMillis()
    val today = orders.filter { sameDay(it.createdAt, now) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        ScreenHeader("Kas hari ini", "Semua transaksi tersimpan di perangkat")

        if (isLoading) {
            CashScreenSkeleton()
        } else {
            // Summary Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6941C6), Color(0xFF5635A5))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "TOTAL PEMASUKAN",
                            Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.1.sp
                        )
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        rupiah(today.sumOf { it.total }),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${today.size} transaksi",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "·",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            dateLabel(now),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Favorites
            if (favorites.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = BrandPurple, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Favorit", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(favorites) { name ->
                        SuggestionChip(
                            onClick = { onFavorite(name) },
                            label = { Text(name) },
                            icon = { Icon(Icons.Default.Bolt, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            // Recent Header
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Terbaru", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.weight(1f))
                Text("${orders.size} transaksi", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))

            if (orders.isEmpty()) {
                EmptyState("Belum ada transaksi", "Tekan tombol + di bawah untuk mulai mencatat penjualan.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(orders.take(15), key = { it.key }) { order ->
                        SaleRow(order, onClick = { onReceipt(order) })
                    }
                }
            }
        }
    }
}

@Composable
fun SaleRow(order: OrderReceipt, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BrandPurple.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (order.items.any { it.favorite }) Icons.Default.Star else Icons.Default.Receipt,
                    contentDescription = null,
                    tint = BrandPurple,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val firstName = order.items.firstOrNull()?.name.orEmpty().ifBlank { "Barang" }
                val extraText = if (order.items.size > 1) " +${order.items.size - 1} barang" else ""
                Text(
                    firstName + extraText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    "${order.items.size} item · ${dateTimeLabel(order.createdAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Text(
                rupiah(order.total),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CashScreenSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        // Shimmer summary box
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(22.dp))
                .shimmerEffect()
        )
        Spacer(Modifier.height(20.dp))
        // Shimmer header row
        Box(
            Modifier
                .width(120.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect()
        )
        Spacer(Modifier.height(12.dp))
        // Shimmer list items
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .shimmerEffect()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
