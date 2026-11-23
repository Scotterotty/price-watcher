package com.pricewatch.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Keeps a copy of each store page the app couldn't read a price from, so it
 * can be shared to help fix the price reader for that store.
 */
object DebugPages {

    private fun dir(context: Context) = File(context.cacheDir, "debug").apply { mkdirs() }

    fun save(context: Context, store: String, url: String, html: String) {
        try {
            val name = store.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "page" }
            File(dir(context), "$name.html").writeText("<!-- $url -->\n$html")
        } catch (e: Exception) {
            // Debug copy only; never let it break a price check.
        }
    }

    fun files(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "html" }?.sortedBy { it.name } ?: emptyList()

    fun share(context: Context) {
        val uris = ArrayList(files(context).map {
            FileProvider.getUriForFile(context, "${context.packageName}.files", it)
        })
        if (uris.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/html"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Price Watch: pages without a price")
            clipData = ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share pages"))
    }
}
