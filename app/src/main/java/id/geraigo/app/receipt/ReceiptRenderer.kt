package id.geraigo.app.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.LruCache
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import java.io.File
import java.io.InputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Single receipt layout used by the on-screen preview, image sharing, and ESC/POS printing. */
object ReceiptRenderer {
    private val imageCache = object : LruCache<String, Bitmap>(6) {}

    private data class Row(
        val left: String = "",
        val right: String = "",
        val centered: String = "",
        val bold: Boolean = false,
        val large: Boolean = false,
        val total: Boolean = false,
        val rule: Boolean = false,
        val spaceBefore: Int = 0,
        val image: Bitmap? = null
    )

    fun render(context: Context, receipt: OrderReceipt, settings: StoreSettings): Bitmap {
        val width = if (settings.paperWidth == 80) 576 else 384
        val pad = if (width == 576) 32f else 24f
        val textSize = 19f
        val lineHeight = 28f
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val bold = Paint(normal).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        val large = Paint(bold).apply { this.textSize = 25f }
        val totalPaint = Paint(bold).apply { this.textSize = 28f }
        val contentWidth = width - 2 * pad
        val rows = mutableListOf<Row>()

        fun center(text: String, isBold: Boolean = false, before: Int = 0, isLarge: Boolean = false) {
            val p = if (isLarge) large else if (isBold) bold else normal
            val lines = wrapMultiline(text, p, contentWidth)
            lines.forEachIndexed { index, line ->
                rows += Row(centered = line, bold = isBold, large = isLarge, spaceBefore = if (index == 0) before else 0)
            }
        }

        fun left(text: String, isBold: Boolean = false, before: Int = 0) {
            val p = if (isBold) bold else normal
            val lines = wrapMultiline(text, p, contentWidth)
            lines.forEachIndexed { index, line ->
                rows += Row(left = line, bold = isBold, spaceBefore = if (index == 0) before else 0)
            }
        }

        fun paired(label: String, value: String, isBold: Boolean = false, before: Int = 0) {
            val p = if (isBold) bold else normal
            val rightWidth = p.measureText(value)
            val leftAvailableWidth = (contentWidth - rightWidth - 14f).coerceAtLeast(30f)
            val pieces = wrapMultiline(label, p, leftAvailableWidth)
            pieces.forEachIndexed { index, part ->
                rows += Row(
                    left = part,
                    right = if (index == pieces.lastIndex) value else "",
                    bold = isBold,
                    spaceBefore = if (index == 0) before else 0
                )
            }
        }

        // Header logo
        if (settings.showLogo && settings.logoUri.isNotBlank()) {
            val maxImageW = (contentWidth * settings.imageWidth / 384).toInt().coerceIn(96, contentWidth.toInt())
            loadImage(context, settings.logoUri, maxImageW, 400)?.let {
                rows += Row(image = it, spaceBefore = 6)
            }
        }

        center(settings.name.ifBlank { "Gerai Go" }, isBold = true, isLarge = true)
        if (settings.address.isNotBlank()) {
            center(settings.address)
        }
        if (settings.contact.isNotBlank()) {
            center(settings.contact)
        }
        if (settings.header.isNotBlank()) {
            center(settings.header, before = 2)
        }

        rows += Row(rule = true, spaceBefore = 8)
        paired("No. nota", "GG-${receipt.key.takeLast(6).uppercase()}")
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
        val receiptDate = Date(receipt.createdAt)
        paired("Tanggal", dateFormat.format(receiptDate))
        paired("Waktu", timeFormat.format(receiptDate))
        rows += Row(rule = true, spaceBefore = 8)

        center("Barang (${receipt.items.size})", isBold = true, before = 2)
        receipt.items.forEachIndexed { index, item ->
            left("${index + 1}. ${item.name.ifBlank { "Barang" }}", isBold = true, before = if (index == 0) 2 else 6)
            paired("${item.quantity} × ${money(item.price)}", money(item.total))
        }

        if (receipt.note.isNotBlank()) {
            left("Catatan: ${receipt.note}", before = 4)
        }

        rows += Row(rule = true, spaceBefore = 8)
        left("Ringkasan", isBold = true, before = 2)
        paired("Total kuantitas", "${receipt.items.sumOf { it.quantity }} pcs")
        paired("Subtotal", money(receipt.subtotal))
        if (receipt.taxPercent > 0.0 || receipt.taxAmount > 0) {
            paired("Pajak ${percent(receipt.taxPercent)}%", money(receipt.taxAmount))
        }
        if (receipt.adminPercent > 0.0 || receipt.adminAmount > 0) {
            paired("Biaya admin ${percent(receipt.adminPercent)}%", money(receipt.adminAmount))
        }

        rows += Row(rule = true, spaceBefore = 6)
        rows += Row(left = "TOTAL BAYAR", right = money(receipt.total), bold = true, total = true, spaceBefore = 2)
        rows += Row(rule = true, spaceBefore = 6)

        // Extra promotional / QR image
        if (settings.showExtraImage && settings.extraImageUri.isNotBlank()) {
            val maxImageW = (contentWidth * settings.imageWidth / 384).toInt().coerceIn(96, contentWidth.toInt())
            loadImage(context, settings.extraImageUri, maxImageW, 320)?.let {
                rows += Row(image = it, spaceBefore = 8)
            }
        }

        if (settings.footer.isNotBlank()) {
            center(settings.footer, before = 6)
        }

        fun rowHeight(row: Row): Int = when {
            row.large || row.total -> 36
            row.rule -> 16
            else -> lineHeight.toInt()
        }

        val totalHeight = (pad * 2 + rows.sumOf { it.spaceBefore + (it.image?.height ?: rowHeight(it)) }).toInt().coerceAtLeast(180)
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = pad
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        rows.forEach { row ->
            y += row.spaceBefore
            when {
                row.rule -> {
                    var x = pad
                    val dotY = y + 8f
                    while (x <= width - pad) {
                        canvas.drawCircle(x, dotY, 1.4f, dotPaint)
                        x += 8f
                    }
                    y += rowHeight(row)
                }
                row.image != null -> {
                    canvas.drawBitmap(row.image, (width - row.image.width) / 2f, y, null)
                    y += row.image.height
                }
                else -> {
                    val paint = when {
                        row.total -> totalPaint
                        row.large -> large
                        row.bold -> bold
                        else -> normal
                    }
                    val rh = rowHeight(row)
                    val baseline = y + rh / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
                    if (row.centered.isNotEmpty()) {
                        val textWidth = paint.measureText(row.centered)
                        canvas.drawText(row.centered, (width - textWidth) / 2f, baseline, paint)
                    } else {
                        if (row.left.isNotEmpty()) {
                            canvas.drawText(row.left, pad, baseline, paint)
                        }
                        if (row.right.isNotEmpty()) {
                            val rightWidth = paint.measureText(row.right)
                            canvas.drawText(row.right, width - pad - rightWidth, baseline, paint)
                        }
                    }
                    y += rh
                }
            }
        }

        return bitmap
    }

    private fun openStream(context: Context, source: String): InputStream? {
        val uri = Uri.parse(source)
        return if (uri.scheme == "file") {
            val path = uri.path ?: return null
            File(path).takeIf { it.exists() }?.inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun loadImage(context: Context, source: String, maxWidth: Int, maxHeight: Int): Bitmap? {
        val cacheKey = "$source|$maxWidth|$maxHeight"
        imageCache.get(cacheKey)?.let { return it }

        val rendered = runCatching {
            // First decode bounds with inJustDecodeBounds = true to prevent OOM
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, source)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW <= 0 || origH <= 0) return@runCatching null

            var inSampleSize = 1
            while ((origW / (inSampleSize * 2)) >= maxWidth && (origH / (inSampleSize * 2)) >= maxHeight) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val downsampled = openStream(context, source)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return@runCatching null

            val scale = minOf(maxWidth.toFloat() / downsampled.width, maxHeight.toFloat() / downsampled.height)
            val finalW = (downsampled.width * scale).toInt().coerceAtLeast(1)
            val finalH = (downsampled.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(downsampled, finalW, finalH, true)
            if (scaled !== downsampled) {
                downsampled.recycle()
            }
            scaled
        }.getOrNull()

        if (rendered != null) {
            imageCache.put(cacheKey, rendered)
        }
        return rendered
    }

    private fun wrapMultiline(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.split("\n")
        paragraphs.forEach { paragraph ->
            if (paragraph.isBlank()) {
                result += ""
            } else {
                result.addAll(wrapSingleLine(paragraph, paint, maxWidth))
            }
        }
        return result.ifEmpty { listOf("") }
    }

    private fun wrapSingleLine(value: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val words = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
        words.forEach { word ->
            val current = result.lastOrNull().orEmpty()
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                if (result.isEmpty()) result += candidate else result[result.lastIndex] = candidate
            } else {
                var remaining = word
                while (paint.measureText(remaining) > maxWidth) {
                    val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
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
