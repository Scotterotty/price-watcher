package com.pricewatch.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager

class MainActivity : Activity() {

    private lateinit var store: ItemStore
    private lateinit var list: LinearLayout
    private lateinit var checkButton: Button
    private val workObserver = Observer<List<WorkInfo>> { infos ->
        val running = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        checkButton.isEnabled = !running
        checkButton.text = if (running) "Checking…" else "Check now"
        checkButton.alpha = if (running) 0.6f else 1f
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ItemStore(this)
        window.statusBarColor = Theme.BG
        window.navigationBarColor = Theme.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        // Header: gradient wordmark + tagline.
        root.addView(TextView(this).apply {
            text = "Price Watch"
            textSize = 32f
            setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL))
            setTextColor(Theme.TEXT)
            post {
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Theme.CYAN, Theme.VIOLET, Shader.TileMode.CLAMP)
                invalidate()
            }
        })
        root.addView(text("Alerts you when prices hit rock bottom", 14f, Theme.MUTED).apply {
            setPadding(0, dp(2), 0, dp(18))
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        checkButton = pill("Check now", filled = true) { PriceCheckWorker.runNow(this@MainActivity) }
        val addButton = pill("+ Add item", filled = false) { editDialog(null) }
        buttons.addView(checkButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        buttons.addView(addButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(buttons)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(list)

        root.addView(text(
            "Checks every 6 hours. Tap an item to edit it, long-press to open the store.",
            12f, Theme.MUTED,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Theme.BG)
            isFillViewport = true
            addView(root)
        })

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(PriceCheckWorker.nowWorkName())
            .observeForever(workObserver)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(PriceCheckWorker.nowWorkName())
            .removeObserver(workObserver)
        super.onDestroy()
    }

    private fun render() {
        list.removeAllViews()
        val items = store.load()
        if (items.isEmpty()) {
            list.addView(text("Nothing watched yet. Tap + Add item.", 15f, Theme.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, dp(40))
            })
        }
        items.forEach { list.addView(card(it)) }
        PriceWidget.updateAll(this)
    }

    private fun card(item: WatchItem): View {
        val cur = item.current
        val low = item.lowest
        val atLow = item.isAtLowest
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (atLow) Theme.CARD_LOW else Theme.CARD)
                setStroke(dp(1), if (atLow) Theme.GREEN else 0x22FFFFFF)
            }
            if (Build.VERSION.SDK_INT >= 28 && atLow) {
                elevation = dp(8).toFloat()
                outlineSpotShadowColor = Theme.GREEN
                outlineAmbientShadowColor = Theme.GREEN
            }
            setOnClickListener { editDialog(item) }
            setOnLongClickListener {
                item.urls.firstOrNull()?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                true
            }
        }

        // Title row with optional LOWEST badge.
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text(item.name, 16f, Theme.TEXT, bold = true).apply {
            maxLines = 2
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (atLow) {
            titleRow.addView(text("LOWEST", 11f, Theme.BG, bold = true).apply {
                letterSpacing = 0.1f
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(99).toFloat()
                    setColor(Theme.GREEN)
                }
            })
        }
        box.addView(titleRow)

        // Big price + change since the previous reading.
        val priceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(8), 0, 0)
        }
        priceRow.addView(text(cur?.let { Format.money(it.price) } ?: "—", 34f,
            if (atLow) Theme.GREEN else Theme.TEXT).apply {
            setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL))
        })
        val prev = item.history.dropLast(1).lastOrNull()
        if (cur != null && prev != null && prev.price != cur.price) {
            val down = cur.price < prev.price
            priceRow.addView(text(
                (if (down) "  ▼ " else "  ▲ ") + Format.money(kotlin.math.abs(cur.price - prev.price)),
                14f, if (down) Theme.GREEN else Theme.RED, bold = true,
            ).apply { setPadding(0, 0, 0, dp(6)) })
        }
        box.addView(priceRow)
        if (cur != null) box.addView(text("at ${cur.store}", 13f, Theme.MUTED))

        // Price-trend sparkline.
        if (item.history.size >= 2) {
            box.addView(ImageView(this).apply {
                setPadding(0, dp(12), 0, dp(4))
                post {
                    setImageBitmap(Sparkline.render(item.history.map { it.price }, width, dp(56), atLow))
                }
            }, LinearLayout.LayoutParams(-1, dp(72)))
        }

        val details = buildList {
            if (!atLow && low != null && item.history.size > 1) {
                add("Lowest ${Format.money(low.price)} at ${low.store} on " +
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(low.time))
            }
            item.targetPrice?.let { add("Target ${Format.money(it)}") }
            add("${item.urls.size} store link${if (item.urls.size == 1) "" else "s"} · checked ${Format.ago(item.lastChecked)}")
        }
        box.addView(text(details.joinToString("\n"), 12f, Theme.MUTED).apply {
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        item.lastError?.let { box.addView(text(it, 12f, 0xFFFFC266.toInt()).apply { setPadding(0, dp(4), 0, 0) }) }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(box)
        }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
    }

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        setTextColor(if (filled) Theme.BG else Theme.TEXT)
        stateListAnimator = null
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (filled) intArrayOf(Theme.CYAN, Theme.VIOLET) else intArrayOf(Theme.CARD, Theme.CARD),
        ).apply {
            cornerRadius = dp(99).toFloat()
            if (!filled) setStroke(dp(1), 0x33FFFFFF)
        }
        setOnClickListener { onClick() }
    }

    private fun editDialog(existing: WatchItem?) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply {
            hint = "Item name"
            setText(existing?.name ?: "")
        }
        val urls = EditText(this).apply {
            hint = "Paste product links (Amazon, Best Buy, Walmart, Target…)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI
            minLines = 3
            gravity = Gravity.TOP
            setText(existing?.urls?.joinToString("\n") ?: "")
        }
        val target = EditText(this).apply {
            hint = "Target price (optional)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(existing?.targetPrice?.toString() ?: "")
        }
        form.addView(name)
        form.addView(urls)
        form.addView(target)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (existing == null) "Watch an item" else "Edit item")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) {
                    Toast.makeText(this, "Give the item a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val links = Links.extract(urls.text.toString())
                val t = target.text.toString().trim().removePrefix("$").toDoubleOrNull()
                val item = existing?.copy(name = n, urls = links, targetPrice = t, lastAlertedPrice = null)
                    ?: WatchItem(name = n, urls = links, targetPrice = t)
                store.upsert(item)
                render()
                if (links.isNotEmpty()) PriceCheckWorker.runNow(this)
            }
            .setNegativeButton("Cancel", null)
        if (existing != null) {
            dialog.setNeutralButton("Delete") { _, _ ->
                store.delete(existing.id)
                render()
            }
        }
        dialog.show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
