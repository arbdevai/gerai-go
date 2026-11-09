package id.geraigo.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.NumberFormat
import java.util.Locale

class RupiahInputTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.filter(Char::isDigit)
        if (raw.isEmpty()) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }

        val num = raw.toLongOrNull()
        val formatted = if (num != null) {
            "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(num)
        } else {
            "Rp $raw"
        }

        // Build exact mapping arrays
        val origToTrans = IntArray(raw.length + 1)
        val transToOrig = IntArray(formatted.length + 1)

        val prefixLength = 3 // "Rp "
        var digitIndex = 0

        for (i in 0 until prefixLength.coerceAtMost(formatted.length)) {
            transToOrig[i] = 0
        }

        for (i in prefixLength until formatted.length) {
            if (formatted[i].isDigit()) {
                digitIndex++
                if (digitIndex <= raw.length) {
                    origToTrans[digitIndex] = i + 1
                }
            }
            transToOrig[i + 1] = digitIndex.coerceAtMost(raw.length)
        }
        origToTrans[0] = prefixLength.coerceAtMost(formatted.length)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                return origToTrans[clamped].coerceIn(0, formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, formatted.length)
                return transToOrig[clamped].coerceIn(0, raw.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
