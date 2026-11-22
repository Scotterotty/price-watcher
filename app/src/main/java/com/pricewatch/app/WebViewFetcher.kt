package com.pricewatch.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loads a page in an invisible WebView (the phone's real Chrome engine) and
 * returns the HTML after scripts have run. Stores like Amazon often refuse
 * plain HTTP requests but serve a normal page to a real browser, and some
 * stores only fill in the price with JavaScript.
 */
object WebViewFetcher {

    data class Page(val html: String, val finalUrl: String)

    /** Call from a background thread; blocks up to [timeoutSec]. */
    @SuppressLint("SetJavaScriptEnabled")
    fun load(context: Context, url: String, timeoutSec: Long = 30): Page? {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Don't call on the main thread" }
        val main = Handler(Looper.getMainLooper())
        val done = CountDownLatch(1)
        var result: Page? = null
        var webView: WebView? = null

        main.post {
            val wv = WebView(context.applicationContext)
            webView = wv
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.loadsImagesAutomatically = false
            wv.webViewClient = object : WebViewClient() {
                private var grabbed = false
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    if (grabbed) return
                    grabbed = true
                    // Give the page's scripts a moment to render the price.
                    main.postDelayed({
                        view.evaluateJavascript("document.documentElement.outerHTML") { json ->
                            result = try {
                                Page(JSONArray("[$json]").getString(0), view.url ?: finishedUrl)
                            } catch (e: Exception) {
                                null
                            }
                            done.countDown()
                        }
                    }, 3000)
                }
            }
            wv.loadUrl(url)
        }

        done.await(timeoutSec, TimeUnit.SECONDS)
        main.post {
            webView?.stopLoading()
            webView?.destroy()
        }
        return result
    }
}
