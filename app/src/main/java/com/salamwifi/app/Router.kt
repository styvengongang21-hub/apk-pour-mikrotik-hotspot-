package com.salamwifi.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

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

class ApiError(msg: String) : RuntimeException(msg)

/** Client de l'API binaire MikroTik (port 8728), RouterOS 6.43+ et 7. */
class Router(val host: String, private val port: Int, private val user: String, private val pass: String) {
    private var sock: Socket? = null
    private var inp: DataInputStream? = null
    private var out: OutputStream? = null

    private fun close() {
        runCatching { sock?.close() }
        sock = null; inp = null; out = null
    }

    private fun open() {
        close()
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), 6000)
        } catch (e: IOException) {
            runCatching { s.close() }
            throw IOException("Impossible de joindre $host:$port. Vérifiez l'IP, le port et que le service API est actif.")
        }
        s.soTimeout = 20000
        sock = s
        inp = DataInputStream(BufferedInputStream(s.getInputStream()))
        out = BufferedOutputStream(s.getOutputStream())
        try {
            exchange("/login", listOf("=name=$user", "=password=$pass"))
        } catch (e: ApiError) {
            close()
            throw ApiError("Identifiant ou mot de passe refusé par le routeur.")
        }
    }

    private fun writeWord(o: OutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        val n = b.size
        when {
            n < 0x80 -> o.write(n)
            n < 0x4000 -> { o.write((n shr 8) or 0x80); o.write(n and 0xFF) }
            n < 0x200000 -> { o.write((n shr 16) or 0xC0); o.write((n shr 8) and 0xFF); o.write(n and 0xFF) }
            else -> { o.write((n shr 24) or 0xE0); o.write((n shr 16) and 0xFF); o.write((n shr 8) and 0xFF); o.write(n and 0xFF) }
        }
        o.write(b)
    }

    private fun readLen(i: DataInputStream): Int {
        val c = i.readUnsignedByte()
        return when {
            c < 0x80 -> c
            c < 0xC0 -> ((c and 0x3F) shl 8) or i.readUnsignedByte()
            c < 0xE0 -> { val b1 = i.readUnsignedByte(); val b2 = i.readUnsignedByte(); ((c and 0x1F) shl 16) or (b1 shl 8) or b2 }
            c < 0xF0 -> { val b1 = i.readUnsignedByte(); val b2 = i.readUnsignedByte(); val b3 = i.readUnsignedByte()
                ((c and 0x0F) shl 24) or (b1 shl 16) or (b2 shl 8) or b3 }
            else -> i.readInt()
        }
    }

    private fun readSentence(i: DataInputStream): List<String> {
        val w = ArrayList<String>()
        while (true) {
            val n = readLen(i)
            if (n == 0) break
            val b = ByteArray(n)
            i.readFully(b)
            w.add(String(b, Charsets.UTF_8))
        }
        return w
    }

    private fun toMap(words: List<String>): Map<String, String> {
        val m = HashMap<String, String>()
        for (w in words.drop(1)) {
            if (!w.startsWith("=")) continue
            val idx = w.indexOf('=', 1)
            if (idx < 0) continue
            m[w.substring(1, idx)] = w.substring(idx + 1)
        }
        return m
    }

    private fun exchange(cmd: String, attrs: List<String>): List<Map<String, String>> {
        val o = out!!
        val i = inp!!
        writeWord(o, cmd)
        attrs.forEach { writeWord(o, it) }
        o.write(0)
        o.flush()
        val rows = ArrayList<Map<String, String>>()
        var err: String? = null
        while (true) {
            val words = readSentence(i)
            if (words.isEmpty()) continue
            val type = words[0]
            if (type == "!re") rows.add(toMap(words))
            else if (type == "!trap") err = toMap(words)["message"] ?: "Erreur du routeur"
            else if (type == "!fatal") throw IOException(words.getOrElse(1) { "Connexion fermée par le routeur" })
            else if (type == "!done") break
        }
        if (err != null) throw ApiError(err)
        return rows
    }

    @Synchronized
    private fun call(cmd: String, vararg attrs: String): List<Map<String, String>> {
        val a = attrs.toList()
        val had = sock != null
        try {
            if (sock == null) open()
            return exchange(cmd, a)
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            if (!had) throw e
            open()                      // session perdue : on se reconnecte une fois
            return exchange(cmd, a)
        }
    }

    fun ping() { call("/system/identity/print") }
    fun servers() = call("/ip/hotspot/print").mapNotNull { it["name"] }
    fun profiles() = call("/ip/hotspot/user/profile/print").map {
        Profile(it[".id"] ?: "", it["name"] ?: "", parseDur(it["session-timeout"]), it["on-login"] ?: "")
    }
    fun userNames(): MutableSet<String> =
        call("/ip/hotspot/user/print", "=.proplist=name").mapNotNull { it["name"] }.toHashSet()
    fun commentExists(c: String) =
        call("/ip/hotspot/user/print", "?comment=$c", "=.proplist=name").isNotEmpty()

    fun createUser(v: Voucher, p: Profile, server: String, comment: String) {
        val a = arrayListOf("=name=${v.user}", "=password=${v.pass}", "=profile=${p.name}",
            "=comment=$comment", "=limit-uptime=${p.timeout}s")
        if (server != "all") a.add("=server=$server")
        call("/ip/hotspot/user/add", *a.toTypedArray())
    }

    private fun upsert(base: String, name: String, attrs: List<String>) {
        val f = call("$base/print", "?name=$name", "=.proplist=.id")
        if (f.isNotEmpty()) call("$base/set", *(listOf("=.id=${f[0][".id"]}") + attrs).toTypedArray())
        else call("$base/add", *(listOf("=name=$name") + attrs).toTypedArray())
    }

    fun installExpiry(p: Profile, expire: String, login: String) {
        val pol = "read,write,test,policy"
        upsert("/system/script", "salam-expire", listOf("=source=$expire", "=policy=$pol"))
        upsert("/system/scheduler", "salam-sweeper",
            listOf("=interval=1m", "=on-event=salam-expire", "=policy=$pol", "=start-time=startup"))
        val script = if (p.onLogin.isBlank()) login else p.onLogin.trimEnd() + "\n" + login
        call("/ip/hotspot/user/profile/set", "=.id=${p.id}", "=on-login=$script")
    }
}
