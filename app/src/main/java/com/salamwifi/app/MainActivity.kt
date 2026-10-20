package com.salamwifi.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

class AppVM(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    val store = Store(ctx)
    var phase by mutableStateOf(0)          // 0 démarrage, 1 connexion, 2 principal
    var loginError by mutableStateOf("")
    var busy by mutableStateOf(false)
    var servers by mutableStateOf(listOf<String>())
    var profiles by mutableStateOf(listOf<Profile>())
    var history by mutableStateOf(store.loadHistory())
    var progress by mutableStateOf(0f)
    var status by mutableStateOf("")
    var statusOk by mutableStateOf(true)
    private var router: Router? = null
    private val loginScript = ctx.assets.open("on_login.rsc").bufferedReader().use { it.readText() }
    private val expireScript = ctx.assets.open("expire.rsc").bufferedReader().use { it.readText() }

    init {
        val c = store.loadCreds()
        if (c == null) phase = 1 else connect(c.host, c.port, c.user, c.pass)
    }

    fun connect(host: String, port: String, user: String, pass: String) {
        viewModelScope.launch {
            busy = true; loginError = ""
            try {
                val r = Router(host.trim(), port.trim().toIntOrNull() ?: 8080, user.trim(), pass)
                val (s, p) = withContext(Dispatchers.IO) { r.ping(); Pair(r.servers(), r.profiles()) }
                router = r; servers = s; profiles = p
                store.saveCreds(Creds(host.trim(), port.trim(), user.trim(), pass))
                phase = 2
            } catch (e: Exception) {
                loginError = e.message ?: "Connexion impossible"; phase = 1
            }
            busy = false
        }
    }

    fun logout() { store.clearCreds(); router = null; phase = 1 }

    private suspend fun reload() {
        val r = router ?: return
        val (s, p) = withContext(Dispatchers.IO) { Pair(r.servers(), r.profiles()) }
        servers = s; profiles = p
    }

    fun refresh() {
        viewModelScope.launch {
            try { reload() } catch (e: Exception) { status = e.message ?: "Erreur"; statusOk = false }
        }
    }

    fun install(p: Profile) {
        viewModelScope.launch {
            busy = true; status = ""
            try {
                withContext(Dispatchers.IO) { router!!.installExpiry(p, expireScript, loginScript) }
                reload()
                status = "Expiration automatique activée sur « ${p.name} »."; statusOk = true
            } catch (e: Exception) { status = e.message ?: "Erreur"; statusOk = false }
            busy = false
        }
    }

    fun generate(server: String, p: Profile, qty: Int, uLen: Int, uKind: Kind, pLen: Int, pKind: Kind, comment: String) {
        viewModelScope.launch {
            busy = true; progress = 0f; status = ""
            try {
                val c = comment.trim()
                if (c.isEmpty()) throw IllegalStateException("Saisissez un commentaire.")
                val msg = withContext(Dispatchers.IO) {
                    val r = router!!
                    if (history.any { it.comment.equals(c, true) } || r.commentExists(c))
                        throw IllegalStateException("Ce commentaire existe déjà, choisissez-en un autre.")
                    val list = Gen.make(qty, uLen, uKind, pLen, pKind, r.userNames())
                    val ok = ArrayList<Voucher>(); var fail = 0; var lastErr = ""
                    list.forEachIndexed { i, v ->
                        try { r.createUser(v, p, server, c); ok.add(v) }
                        catch (e: Exception) { fail++; lastErr = e.message ?: "" }
                        progress = (i + 1) / list.size.toFloat()
                    }
                    if (ok.isEmpty()) throw IllegalStateException("Aucun ticket créé. $lastErr")
                    val f = java.io.File(store.pdfDir(), safeName(c) + ".pdf")
                    PdfMaker.make(f, ok, fmtDur(p.timeout), r.host)
                    store.add(Batch(c, f.name, System.currentTimeMillis(), ok.size, p.name))
                    "${ok.size} tickets créés, PDF « $c » disponible dans l'onglet PDF." +
                        if (fail > 0) " ($fail échecs : $lastErr)" else ""
                }
                history = store.loadHistory()
                status = msg; statusOk = true
            } catch (e: Exception) { status = e.message ?: "Erreur"; statusOk = false }
            busy = false
        }
    }

    fun remove(b: Batch) { store.remove(b); history = store.loadHistory() }
}
