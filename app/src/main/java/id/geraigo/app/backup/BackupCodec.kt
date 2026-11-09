package id.geraigo.app.backup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import id.geraigo.app.data.Sale
import id.geraigo.app.data.StoreSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object BackupCodec {
    private fun openStream(context: Context, uriString: String): InputStream? {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "file") {
            val path = uri.path ?: return null
            File(path).takeIf { it.exists() }?.inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    fun encode(context: Context, sales: List<Sale>, settings: StoreSettings): String {
        val transactions = JSONArray()
        sales.forEach { sale ->
            transactions.put(
                JSONObject()
                    .put("name", sale.name)
                    .put("price", sale.price)
                    .put("quantity", sale.quantity)
                    .put("note", sale.note)
                    .put("createdAt", sale.createdAt)
                    .put("favorite", sale.favorite)
                    .put("receiptKey", sale.receiptKey)
                    .put("taxAmount", sale.taxAmount)
                    .put("adminAmount", sale.adminAmount)
                    .put("taxPercent", sale.taxPercent)
                    .put("adminPercent", sale.adminPercent)
            )
        }

        fun encodeImage(uri: String): String = if (uri.isBlank()) "" else runCatching {
            openStream(context, uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                // Quick bounds check
                val bytes = input.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) return@use ""

                // Limit maximum backup image dimension to 640px to prevent multi-megabyte JSON
                var sample = 1
                while ((options.outWidth / (sample * 2)) >= 640 && (options.outHeight / (sample * 2)) >= 640) {
                    sample *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return@use ""
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                bmp.recycle()
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }.orEmpty()
        }.getOrDefault("")

        val prefs = JSONObject()
            .put("name", settings.name)
            .put("address", settings.address)
            .put("contact", settings.contact)
            .put("header", settings.header)
            .put("footer", settings.footer)
            .put("logoUri", settings.logoUri)
            .put("extraImageUri", settings.extraImageUri)
            .put("logoImage", encodeImage(settings.logoUri))
            .put("extraImage", encodeImage(settings.extraImageUri))
            .put("showLogo", settings.showLogo)
            .put("showExtraImage", settings.showExtraImage)
            .put("imageWidth", settings.imageWidth)
            .put("paperWidth", settings.paperWidth)
            .put("taxEnabled", settings.taxEnabled)
            .put("taxPercent", settings.taxPercent)
            .put("adminFeeEnabled", settings.adminFeeEnabled)
            .put("adminFeePercent", settings.adminFeePercent)
            .put("printerAddress", settings.printerAddress)

        return JSONObject()
            .put("format", "gerai-go-backup")
            .put("version", 3)
            .put("createdAt", System.currentTimeMillis())
            .put("transactions", transactions)
            .put("settings", prefs)
            .toString(2)
    }

    fun decode(context: Context, raw: String): Pair<List<Sale>, StoreSettings?> {
        val json = JSONObject(raw)
        require(json.optString("format") == "gerai-go-backup") { "Format backup tidak dikenali." }
        val transactions = json.getJSONArray("transactions")
        val restored = ArrayList<Sale>(transactions.length())
        for (index in 0 until transactions.length()) {
            val item = transactions.getJSONObject(index)
            val fallbackKey = "legacy-${item.optLong("createdAt", System.currentTimeMillis())}"
            val rKey = item.optString("receiptKey").ifBlank { fallbackKey }

            restored += Sale(
                id = 0,
                name = item.getString("name"),
                price = item.getLong("price"),
                quantity = item.getInt("quantity"),
                note = item.optString("note"),
                createdAt = item.getLong("createdAt"),
                favorite = item.optBoolean("favorite"),
                receiptKey = rKey,
                taxAmount = item.optLong("taxAmount"),
                adminAmount = item.optLong("adminAmount"),
                taxPercent = item.optDouble("taxPercent"),
                adminPercent = item.optDouble("adminPercent")
            )
        }

        val prefJson = json.optJSONObject("settings") ?: return restored to null

        fun restoreImage(key: String, filename: String, legacyUri: String): String {
            val encoded = prefJson.optString(key)
            if (encoded.isBlank()) return legacyUri
            return runCatching {
                val file = File(context.filesDir, filename)
                file.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                Uri.fromFile(file).toString()
            }.getOrDefault(legacyUri)
        }

        val settings = StoreSettings(
            name = prefJson.optString("name", "Gerai Go"),
            address = prefJson.optString("address"),
            contact = prefJson.optString("contact"),
            header = prefJson.optString("header"),
            footer = prefJson.optString("footer", "Terima kasih telah berbelanja"),
            logoUri = restoreImage("logoImage", "backup-logo.png", prefJson.optString("logoUri")),
            extraImageUri = restoreImage("extraImage", "backup-extra-image.png", prefJson.optString("extraImageUri")),
            showLogo = prefJson.optBoolean("showLogo", true),
            showExtraImage = prefJson.optBoolean("showExtraImage"),
            imageWidth = prefJson.optInt("imageWidth", 160),
            paperWidth = prefJson.optInt("paperWidth", 58),
            taxEnabled = prefJson.optBoolean("taxEnabled"),
            taxPercent = prefJson.optDouble("taxPercent"),
            adminFeeEnabled = prefJson.optBoolean("adminFeeEnabled"),
            adminFeePercent = prefJson.optDouble("adminFeePercent"),
            printerAddress = prefJson.optString("printerAddress")
        )
        return restored to settings
    }
}
