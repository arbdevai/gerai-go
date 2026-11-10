package id.geraigo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.Sale
import id.geraigo.app.data.SaleDao
import id.geraigo.app.data.StoreSettings
import id.geraigo.app.ui.components.RupiahInputTransformation
import id.geraigo.app.ui.components.rupiah
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToLong

data class DraftItem(
    val key: String = UUID.randomUUID().toString(),
    val name: String = "",
    val price: String = "",
    val quantity: String = "1",
    val favorite: Boolean = false
)

@Composable
fun SaleFormDialog(
    dao: SaleDao,
    initial: OrderReceipt?,
    quickItem: Pair<String, Long>?,
    settings: StoreSettings,
    onClose: () -> Unit,
    onSaved: (OrderReceipt) -> Unit
) {
    var items by remember(initial?.key) {
        mutableStateOf(
            initial?.items?.map {
                DraftItem(
                    name = it.name,
                    price = it.price.toString(),
                    quantity = it.quantity.toString(),
                    favorite = it.favorite
                )
            } ?: listOf(
                DraftItem(
                    name = quickItem?.first.orEmpty(),
                    price = quickItem?.second?.takeIf { it > 0 }?.toString().orEmpty()
                )
            )
        )
    }

    var note by remember(initial?.key) { mutableStateOf(initial?.note.orEmpty()) }
    var isKasbon by remember(initial?.key) { mutableStateOf(initial?.isKasbon ?: false) }
    var customerName by remember(initial?.key) { mutableStateOf(initial?.customerName.orEmpty()) }
    var paidCashText by remember(initial?.key) {
        mutableStateOf(if ((initial?.paidCash ?: 0L) > 0L) initial?.paidCash.toString() else "")
    }
    val previewCreatedAt = remember(initial?.key, quickItem) { initial?.createdAt ?: System.currentTimeMillis() }
    val receiptKey = remember(initial?.key, quickItem) { initial?.key ?: UUID.randomUUID().toString().replace("-", "") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var focusedItem by remember { mutableIntStateOf(-1) }
    var formError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Dynamic focus requesters for rapid Enter-key jumping
    val nameFocusRequesters = remember { mutableStateListOf<FocusRequester>() }
    val priceFocusRequesters = remember { mutableStateListOf<FocusRequester>() }
    val qtyFocusRequesters = remember { mutableStateListOf<FocusRequester>() }

    while (nameFocusRequesters.size < items.size) nameFocusRequesters.add(FocusRequester())
    while (priceFocusRequesters.size < items.size) priceFocusRequesters.add(FocusRequester())
    while (qtyFocusRequesters.size < items.size) qtyFocusRequesters.add(FocusRequester())

    fun changeItem(index: Int, value: DraftItem) {
        items = items.toMutableList().also { it[index] = value }
    }

    fun addNewItemAndFocus() {
        items = items + DraftItem()
        scope.launch {
            delay(80)
            if (nameFocusRequesters.size >= items.size) {
                nameFocusRequesters[items.lastIndex].requestFocus()
            }
        }
    }

    val appliedSettings = remember(initial, settings) {
        if (initial == null) settings else settings.copy(
            taxEnabled = initial.taxPercent > 0.0 || initial.taxAmount > 0,
            taxPercent = initial.taxPercent,
            adminFeeEnabled = initial.adminPercent > 0.0 || initial.adminAmount > 0,
            adminFeePercent = initial.adminPercent
        )
    }

    val subtotal = items.sumOf { (it.price.toLongOrNull() ?: 0L) * (it.quantity.toIntOrNull() ?: 0) }
    val tax = if (appliedSettings.taxEnabled) (subtotal * appliedSettings.taxPercent / 100.0).roundToLong() else 0L
    val admin = if (appliedSettings.adminFeeEnabled) (subtotal * appliedSettings.adminFeePercent / 100.0).roundToLong() else 0L
    val totalAmount = subtotal + tax + admin
    val paidCashAmount = paidCashText.toLongOrNull() ?: 0L
    val changeAmount = (paidCashAmount - totalAmount).coerceAtLeast(0L)
    val shortageAmount = (totalAmount - paidCashAmount).coerceAtLeast(0L)

    val cashSuggestions = remember(totalAmount) {
        if (totalAmount <= 0L) emptyList<Long>()
        else {
            val list = linkedSetOf<Long>()
            val candidates = listOf(10_000L, 20_000L, 50_000L, 100_000L, 200_000L, 500_000L)
            val next10k = ((totalAmount + 9_999L) / 10_000L) * 10_000L
            if (next10k > totalAmount) list.add(next10k)
            val next50k = ((totalAmount + 49_999L) / 50_000L) * 50_000L
            if (next50k > totalAmount) list.add(next50k)
            candidates.filter { it > totalAmount }.take(3).forEach { list.add(it) }
            list.take(3).toList()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    if (initial == null) "Transaksi baru" else "Edit transaksi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Ketik barang, tekan Enter untuk langsung mengisi harga & jumlah",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        items.forEachIndexed { index, item ->
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Barang ${index + 1}",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { changeItem(index, item.copy(favorite = !item.favorite)) }) {
                            Icon(
                                if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorit",
                                tint = if (item.favorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (items.size > 1) {
                            IconButton(onClick = {
                                items = items.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus barang", tint = Color(0xFFD32F2F))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = item.name,
                        onValueChange = { value ->
                            changeItem(index, item.copy(name = value))
                            focusedItem = index
                            scope.launch {
                                suggestions = if (value.length >= 2) dao.suggestions(value) else emptyList()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequesters.getOrElse(index) { FocusRequester() }),
                        label = { Text("Nama barang") },
                        placeholder = { Text("Ketik nama barang") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                if (priceFocusRequesters.size > index) {
                                    priceFocusRequesters[index].requestFocus()
                                }
                            }
                        )
                    )

                    if (focusedItem == index && suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        suggestions.forEach { suggestionName ->
                            TextButton(
                                onClick = {
                                    changeItem(index, item.copy(name = suggestionName))
                                    focusedItem = -1
                                    suggestions = emptyList()
                                    scope.launch {
                                        dao.latestPrice(suggestionName)?.let { p ->
                                            changeItem(index, items[index].copy(price = p.toString()))
                                        }
                                        if (priceFocusRequesters.size > index) {
                                            priceFocusRequesters[index].requestFocus()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.History, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(suggestionName, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = item.price,
                            onValueChange = { input ->
                                changeItem(index, item.copy(price = input.filter(Char::isDigit).take(12)))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(priceFocusRequesters.getOrElse(index) { FocusRequester() }),
                            label = { Text("Harga · Rp") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    if (qtyFocusRequesters.size > index) {
                                        qtyFocusRequesters[index].requestFocus()
                                    }
                                }
                            ),
                            visualTransformation = RupiahInputTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = item.quantity,
                            onValueChange = { input ->
                                changeItem(index, item.copy(quantity = input.filter(Char::isDigit).take(4)))
                            },
                            modifier = Modifier
                                .width(100.dp)
                                .focusRequester(qtyFocusRequesters.getOrElse(index) { FocusRequester() }),
                            label = { Text("Jumlah") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = if (index == items.lastIndex) ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (item.name.isNotBlank() && (item.price.toLongOrNull() ?: 0L) > 0L) {
                                        addNewItemAndFocus()
                                    }
                                },
                                onNext = {
                                    if (index < items.lastIndex) {
                                        nameFocusRequesters[index + 1].requestFocus()
                                    } else {
                                        addNewItemAndFocus()
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subtotal barang", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            rupiah((item.price.toLongOrNull() ?: 0L) * (item.quantity.toIntOrNull() ?: 0)),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = { addNewItemAndFocus() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Tambah barang lain")
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            label = { Text("Catatan nota (opsional)") },
            shape = RoundedCornerShape(14.dp),
            maxLines = 2
        )

        Spacer(Modifier.height(14.dp))

        // Payment status & Cash calculator section
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Metode & Status Pembayaran",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isKasbon,
                        onClick = { isKasbon = false },
                        label = { Text("Lunas (Tunai)") },
                        leadingIcon = { Icon(Icons.Default.Payments, null, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isKasbon,
                        onClick = { isKasbon = true },
                        label = { Text("Kasbon (Belum Lunas)") },
                        leadingIcon = { Icon(Icons.Default.MenuBook, null, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isKasbon) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nama / Panggilan Pembeli *") },
                        placeholder = { Text("Contoh: Pak RT, Mbak Sari, Mas Budi") },
                        leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Disimpan di Lipatan Buku Kasbon digital agar tidak lupa.",
                        color = Color(0xFFE07A5F),
                        fontSize = 11.sp
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Uang Tunai Diterima",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { paidCashText = totalAmount.toString() },
                            label = { Text("Uang Pas", fontSize = 11.sp) }
                        )
                        cashSuggestions.forEach { amount ->
                            SuggestionChip(
                                onClick = { paidCashText = amount.toString() },
                                label = { Text(rupiah(amount), fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = paidCashText,
                        onValueChange = { input ->
                            paidCashText = input.filter(Char::isDigit).take(12)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Uang Diterima · Rp") },
                        placeholder = { Text("0") },
                        visualTransformation = RupiahInputTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    if (paidCashAmount > 0L) {
                        Spacer(Modifier.height(8.dp))
                        if (paidCashAmount >= totalAmount) {
                            Surface(
                                color = Color(0xFF16A34A).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Kembalian:",
                                        color = Color(0xFF16A34A),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        rupiah(changeAmount),
                                        color = Color(0xFF16A34A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFFE07A5F).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Uang Masih Kurang:",
                                        color = Color(0xFFE07A5F),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        rupiah(shortageAmount),
                                        color = Color(0xFFE07A5F),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Direct Live Thermal Preview below inputs
        LiveThermalPreview(
            drafts = items,
            note = note,
            settings = appliedSettings,
            tax = tax,
            admin = admin,
            createdAt = previewCreatedAt,
            receiptKey = receiptKey,
            isKasbon = isKasbon,
            customerName = customerName.trim(),
            paidCash = if (isKasbon) 0L else paidCashAmount
        )

        if (formError.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(formError, color = Color(0xFFD32F2F), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Summary box
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                ReceiptDataRow("Subtotal", rupiah(subtotal), size = 12.sp)
                if (appliedSettings.taxEnabled) {
                    ReceiptDataRow("Pajak ${appliedSettings.taxPercent}%", rupiah(tax), size = 12.sp)
                }
                if (appliedSettings.adminFeeEnabled) {
                    ReceiptDataRow("Biaya admin ${appliedSettings.adminFeePercent}%", rupiah(admin), size = 12.sp)
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ReceiptDataRow("TOTAL", rupiah(totalAmount), bold = true, size = 15.sp)
                if (isKasbon) {
                    ReceiptDataRow("Status", "KASBON (BELUM LUNAS)", bold = true, size = 12.sp)
                    if (customerName.isNotBlank()) {
                        ReceiptDataRow("Pelanggan", customerName.trim(), bold = true, size = 12.sp)
                    }
                } else if (paidCashAmount > 0L) {
                    ReceiptDataRow("Uang Diterima", rupiah(paidCashAmount), size = 12.sp)
                    ReceiptDataRow("Kembalian", rupiah(changeAmount), bold = true, size = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                formError = when {
                    items.isEmpty() -> "Tambahkan minimal satu barang."
                    items.any { it.name.isBlank() } -> "Nama setiap barang wajib diisi."
                    items.any { (it.price.toLongOrNull() ?: 0L) <= 0L } -> "Harga barang harus lebih dari Rp 0."
                    items.any { (it.quantity.toIntOrNull() ?: 0) <= 0 } -> "Jumlah barang minimal 1."
                    isKasbon && customerName.isBlank() -> "Nama pembeli wajib diisi untuk transaksi kasbon."
                    else -> ""
                }
                if (formError.isBlank()) {
                    val key = receiptKey
                    val createdAt = previewCreatedAt
                    val finalPaidCash = if (isKasbon) 0L else paidCashAmount
                    val rows = items.mapIndexed { idx, draft ->
                        Sale(
                            name = draft.name.trim(),
                            price = draft.price.toLong(),
                            quantity = draft.quantity.toInt(),
                            note = if (idx == 0) note.trim() else "",
                            createdAt = createdAt,
                            favorite = draft.favorite,
                            receiptKey = key,
                            taxAmount = if (idx == 0) tax else 0L,
                            adminAmount = if (idx == 0) admin else 0L,
                            taxPercent = if (idx == 0 && appliedSettings.taxEnabled) appliedSettings.taxPercent else 0.0,
                            adminPercent = if (idx == 0 && appliedSettings.adminFeeEnabled) appliedSettings.adminFeePercent else 0.0,
                            isKasbon = isKasbon,
                            customerName = if (idx == 0) customerName.trim() else "",
                            paidCash = if (idx == 0) finalPaidCash else 0L
                        )
                    }

                    scope.launch {
                        runCatching {
                            if (initial == null) {
                                dao.insertAll(rows)
                            } else {
                                dao.replaceReceipt(key, rows)
                            }
                        }.onSuccess {
                            onSaved(OrderReceipt(key, rows))
                        }.onFailure {
                            formError = "Gagal menyimpan transaksi. Coba lagi."
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (initial == null) "Simpan dan lihat nota" else "Simpan perubahan", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun LiveThermalPreview(
    drafts: List<DraftItem>,
    note: String,
    settings: StoreSettings,
    tax: Long,
    admin: Long,
    createdAt: Long,
    receiptKey: String,
    isKasbon: Boolean,
    customerName: String,
    paidCash: Long
) {
    val rows = drafts.mapIndexed { index, item ->
        Sale(
            name = item.name.ifBlank { "Barang" },
            price = item.price.toLongOrNull() ?: 0L,
            quantity = item.quantity.toIntOrNull() ?: 0,
            note = if (index == 0) note else "",
            createdAt = createdAt,
            receiptKey = receiptKey,
            taxAmount = if (index == 0) tax else 0L,
            adminAmount = if (index == 0) admin else 0L,
            taxPercent = if (index == 0 && settings.taxEnabled) settings.taxPercent else 0.0,
            adminPercent = if (index == 0 && settings.adminFeeEnabled) settings.adminFeePercent else 0.0,
            isKasbon = isKasbon,
            customerName = if (index == 0) customerName else "",
            paidCash = if (index == 0) paidCash else 0L
        )
    }
    val sale = remember(rows, createdAt, receiptKey) { OrderReceipt(receiptKey, rows) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pratinjau nota langsung", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text("THERMAL ${settings.paperWidth} mm", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 0.7.sp)
        }
        ReceiptBitmapPaper(sale, settings, Modifier.fillMaxWidth())
    }
}

@Composable
fun ReceiptDataRow(left: String, right: String, bold: Boolean = false, size: TextUnit = 11.sp) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            left,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Text(
            right,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}
