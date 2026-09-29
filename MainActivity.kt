package com.zcqx.spamguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var list: ListView

    private fun d(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(40), d(20), d(20))
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "SpamGuard"; textSize = 28f; setTextColor(0xFF22C55E.toInt())
        })
        status = TextView(this).apply { setTextColor(Color.WHITE); setPadding(0, d(8), 0, d(8)) }
        root.addView(status)
        root.addView(btn("Enable protection (Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(btn("Allow alert notifications") {
            if (Build.VERSION.SDK_INT >= 33)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        root.addView(btn("Clear log") { Store.clear(this); refresh() })
        root.addView(TextView(this).apply {
            text = "Detected spam (long-press an entry to allow that app)"
            setTextColor(Color.GRAY); setPadding(0, d(16), 0, d(4))
        })
        list = ListView(this)
        root.addView(list)
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        status.text = if (on) "Protection: ON" else "Protection: OFF - tap Enable and switch on SpamGuard"
        val items = Store.entries(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            items.map { "${it[2]}\n${it[3]}\n${it[0]}" })
        list.setOnItemLongClickListener { _, _, i, _ ->
            Store.allow(this, items[i][1])
            Toast.makeText(this, "${items[i][2]} allowed", Toast.LENGTH_SHORT).show()
            true
        }
    }
}
