package id.geraigo.app.printer

import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import id.geraigo.app.receipt.ReceiptRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThermalPrinter {
    fun pairedDevices(context: Context) = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices?.sortedBy { it.name }.orEmpty()

    suspend fun print(context: Context, address: String, receipt: OrderReceipt, settings: StoreSettings) = withContext(Dispatchers.IO) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: error("Bluetooth tidak tersedia")
        val connection = BluetoothConnection(adapter.getRemoteDevice(address))
        var printer: EscPosPrinter? = null
        var bitmap: Bitmap? = null
        try {
            val wide = settings.paperWidth == 80
            val activePrinter = EscPosPrinter(connection, 203, if (wide) 72f else 48f, if (wide) 48 else 32)
            printer = activePrinter
            bitmap = ReceiptRenderer.render(context, receipt, settings)
            val image = PrinterTextParserImg.bitmapToHexadecimalString(activePrinter, bitmap, false)
            activePrinter.printFormattedTextAndCut("[C]<img>$image</img>\n", 8f)
        } finally {
            bitmap?.recycle()
            if (printer != null) printer.disconnectPrinter() else connection.disconnect()
        }
    }
}
