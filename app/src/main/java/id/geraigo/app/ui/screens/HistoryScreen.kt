package id.geraigo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.ui.components.EmptyState
import id.geraigo.app.ui.components.ScreenHeader
import id.geraigo.app.ui.components.dateLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    orders: List<OrderReceipt>,
    query: String,
    selectedDate: Long?,
    onQuery: (String) -> Unit,
    onDate: (Long?) -> Unit,
    onReceipt: (OrderReceipt) -> Unit,
    onEdit: (OrderReceipt) -> Unit,
    onDelete: (OrderReceipt) -> Unit,
    onToggleFavorite: (OrderReceipt) -> Unit,
    onMarkPaid: (OrderReceipt) -> Unit
) {
    var dateDialog by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("ALL") } // "ALL", "PAID", "KASBON"

    val displayedOrders = remember(orders, statusFilter) {
        when (statusFilter) {
            "PAID" -> orders.filter { !it.isKasbon }
            "KASBON" -> orders.filter { it.isKasbon }
            else -> orders
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        ScreenHeader("Riwayat", "Cari dan kelola riwayat transaksi")

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari nama barang, pelanggan, atau catatan") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        // Status filter chips
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = statusFilter == "ALL",
                onClick = { statusFilter = "ALL" },
                label = { Text("Semua") }
            )
            FilterChip(
                selected = statusFilter == "PAID",
                onClick = { statusFilter = "PAID" },
                label = { Text("Lunas") }
            )
            FilterChip(
                selected = statusFilter == "KASBON",
                onClick = { statusFilter = "KASBON" },
                label = { Text("Kasbon") }
            )
            Spacer(Modifier.weight(1f))
            Text("${displayedOrders.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = selectedDate == null,
                onClick = { onDate(null) },
                label = { Text("Semua tanggal") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = selectedDate != null,
                onClick = { dateDialog = true },
                label = { Text(selectedDate?.let { dateLabel(it) } ?: "Pilih tanggal") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (displayedOrders.isEmpty()) {
            EmptyState("Tidak ada transaksi ditemukan", "Ubah kata kunci pencarian, status kasbon, atau tanggal filter.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
            ) {
                items(displayedOrders, key = { it.key }) { order ->
                    var menuOpen by remember(order.key) { mutableStateOf(false) }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SaleRow(
                                order = order,
                                onClick = { onReceipt(order) },
                                modifier = Modifier.weight(1f)
                            )
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Default.MoreVert, "Aksi transaksi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (order.isKasbon) {
                                        DropdownMenuItem(
                                            text = { Text("Tandai Sudah Lunas", color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A)) },
                                            onClick = { menuOpen = false; onMarkPaid(order) }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Lihat nota") },
                                        leadingIcon = { Icon(Icons.Default.ReceiptLong, null) },
                                        onClick = { menuOpen = false; onReceipt(order) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit transaksi") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = { menuOpen = false; onEdit(order) }
                                    )
                                    val isFav = order.items.all { it.favorite }
                                    DropdownMenuItem(
                                        text = { Text(if (isFav) "Hapus dari favorit" else "Jadikan favorit") },
                                        leadingIcon = { Icon(if (isFav) Icons.Default.Star else Icons.Default.StarBorder, null) },
                                        onClick = { menuOpen = false; onToggleFavorite(order) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Hapus transaksi", color = Color(0xFFD32F2F)) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFD32F2F)) },
                                        onClick = { menuOpen = false; onDelete(order) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { dateDialog = false },
            confirmButton = {
                TextButton(onClick = { onDate(picker.selectedDateMillis); dateDialog = false }) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDate(null); dateDialog = false }) {
                    Text("Hapus filter")
                }
            }
        ) {
            DatePicker(state = picker)
        }
    }
}
