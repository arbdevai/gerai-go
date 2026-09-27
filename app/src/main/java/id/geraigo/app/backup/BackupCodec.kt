package id.geraigo.app.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import id.geraigo.app.data.Sale
import id.geraigo.app.data.StoreSettings
import org.json.JSONArray
import org.json.JSONObject

object BackupCodec {
    fun encode(context: Context, sales: List<Sale>, settings: StoreSettings): String {
        val transactions = JSONArray()
        sales.forEach { sale ->
            transactions.put(JSONObject()
                .put("name", sale.name).put("price", sale.price).put("quantity", sale.quantity)
                .put("note", sale.note).put("createdAt", sale.createdAt).put("favorite", sale.favorite)
                .put("receiptKey", sale.receiptKey).put("taxAmount", sale.taxAmount)
                .put("adminAmount", sale.adminAmount).put("taxPercent", sale.taxPercent)
                .put("adminPercent", sale.adminPercent))
        }
        fun encodeImage(uri: String) = if (uri.isBlank()) "" else runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }.orEmpty()
        }.getOrDefault("")
        val prefs = JSONObject()
            .put("name", settings.name).put("address", settings.address).put("contact", settings.contact)
            .put("header", settings.header).put("footer", settings.footer).put("logoUri", settings.logoUri)
            .put("extraImageUri", settings.extraImageUri).put("logoImage", encodeImage(settings.logoUri))
            .put("extraImage", encodeImage(settings.extraImageUri)).put("showLogo", settings.showLogo)
            .put("showExtraImage", settings.showExtraImage).put("imageWidth", settings.imageWidth)
            .put("paperWidth", settings.paperWidth).put("taxEnabled", settings.taxEnabled)
            .put("taxPercent", settings.taxPercent).put("adminFeeEnabled", settings.adminFeeEnabled)
            .put("adminFeePercent", settings.adminFeePercent).put("printerAddress", settings.printerAddress)
        return JSONObject().put("format", "gerai-go-backup").put("version", 3)
            .put("createdAt", System.currentTimeMillis()).put("transactions", transactions)
            .put("settings", prefs).toString(2)
    }

    fun decode(context: Context, raw: String): Pair<List<Sale>, StoreSettings?> {
        val json = JSONObject(raw)
        require(json.optString("format") == "gerai-go-backup") { "Format backup tidak dikenali." }
        val transactions = json.getJSONArray("transactions")
        val restored = ArrayList<Sale>(transactions.length())
        for (index in 0 until transactions.length()) {
            val item = transactions.getJSONObject(index)
            restored += Sale(
                id = 0, name = item.getString("name"), price = item.getLong("price"),
                quantity = item.getInt("quantity"), note = item.optString("note"),
                createdAt = item.getLong("createdAt"), favorite = item.optBoolean("favorite"),
                receiptKey = item.optString("receiptKey", "legacy-${item.optLong("createdAt")}"),
                taxAmount = item.optLong("taxAmount"), adminAmount = item.optLong("adminAmount"),
                taxPercent = item.optDouble("taxPercent"), adminPercent = item.optDouble("adminPercent")
            )
        }
        val prefJson = json.optJSONObject("settings") ?: return restored to null
        fun restoreImage(key: String, filename: String, legacyUri: String): String {
            val encoded = prefJson.optString(key)
            if (encoded.isBlank()) return legacyUri
            val file = java.io.File(context.filesDir, filename)
            file.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
            return Uri.fromFile(file).toString()
        }
        val settings = StoreSettings(
            name = prefJson.optString("name", "Gerai Go"), address = prefJson.optString("address"),
            contact = prefJson.optString("contact"), header = prefJson.optString("header"),
            footer = prefJson.optString("footer", "Terima kasih telah berbelanja"),
            logoUri = restoreImage("logoImage", "backup-logo", prefJson.optString("logoUri")),
            extraImageUri = restoreImage("extraImage", "backup-extra-image", prefJson.optString("extraImageUri")),
            showLogo = prefJson.optBoolean("showLogo", true), showExtraImage = prefJson.optBoolean("showExtraImage"),
            imageWidth = prefJson.optInt("imageWidth", 160), paperWidth = prefJson.optInt("paperWidth", 58),
            taxEnabled = prefJson.optBoolean("taxEnabled"), taxPercent = prefJson.optDouble("taxPercent"),
            adminFeeEnabled = prefJson.optBoolean("adminFeeEnabled"), adminFeePercent = prefJson.optDouble("adminFeePercent"),
            printerAddress = prefJson.optString("printerAddress")
        )
        return restored to settings
    }
}
