package com.salamwifi.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Creds(val host: String, val port: String, val user: String, val pass: String)
data class Batch(val comment: String, val file: String, val date: Long, val count: Int, val profile: String)

class Store(private val ctx: Context) {
    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            ctx, "secure", MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) { ctx.getSharedPreferences("plain", 0) }

    fun saveCreds(c: Creds) = prefs.edit().putString("h", c.host).putString("p", c.port)
        .putString("u", c.user).putString("w", c.pass).apply()
    fun loadCreds(): Creds? {
        val h = prefs.getString("h", null) ?: return null
        return Creds(h, prefs.getString("p", "8080") ?: "8080", prefs.getString("u", "") ?: "", prefs.getString("w", "") ?: "")
    }
    fun clearCreds() = prefs.edit().clear().apply()

    private val hf get() = File(ctx.filesDir, "history.json")
    fun pdfDir() = File(ctx.filesDir, "pdfs").apply { mkdirs() }
    fun pdfFile(b: Batch) = File(pdfDir(), b.file)

    fun loadHistory(): List<Batch> = runCatching {
        val a = JSONArray(hf.readText())
        List(a.length()) {
            val o = a.getJSONObject(it)
            Batch(o.getString("c"), o.getString("f"), o.getLong("d"), o.getInt("n"), o.getString("p"))
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    private fun save(l: List<Batch>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("c", it.comment).put("f", it.file).put("d", it.date).put("n", it.count).put("p", it.profile)) }
        hf.writeText(a.toString())
    }
    fun add(b: Batch) = save(loadHistory().filter { it.comment != b.comment } + b)
    fun remove(b: Batch) { pdfFile(b).delete(); save(loadHistory().filter { it.comment != b.comment }) }
}
