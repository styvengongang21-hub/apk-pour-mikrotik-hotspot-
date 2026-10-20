package com.salamwifi.app

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Profile(val id: String, val name: String, val timeout: Long, val onLogin: String) {
    val installed get() = onLogin.contains("SALAM-WIFI")
}
data class Voucher(val user: String, val pass: String)

fun parseDur(s: String?): Long {
    if (s.isNullOrBlank() || s == "none") return 0
    Regex("^(?:(\\d+)d)?(\\d{1,3}):(\\d{2}):(\\d{2})$").find(s)?.let { m ->
        val g = m.groupValues
        return (g[1].toLongOrNull() ?: 0) * 86400 + g[2].toLong() * 3600 + g[3].toLong() * 60 + g[4].toLong()
    }
    var t = 0L
    Regex("(\\d+)([wdhms])").findAll(s).forEach {
        val n = it.groupValues[1].toLong()
        t += n * when (it.groupValues[2]) { "w" -> 604800L; "d" -> 86400L; "h" -> 3600L; "m" -> 60L; else -> 1L }
    }
    return t
}

fun fmtDur(s: Long): String {
    fun u(n: Long, one: String, many: String) = if (n == 1L) "1 $one" else "$n $many"
    return when {
        s <= 0 -> "durée non définie"
        s % 2592000 == 0L -> u(s / 2592000, "mois", "mois")
        s % 604800 == 0L -> u(s / 604800, "semaine", "semaines")
        s % 86400 == 0L -> u(s / 86400, "jour", "jours")
        s % 3600 == 0L -> "${s / 3600} h"
        s % 60 == 0L -> "${s / 60} min"
        else -> "$s s"
    }
}

class Router(val host: String, private val port: Int, user: String, pass: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val auth = Credentials.basic(user, pass)
    private val media = "application/json".toMediaType()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun call(method: String, path: String, body: JSONObject? = null): String {
        val rb = when {
            body != null -> body.toString().toRequestBody(media)
            method == "GET" || method == "DELETE" -> null
            else -> "{}".toRequestBody(media)
        }
        val req = Request.Builder().url("http://$host:$port/rest$path")
            .header("Authorization", auth).method(method, rb).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val d = runCatching { JSONObject(t).optString("detail") }.getOrDefault("")
                val hint = if (r.code == 401) " (identifiants refusés)" else ""
                throw RuntimeException("Erreur ${r.code}: $d$hint".trim())
            }
            return t
        }
    }

    private fun list(path: String): List<JSONObject> {
        val a = JSONArray(call("GET", path))
        return List(a.length()) { a.getJSONObject(it) }
    }

    fun ping() { call("GET", "/system/identity") }
    fun servers() = list("/ip/hotspot").map { it.getString("name") }
    fun profiles() = list("/ip/hotspot/user/profile").map {
        Profile(it.getString(".id"), it.getString("name"), parseDur(it.optString("session-timeout")), it.optString("on-login"))
    }
    fun userNames(): MutableSet<String> =
        list("/ip/hotspot/user?.proplist=name").map { it.optString("name") }.toHashSet()
    fun commentExists(c: String) =
        list("/ip/hotspot/user?comment=${enc(c)}&.proplist=name").isNotEmpty()

    fun createUser(v: Voucher, p: Profile, server: String, comment: String) {
        val b = JSONObject().put("name", v.user).put("password", v.pass)
            .put("profile", p.name).put("comment", comment).put("limit-uptime", "${p.timeout}s")
        if (server != "all") b.put("server", server)
        call("PUT", "/ip/hotspot/user", b)
    }

    private fun upsert(path: String, name: String, body: JSONObject) {
        val found = list("$path?name=${enc(name)}")
        if (found.isNotEmpty()) call("PATCH", "$path/${found[0].getString(".id")}", body)
        else call("PUT", path, body.put("name", name))
    }

    fun installExpiry(p: Profile, expire: String, login: String) {
        val pol = "read,write,test,policy"
        upsert("/system/script", "salam-expire", JSONObject().put("source", expire).put("policy", pol))
        upsert("/system/scheduler", "salam-sweeper", JSONObject()
            .put("interval", "1m").put("on-event", "salam-expire").put("policy", pol).put("start-time", "startup"))
        val script = if (p.onLogin.isBlank()) login else p.onLogin.trimEnd() + "\n" + login
        call("PATCH", "/ip/hotspot/user/profile/${p.id}", JSONObject().put("on-login", script))
    }
}
