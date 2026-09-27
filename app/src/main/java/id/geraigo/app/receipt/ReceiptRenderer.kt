package id.geraigo.app.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.LruCache
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Single receipt layout used by the on-screen preview, image sharing, and ESC/POS printing. */
object ReceiptRenderer {
    private val imageCache = object : LruCache<String, Bitmap>(4) {}
    private data class Row(
        val left: String = "",
        val right: String = "",
        val centered: String = "",
        val bold: Boolean = false,
        val large: Boolean = false,
        val rule: Boolean = false,
        val spaceBefore: Int = 0,
        val image: Bitmap? = null,
    )

    fun render(context: Context, receipt: OrderReceipt, settings: StoreSettings): Bitmap {
        val width = if (settings.paperWidth == 80) 576 else 384
        val pad = if (width == 576) 24f else 16f
        val textSize = 20f
        val lineHeight = 27f
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            this.textSize = textSize
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val bold = Paint(normal).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        val large = Paint(bold).apply { this.textSize = 24f }
        val totalPaint = Paint(bold).apply { this.textSize = 26f }
        val contentWidth = width - 2 * pad
        val rows = mutableListOf<Row>()
        fun center(text: String, isBold: Boolean = false, before: Int = 0, isLarge: Boolean = false) { rows += Row(centered = text, bold = isBold, large = isLarge, spaceBefore = before) }
        fun left(text: String, isBold: Boolean = false) {
            val p = if (isBold) bold else normal
            wrap(text, p, contentWidth).forEach { rows += Row(left = it, bold = isBold) }
        }
        fun paired(label: String, value: String, isBold: Boolean = false) {
            val p = if (isBold) bold else normal
            val maxChars = (contentWidth / p.measureText("M")).toInt().coerceAtLeast(8)
            val rightChars = (p.measureText(value) / p.measureText("M")).toInt().coerceAtMost(maxChars / 2)
            val leftChars = (maxChars - rightChars - 1).coerceAtLeast(1)
            val pieces = wrap(label, p, leftChars * p.measureText("M"))
            pieces.forEachIndexed { index, part -> rows += Row(left = part, right = if (index == pieces.lastIndex) value else "", bold = isBold) }
        }
        if (settings.showLogo && settings.logoUri.isNotBlank()) loadImage(context, settings.logoUri, (width * settings.imageWidth / 384).coerceIn(96, width), 512)?.let { rows += Row(image = it, spaceBefore = 4) }
        center(settings.name.ifBlank { "Gerai Go" }, true, isLarge = true)
        if (settings.address.isNotBlank()) wrap(settings.address, normal, contentWidth).forEach { center(it) }
        if (settings.contact.isNotBlank()) wrap(settings.contact, normal, contentWidth).forEach { center(it) }
        if (settings.header.isNotBlank()) wrap(settings.header, normal, contentWidth).forEach { center(it) }
        rows += Row(rule = true, spaceBefore = 5)
        center("NOTA PENJUALAN", true, before = 4)
        paired("No. nota", "GG-${receipt.key.takeLast(6).uppercase()}")
        paired("Waktu", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date(receipt.createdAt)))
        rows += Row(rule = true, spaceBefore = 5)
        center("RINCIAN BARANG", true, before = 2)
        receipt.items.forEachIndexed { index, item ->
            left("${index + 1}. ${item.name.ifBlank { "Barang" }}", true)
            paired("${item.quantity} × ${money(item.price)}", "= ${money(item.total)}")
        }
        if (receipt.note.isNotBlank()) left("Catatan: ${receipt.note}")
        rows += Row(rule = true, spaceBefore = 5)
        center("RINGKASAN PEMBAYARAN", true, before = 2)
        paired("Jenis barang", "${receipt.items.size}")
        paired("Total kuantitas", receipt.items.sumOf { it.quantity }.toString())
        paired("Subtotal", money(receipt.subtotal))
        if (receipt.taxPercent > 0.0 || receipt.taxAmount > 0) paired("Pajak ${percent(receipt.taxPercent)}%", money(receipt.taxAmount))
        if (receipt.adminPercent > 0.0 || receipt.adminAmount > 0) paired("Biaya admin ${percent(receipt.adminPercent)}%", money(receipt.adminAmount))
        rows += Row(rule = true, spaceBefore = 4)
        paired("TOTAL BAYAR", money(receipt.total), true)
        rows += Row(rule = true, spaceBefore = 5)
        if (settings.showExtraImage && settings.extraImageUri.isNotBlank()) loadImage(context, settings.extraImageUri, (width * settings.imageWidth / 384).coerceIn(96, width), 320)?.let { rows += Row(image = it, spaceBefore = 5) }
        if (settings.footer.isNotBlank()) wrap(settings.footer, normal, contentWidth).forEach { center(it, before = if (it == settings.footer) 5 else 0) }

        fun rowHeight(row: Row) = if (row.large || (row.bold && row.right.isNotBlank())) 34 else lineHeight.toInt()
        val height = (pad.toDouble() + rows.sumOf { (it.spaceBefore + (it.image?.height ?: rowHeight(it))).toDouble() } + pad).toInt().coerceAtLeast(180)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = pad
        rows.forEach { row ->
            y += row.spaceBefore
            when {
                row.rule -> {
                    val dotPaint = Paint(normal).apply { color = 0xFF777777.toInt() }
                    var x = pad
                    while (x <= width - pad) { canvas.drawCircle(x, y + lineHeight / 2, 1.35f, dotPaint); x += 8f }
                    y += rowHeight(row)
                }
                row.image != null -> { canvas.drawBitmap(row.image, (width - row.image.width) / 2f, y, null); y += row.image.height }
                else -> {
                    val paint = when {
                        row.centered == "TOTAL BAYAR" || row.right == money(receipt.total) && row.bold -> totalPaint
                        row.large -> large
                        row.bold -> bold
                        else -> normal
                    }
                    if (row.centered.isNotEmpty()) canvas.drawText(row.centered, (width - paint.measureText(row.centered)) / 2f, y + textSize, paint)
                    else {
                        if (row.left.isNotEmpty()) canvas.drawText(row.left, pad, y + textSize, paint)
                        if (row.right.isNotEmpty()) canvas.drawText(row.right, width - pad - paint.measureText(row.right), y + textSize, paint)
                    }
                    y += rowHeight(row)
                }
            }
        }
        return bitmap
    }

    private fun loadImage(context: Context, source: String, maxWidth: Int, maxHeight: Int): Bitmap? {
        val cacheKey = "$source|$maxWidth|$maxHeight"
        imageCache.get(cacheKey)?.let { return it }
        val rendered = runCatching {
        val original = context.contentResolver.openInputStream(Uri.parse(source))?.use(BitmapFactory::decodeStream) ?: return null
        val scale = minOf(maxWidth.toFloat() / original.width, maxHeight.toFloat() / original.height)
        val width = (original.width * scale).toInt().coerceAtLeast(1)
        val height = (original.height * scale).toInt().coerceAtLeast(1)
        val result = Bitmap.createScaledBitmap(original, width, height, true)
        if (result !== original) original.recycle()
        result
        }.getOrNull()
        if (rendered != null) imageCache.put(cacheKey, rendered)
        return rendered
    }

    private fun wrap(value: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        value.replace('\n', ' ').split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            val current = result.lastOrNull().orEmpty()
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                if (result.isEmpty()) result += candidate else result[result.lastIndex] = candidate
            } else {
                var remaining = word
                while (paint.measureText(remaining) > maxWidth) {
                    var count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                    result += remaining.take(count)
                    remaining = remaining.drop(count)
                }
                if (remaining.isNotEmpty()) result += remaining
            }
        }
        return result.ifEmpty { listOf("") }
    }

    private fun money(value: Long) = "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)
    private fun percent(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
