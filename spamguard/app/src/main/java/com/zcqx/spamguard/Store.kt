package com.zcqx.spamguard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object Store {
    private fun p(c: Context) = c.getSharedPreferences("sg", 0)

    fun log(c: Context, pkg: String, label: String, reason: String) {
        val t = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(Date())
        val old = p(c).getString("log", "")!!.split("\n").filter { it.isNotBlank() }
        val all = (listOf("$t|$pkg|$label|$reason") + old).take(100)
        p(c).edit().putString("log", all.joinToString("\n")).apply()
    }

    fun entries(c: Context): List<List<String>> =
        p(c).getString("log", "")!!.split("\n").filter { it.isNotBlank() }.map { it.split("|") }

    fun clear(c: Context) = p(c).edit().remove("log").apply()

    fun allow(c: Context, pkg: String) =
        p(c).edit().putStringSet("allow", allowed(c) + pkg).apply()

    fun allowed(c: Context): Set<String> = p(c).getStringSet("allow", emptySet())!!.toSet()
}
