package id.geraigo.app.printer

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import id.geraigo.app.data.OrderReceipt
import id.geraigo.app.data.StoreSettings
import id.geraigo.app.receipt.ReceiptRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PrinterDeviceInfo(
    val name: String,
    val address: String
)

object ThermalPrinter {
    private val printMutex = Mutex()

    fun hasBluetoothPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val connect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        return connect
    }

    fun pairedDevices(context: Context): List<PrinterDeviceInfo> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()

        return runCatching {
            adapter.bondedDevices.orEmpty()
                .map { device ->
                    val deviceName = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Printer Bluetooth" }
                    PrinterDeviceInfo(name = deviceName, address = device.address)
                }
                .sortedBy { it.name }
        }.getOrDefault(emptyList())
    }

    suspend fun print(context: Context, address: String, receipt: OrderReceipt, settings: StoreSettings) = withContext(Dispatchers.IO) {
        printMutex.withLock {
            if (!hasBluetoothPermission(context)) {
                error("Izin Bluetooth Connect belum diberikan.")
            }
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: error("Bluetooth tidak tersedia pada perangkat")
            val adapter = manager.adapter ?: error("Bluetooth adapter tidak aktif")
            val remoteDevice = adapter.getRemoteDevice(address) ?: error("Perangkat printer tidak ditemukan")
            val connection = BluetoothConnection(remoteDevice)
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
                if (printer != null) {
                    runCatching { printer.disconnectPrinter() }
                } else {
                    runCatching { connection.disconnect() }
                }
            }
        }
    }
}
