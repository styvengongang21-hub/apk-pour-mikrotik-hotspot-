@file:OptIn(ExperimentalMaterial3Api::class)

package com.salamwifi.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Teal = Color(0xFF0B6E75)
private val Orange = Color(0xFFFF9F1C)

@Composable
fun App(vm: AppVM = viewModel()) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Orange,
        background = Color(0xFFF4F8F8), surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (vm.phase) {
                0 -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Teal)
                        Spacer(Modifier.height(12.dp)); Text("Connexion au routeur…")
                    }
                }
                1 -> LoginScreen(vm)
                else -> MainScreen(vm)
            }
        }
    }
}

@Composable
fun LoginScreen(vm: AppVM) {
    val c = remember { vm.store.loadCreds() }
    var host by remember { mutableStateOf(c?.host ?: "192.168.88.1") }
    var port by remember { mutableStateOf(c?.port ?: "8080") }
    var user by remember { mutableStateOf(c?.user ?: "") }
    var pass by remember { mutableStateOf(c?.pass ?: "") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SALAM-WIFI", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Teal)
        Text("Générateur de vouchers MikroTik", color = Color.Gray)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(host, { host = it }, label = { Text("Adresse IP du routeur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port API REST (www)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(user, { user = it }, label = { Text("Utilisateur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pass, { pass = it }, label = { Text("Mot de passe") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (vm.loginError.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(vm.loginError, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { vm.connect(host, port, user, pass) },
            enabled = !vm.busy && host.isNotBlank() && user.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Se connecter")
        }
    }
}

@Composable
fun MainScreen(vm: AppVM) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SALAM-WIFI", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Teal, titleContentColor = Color.White, actionIconContentColor = Color.White),
                actions = { TextButton(onClick = { vm.logout() }) { Text("Déconnexion", color = Color.White) } })
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("🎟️") }, label = { Text("Générer") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📄") }, label = { Text("PDF (${vm.history.size})") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) { if (tab == 0) GenerateScreen(vm) else HistoryScreen(vm) }
    }
}

@Composable
fun Picker(label: String, options: List<String>, value: String, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(i); open = false }) }
        }
    }
}

@Composable
fun SliderRow(label: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label : ${v.toInt()}", fontWeight = FontWeight.Medium)
        Slider(value = v, onValueChange = { onChange(Math.round(it).toFloat()) }, valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1)
    }
}

@Composable
fun KindRow(sel: Kind, onPick: (Kind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Kind.values().forEach { k -> FilterChip(selected = sel == k, onClick = { onPick(k) }, label = { Text(k.short) }) }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Teal, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
fun GenerateScreen(vm: AppVM) {
    var serverIdx by remember { mutableStateOf(0) }
    var profIdx by remember { mutableStateOf(0) }
    var qty by remember { mutableStateOf(10f) }
    var uLen by remember { mutableStateOf(6f) }
    var uKind by remember { mutableStateOf(Kind.NUM) }
    var pLen by remember { mutableStateOf(6f) }
    var pKind by remember { mutableStateOf(Kind.NUM) }
    var comment by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date()) + "_Lot-" + (vm.history.size + 1))
    }
    val servers = listOf("all") + vm.servers
    val p = vm.profiles.getOrNull(profIdx)
    val labels = vm.profiles.map { "${it.name}  (${fmtDur(it.timeout)})" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("Routeur") {
            Picker("Serveur hotspot", servers, servers.getOrElse(serverIdx) { "all" }) { serverIdx = it }
            Picker("Profil utilisateur", labels, labels.getOrElse(profIdx) { "Aucun profil" }) { profIdx = it }
            if (p != null) {
                when {
                    p.timeout <= 0 -> Text("⚠️ Ce profil n'a pas de Session Timeout : impossible de calculer l'expiration.",
                        color = MaterialTheme.colorScheme.error)
                    p.installed -> Text("✅ Valable ${fmtDur(p.timeout)} après la première connexion, puis désactivé automatiquement.", color = Teal)
                    else -> {
                        Text("⚠️ L'expiration automatique n'est pas encore activée sur ce profil." +
                            if (p.onLogin.isNotBlank()) " Son script On Login existant sera conservé, le nôtre sera ajouté à la suite." else "")
                        OutlinedButton(onClick = { vm.install(p) }, enabled = !vm.busy) { Text("Activer l'expiration automatique") }
                    }
                }
            }
            TextButton(onClick = { vm.refresh() }) { Text("↻ Actualiser les profils") }
        }
        Section("Quantité") { SliderRow("Nombre de vouchers", qty, 1f..100f) { qty = it } }
        Section("Nom d'utilisateur") {
            SliderRow("Longueur", uLen, 4f..12f) { uLen = it }
            KindRow(uKind) { uKind = it }
        }
        Section("Mot de passe") {
            SliderRow("Longueur", pLen, 4f..12f) { pLen = it }
            KindRow(pKind) { pKind = it }
        }
        Section("Commentaire (nom du PDF)") {
            OutlinedTextField(comment, { comment = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (vm.busy && vm.progress > 0f) LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth(), color = Orange)
        if (vm.status.isNotEmpty()) Text(vm.status, color = if (vm.statusOk) Teal else MaterialTheme.colorScheme.error)
        Button(
            onClick = { vm.generate(servers[serverIdx], p!!, qty.toInt(), uLen.toInt(), uKind, pLen.toInt(), pKind, comment) },
            enabled = !vm.busy && p != null && p.timeout > 0 && p.installed,
            colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Générer ${qty.toInt()} vouchers + PDF", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun HistoryScreen(vm: AppVM) {
    val ctx = LocalContext.current
    var toDelete by remember { mutableStateOf<Batch?>(null) }
    if (vm.history.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Aucun PDF généré pour le moment.", color = Color.Gray) }
    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(vm.history) { b ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("📄 ${b.comment}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Teal)
                    Text("${b.count} tickets • profil ${b.profile}", color = Color.DarkGray)
                    Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(b.date)), color = Color.Gray, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth()) {
                        TextButton({ saveToDocuments(ctx, vm.store.pdfFile(b), b.comment) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Télécharger", fontSize = 13.sp) }
                        TextButton({ share(ctx, vm.store.pdfFile(b)) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Partager", fontSize = 13.sp) }
                        TextButton({ toDelete = b }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                            Text("Supprimer", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { b ->
        AlertDialog(onDismissRequest = { toDelete = null },
            title = { Text("Supprimer le PDF ?") },
            text = { Text("« ${b.comment} » sera retiré de l'application. Les tickets sur le routeur ne sont pas supprimés.") },
            confirmButton = { TextButton({ vm.remove(b); toDelete = null }) { Text("Supprimer") } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Annuler") } })
    }
}

fun saveToDocuments(ctx: Context, file: File, name: String) {
    try {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(name) + ".pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/SALAM-WIFI")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Files.getContentUri("external"), cv) ?: throw Exception("Création impossible")
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        Toast.makeText(ctx, "Enregistré dans Documents/SALAM-WIFI", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Échec : ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun share(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Partager le PDF"))
}
