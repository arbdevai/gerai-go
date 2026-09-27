package id.geraigo.app.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object ThermalPrinter {
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    fun pairedDevices(context: Context) = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices?.sortedBy { it.name }.orEmpty()

    suspend fun print(context: Context, address: String, receipt: OrderReceipt, settings: StoreSettings) = withContext(Dispatchers.IO) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: error("Bluetooth tidak tersedia")
        val device = adapter.getRemoteDevice(address)
        val socket = device.createRfcommSocketToServiceRecord(spp)
        adapter.cancelDiscovery()
        socket.connect()
        try {
            val out = socket.outputStream
            out.write(byteArrayOf(0x1b, 0x40))
            out.write(byteArrayOf(0x1b, 0x61, 1))
            if (settings.showLogo && settings.logoUri.isNotBlank()) printImage(context, out, Uri.parse(settings.logoUri), settings.imageWidth, settings.paperWidth)
            line(out, settings.name.ifBlank { "Gerai Go" })
            if (settings.address.isNotBlank()) line(out, settings.address)
            if (settings.contact.isNotBlank()) line(out, settings.contact)
            if (settings.header.isNotBlank()) line(out, settings.header)
            out.write("\n".toByteArray())
            out.write(byteArrayOf(0x1b, 0x61, 0))
            val columns = if (settings.paperWidth == 80) 48 else 32
            line(out, "-".repeat(columns))
            receipt.items.forEach { item ->
                line(out, item.name)
                line(out, "${item.quantity} x ${money(item.price)}", money(item.total), columns)
            }
            if (receipt.note.isNotBlank()) line(out, "Catatan: ${receipt.note}")
            line(out, "-".repeat(columns))
            line(out, "Subtotal", money(receipt.subtotal), columns)
            if (receipt.taxAmount > 0) line(out, "Pajak ${receipt.taxPercent}%", money(receipt.taxAmount), columns)
            if (receipt.adminAmount > 0) line(out, "Biaya admin ${receipt.adminPercent}%", money(receipt.adminAmount), columns)
            line(out, "TOTAL", money(receipt.total), columns)
            line(out, "Waktu", java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale("id", "ID")).format(java.util.Date(receipt.createdAt)), columns)
            if (settings.showExtraImage && settings.extraImageUri.isNotBlank()) {
                out.write(byteArrayOf(0x1b, 0x61, 1))
                printImage(context, out, Uri.parse(settings.extraImageUri), settings.imageWidth, settings.paperWidth)
            }
            out.write(byteArrayOf(0x1b, 0x61, 1))
            if (settings.footer.isNotBlank()) line(out, settings.footer)
            out.write("\n\n\n".toByteArray())
            out.write(byteArrayOf(0x1d, 0x56, 0x00))
            out.flush()
        } finally { socket.close() }
    }

    private fun line(out: java.io.OutputStream, text: String) { out.write((ascii(text) + "\n").toByteArray(Charsets.US_ASCII)) }
    private fun line(out: java.io.OutputStream, left: String, right: String, columns: Int) {
        val l = ascii(left).take(columns); val r = ascii(right).take(columns)
        out.write((l + " ".repeat((columns - l.length - r.length).coerceAtLeast(1)) + r + "\n").toByteArray(Charsets.US_ASCII))
    }
    private fun money(value: Long) = "Rp " + java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID")).format(value)
    private fun ascii(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "").replace("[^\\x20-\\x7E]".toRegex(), "?")

    private fun printImage(context: Context, out: java.io.OutputStream, uri: Uri, requestedWidth: Int, paper: Int) {
        val original = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return
        val maxWidth = ((if (paper == 80) 576 else 384) * requestedWidth / 384).coerceIn(96, if (paper == 80) 576 else 384)
        val scale = maxWidth.toFloat() / original.width
        val width = maxWidth - (maxWidth % 8)
        val height = (original.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createScaledBitmap(original, width, height, true)
        original.recycle()
        // ESC/POS raster bit image: GS v 0, 1-bit monochrome pixels.
        var y = 0
        while (y < bitmap.height) {
            val rows = (bitmap.height - y).coerceAtMost(128)
            val bytesPerRow = bitmap.width / 8
            out.write(byteArrayOf(0x1d, 0x76, 0x30, 0x00, bytesPerRow.toByte(), (bytesPerRow shr 8).toByte(), rows.toByte(), (rows shr 8).toByte()))
            for (row in y until y + rows) for (byteX in 0 until bytesPerRow) {
                var bits = 0
                for (bit in 0..7) {
                    val pixel = bitmap.getPixel(byteX * 8 + bit, row)
                    val gray = (android.graphics.Color.red(pixel) * 299 + android.graphics.Color.green(pixel) * 587 + android.graphics.Color.blue(pixel) * 114) / 1000
                    if (gray < 160) bits = bits or (1 shl (7 - bit))
                }
                out.write(bits)
            }
            y += rows
        }
        bitmap.recycle()
        out.write("\n".toByteArray())
    }
}
