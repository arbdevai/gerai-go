package id.geraigo.app.printer

import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ThermalPrinter {
    fun pairedDevices(context: Context) = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices?.sortedBy { it.name }.orEmpty()

    suspend fun print(context: Context, address: String, receipt: OrderReceipt, settings: StoreSettings) = withContext(Dispatchers.IO) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: error("Bluetooth tidak tersedia")
        val device = adapter.getRemoteDevice(address)
        val connection = BluetoothConnection(device)
        var printer: EscPosPrinter? = null
        try {
            val wide = settings.paperWidth == 80
            val activePrinter = EscPosPrinter(connection, 203, if (wide) 72f else 48f, if (wide) 48 else 32)
            printer = activePrinter
            val ticket = buildString {
                if (settings.showLogo && settings.logoUri.isNotBlank()) imageMarkup(activePrinter, context, settings.logoUri, settings.imageWidth)?.let { append("[C]<img>").append(it).append("</img>\n") }
                append("[C]<b>").append(escape(settings.name.ifBlank { "Gerai Go" }.uppercase())).append("</b>\n")
                if (settings.address.isNotBlank()) append("[C]").append(escape(settings.address)).append('\n')
                if (settings.contact.isNotBlank()) append("[C]").append(escape(settings.contact)).append('\n')
                if (settings.header.isNotBlank()) append("[C]").append(escape(settings.header)).append('\n')
                append("[C]--------------------------------\n")
                append("[L]NOTA GG-").append(receipt.key.takeLast(6).uppercase()).append('\n')
                append("[L]").append(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date(receipt.createdAt))).append('\n')
                append("[C]--------------------------------\n")
                receipt.items.forEach { item ->
                    append("[L]<b>").append(escape(item.name)).append("</b>\n")
                    append("[L]").append(item.quantity).append(" x ").append(money(item.price)).append("[R]").append(money(item.total)).append('\n')
                }
                if (receipt.note.isNotBlank()) append("[L]Catatan: ").append(escape(receipt.note)).append('\n')
                append("[C]--------------------------------\n")
                append("[L]Subtotal[R]").append(money(receipt.subtotal)).append('\n')
                if (receipt.taxAmount > 0) append("[L]Pajak ").append(receipt.taxPercent).append("%[R]").append(money(receipt.taxAmount)).append('\n')
                if (receipt.adminAmount > 0) append("[L]Biaya admin ").append(receipt.adminPercent).append("%[R]").append(money(receipt.adminAmount)).append('\n')
                append("[L]<b>TOTAL</b>[R]<b>").append(money(receipt.total)).append("</b>\n")
                if (settings.showExtraImage && settings.extraImageUri.isNotBlank()) imageMarkup(activePrinter, context, settings.extraImageUri, settings.imageWidth)?.let { append("[C]<img>").append(it).append("</img>\n") }
                if (settings.footer.isNotBlank()) append("[C]").append(escape(settings.footer)).append('\n')
            }
            activePrinter.printFormattedTextAndCut(ticket, 8f)
        } finally {
            if (printer != null) printer.disconnectPrinter() else connection.disconnect()
        }
    }

    private fun imageMarkup(printer: EscPosPrinter?, context: Context, source: String, requestedWidth: Int): String? {
        printer ?: return null
        val bitmap = context.contentResolver.openInputStream(Uri.parse(source))?.use(BitmapFactory::decodeStream) ?: return null
        try {
            val targetWidth = (printer.printerWidthPx * requestedWidth / 384).coerceIn(96, printer.printerWidthPx).let { it - it % 8 }
            val ratio = targetWidth.toFloat() / bitmap.width
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
            return try { PrinterTextParserImg.bitmapToHexadecimalString(printer, scaled, false) } finally { if (scaled !== bitmap) scaled.recycle() }
        } finally { bitmap.recycle() }
    }

    private fun money(value: Long) = "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)
    private fun escape(value: String) = value.replace("[", "(").replace("]", ")").replace("<", "(").replace(">", ")").replace("\n", " ").replace("\r", " ")
}
