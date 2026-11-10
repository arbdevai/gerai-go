package id.geraigo.app.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import id.geraigo.app.receipt.ReceiptRenderer
import id.geraigo.app.ui.components.dateTimeLabel
import id.geraigo.app.ui.components.rupiah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReceiptDialog(
    sale: OrderReceipt,
    settings: StoreSettings,
    onClose: () -> Unit,
    onPrint: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    onSave: () -> Unit,
    onMarkPaid: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Tutup nota")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    "Struk Belanja",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    "Kertas ${settings.paperWidth} mm",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReceiptBitmapPaper(sale, settings, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShareText,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.TextSnippet, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Teks", fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onShareImage,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Gambar", fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.width(96.dp).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Simpan", fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (sale.isKasbon && onMarkPaid != null) {
            Button(
                onClick = onMarkPaid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tandai Lunas", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onPrint,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cetak Struk")
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ReceiptBitmapPaper(receipt: OrderReceipt, settings: StoreSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(receipt.key, settings) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(receipt, settings) {
        val rendered = withContext(Dispatchers.Default) {
            ReceiptRenderer.render(context, receipt, settings)
        }
        bitmap = rendered
    }

    DisposableEffect(receipt.key, settings) {
        onDispose {
            bitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
        }
    }

    val desiredWidth = if (settings.paperWidth == 80) 330.dp else 240.dp
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val width = minOf(maxWidth, desiredWidth)
        val currentBitmap = bitmap
        if (currentBitmap != null && !currentBitmap.isRecycled) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Pratinjau struk thermal ${settings.paperWidth} mm",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .width(width)
                    .aspectRatio((currentBitmap.width.toFloat() / currentBitmap.height.toFloat()).coerceAtLeast(0.1f))
                    .shadow(8.dp, RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        } else {
            Box(
                modifier = Modifier
                    .width(width)
                    .height(260.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun shareReceiptText(context: Context, receipt: OrderReceipt, settings: StoreSettings) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Nota ${settings.name}")
        putExtra(Intent.EXTRA_TEXT, buildReceiptText(receipt, settings))
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan nota sebagai teks"))
}

fun shareReceiptImage(context: Context, receipt: OrderReceipt, settings: StoreSettings) {
    val bitmap = ReceiptRenderer.render(context, receipt, settings)
    val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(directory, "nota-${receipt.key}.png")
    file.outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    bitmap.recycle()

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, "Nota ${settings.name}")
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Nota Gerai Go", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan nota sebagai gambar"))
}

fun buildReceiptText(receipt: OrderReceipt, settings: StoreSettings): String = buildString {
    fun percent(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    appendLine(settings.name.ifBlank { "Gerai Go" })
    if (settings.address.isNotBlank()) appendLine(settings.address)
    if (settings.contact.isNotBlank()) appendLine(settings.contact)
    if (settings.header.isNotBlank()) appendLine(settings.header)
    appendLine("------------------------------")
    appendLine("NOTA PENJUALAN")
    appendLine("No. struk: GG-${receipt.key.takeLast(6).uppercase()}")
    appendLine(dateTimeLabel(receipt.createdAt))
    if (receipt.isKasbon) {
        appendLine("Status: BELUM LUNAS")
        if (receipt.customerName.isNotBlank()) appendLine("Pelanggan: ${receipt.customerName}")
    } else {
        appendLine("Status: LUNAS")
        if (receipt.customerName.isNotBlank()) appendLine("Pelanggan: ${receipt.customerName}")
    }
    appendLine("------------------------------")
    appendLine("BARANG (${receipt.items.size})")
    receipt.items.forEachIndexed { index, item ->
        appendLine("${index + 1}. ${item.name}")
        appendLine("${item.quantity} x ${rupiah(item.price)}   ${rupiah(item.total)}")
    }
    if (receipt.note.isNotBlank()) appendLine("Catatan: ${receipt.note}")
    appendLine("------------------------------")
    appendLine("Ringkasan")
    appendLine("Total barang: ${receipt.items.sumOf { it.quantity }} item")
    appendLine("Subtotal: ${rupiah(receipt.subtotal)}")
    if (receipt.taxPercent > 0.0 || receipt.taxAmount > 0) {
        appendLine("Pajak ${percent(receipt.taxPercent)}%: ${rupiah(receipt.taxAmount)}")
    }
    if (receipt.adminPercent > 0.0 || receipt.adminAmount > 0) {
        appendLine("Biaya admin ${percent(receipt.adminPercent)}%: ${rupiah(receipt.adminAmount)}")
    }
    appendLine("TOTAL: ${rupiah(receipt.total)}")
    if (receipt.isKasbon) {
        appendLine("*** BELUM LUNAS ***")
    } else if (receipt.paidCash > 0) {
        appendLine("Tunai: ${rupiah(receipt.paidCash)}")
        appendLine("Kembalian: ${rupiah(receipt.changeAmount)}")
    }
    appendLine("------------------------------")
    if (settings.footer.isNotBlank()) appendLine(settings.footer)
}
