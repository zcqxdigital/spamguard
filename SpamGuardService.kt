package com.zcqx.spamguard

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

class SpamGuardService : AccessibilityService() {
    private val hits = HashMap<String, ArrayDeque<Long>>()
    private var lastAct = 0L
    private val base = setOf(
        "android", "com.android.systemui", "com.android.settings",
        "com.google.android.permissioncontroller", "com.android.permissioncontroller"
    )
    private val system by lazy {
        val home = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }
        val ime = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .inputMethodList.map { it.packageName }
        base + home + ime + packageName
    }

    private fun safe(pkg: String) = pkg in system || pkg in Store.allowed(this)

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (now - lastAct < 4000) return

        // 1) Rapid popup bursts: an app opening many windows in a few seconds
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = e.packageName?.toString() ?: return
            if (!safe(pkg)) {
                val q = hits.getOrPut(pkg) { ArrayDeque() }
                q.addLast(now)
                while (q.isNotEmpty() && now - q.first() > 6000) q.removeFirst()
                if (q.size >= 6) { q.clear(); trigger(pkg, "Rapid popups (6+ windows in 6s)"); return }
            }
        }

        // 2) Overlays drawn over other apps
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            val node = w.root ?: continue
            val pkg = node.packageName?.toString()
            node.recycle()
            if (pkg != null && !safe(pkg)) { trigger(pkg, "Drew an overlay over your screen"); return }
        }
    }

    private fun label(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (x: Exception) { pkg }

    private fun trigger(pkg: String, reason: String) {
        lastAct = System.currentTimeMillis()
        val name = label(pkg)
        Store.log(this, pkg, name, reason)
        performGlobalAction(GLOBAL_ACTION_HOME)

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("sg", "Spam alerts", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(
            this, pkg.hashCode(),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            pkg.hashCode(),
            Notification.Builder(this, "sg")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Blocked popup from $name")
                .setContentText("$reason. Tap to open its settings and remove or uninstall it.")
                .setContentIntent(pi).setAutoCancel(true).build()
        )
    }

    override fun onInterrupt() {}
}
