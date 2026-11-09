package id.geraigo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.geraigo.app.printer.ThermalPrinter

@Composable
fun PrinterDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val devices = remember {
        ThermalPrinter.pairedDevices(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Printer Bluetooth", fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "Pastikan printer ESC/POS (58/80 mm) sudah dipasangkan di Pengaturan Bluetooth Android.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))

                if (devices.isEmpty()) {
                    Text(
                        "Belum ada perangkat printer tersanding yang ditemukan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(devices, key = { it.address }) { dev ->
                            TextButton(
                                onClick = { onSelect(dev.address) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(dev.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
