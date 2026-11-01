package com.liamliz.tvhack

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import java.io.File

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val animationKeys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    private lateinit var host: EditText
    private lateinit var connectPort: EditText
    private lateinit var pairPort: EditText
    private lateinit var pairCode: EditText
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var continueButton: Button
    private var client: Kadb? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KadbCert.configure(store = OkioFilePrivateKeyStore(File(filesDir, "adb-host-key.pem").absolutePath.toPath()))

        val prefs = getSharedPreferences("liamliz_connection", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 28)
            setBackgroundColor(0xFF0B1220.toInt())
        }
        fun heading(value: String, size: Float = 16f) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(0xFFE5EDF7.toInt())
            setPadding(0, 15, 0, 7)
        }
        fun field(hintText: String, value: String, numeric: Boolean = false) = EditText(this).apply {
            hint = hintText
            setText(value)
            textSize = 16f
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF94A3B8.toInt())
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xFF1E293B.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7 }
        }
        fun action(label: String, callback: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setOnClickListener { callback() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 5; bottomMargin = 3 }
        }

        root.addView(heading("LIAMLIZ TV HACK", 27f))
        root.addView(heading("Control ADB Wi-Fi para Android y Android TV. No necesita Termux, navegador ni app auxiliar."))
        root.addView(heading("Dispositivo destino"))
        host = field("IP o nombre del dispositivo", prefs.getString("host", "") ?: "")
        connectPort = field("Puerto ADB de conexión (ej. 5555 o puerto TLS)", prefs.getString("connect_port", "5555") ?: "5555", true)
        root.addView(host)
        root.addView(connectPort)
        root.addView(action("CONECTAR / REINTENTAR", ::connectDevice))

        root.addView(heading("Vinculación inalámbrica Android 11+"))
        root.addView(heading("Abre Depuración inalámbrica en el destino. Usa el puerto temporal y el código de esa pantalla."))
        pairPort = field("Puerto temporal de vinculación", prefs.getString("pair_port", "") ?: "", true)
        pairCode = field("Código de vinculación de 6 dígitos", "")
        root.addView(pairPort)
        root.addView(pairCode)
        root.addView(action("VINCULAR DISPOSITIVO", ::pairDevice))

        status = heading("Estado: sin conexión verificada", 17f)
        root.addView(status)
        output = heading("Introduce la IP y el puerto de conexión del destino. La vinculación usa un puerto distinto.", 13f)
        root.addView(output)
        continueButton = action("CONTINUAR · YA AUTORICÉ ADB", ::connectDevice).apply { visibility = View.GONE }
        root.addView(continueButton)

        root.addView(heading("Diagnóstico del dispositivo"))
        root.addView(action("Ejecutar diagnóstico completo", ::diagnostics))
        root.addView(action("Ver aplicaciones de terceros", ::installedApps))

        root.addView(heading("Perfil seguro y reversible"))
        root.addView(heading("Cambia solo las tres escalas de animación a 0.5x. Guarda los valores previos para restaurarlos."))
        root.addView(action("Aplicar perfil 0.5x", ::applySafeProfile))
        root.addView(action("Restaurar valores anteriores", ::restoreSafeProfile))

        root.addView(heading("Control remoto"))
        addDpad(root, ::sendKey)
        root.addView(action("DESCONECTAR", ::disconnect))
        root.addView(heading("Seguridad: no ejecuta root, no desinstala ni deshabilita aplicaciones y no cambia actualizaciones, DNS o kernel. La única modificación incluida es el perfil reversible de animaciones.", 12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addDpad(root: LinearLayout, send: (Int) -> Unit) {
        fun key(label: String, code: Int) = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 18f
            setOnClickListener { send(code) }
            layoutParams = LinearLayout.LayoutParams(0, 58, 1f).apply { setMargins(4, 4, 4, 4) }
        }
        fun row(vararg buttons: Button?) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            buttons.forEach { b ->
                if (b == null) line.addView(View(this), LinearLayout.LayoutParams(0, 58, 1f))
                else line.addView(b)
            }
            root.addView(line)
        }
        row(null, key("▲", 19), null)
        row(key("◀", 21), key("OK", 23), key("▶", 22))
        row(null, key("▼", 20), null)
        row(key("ATRÁS", 4), key("INICIO", 3), key("VOLVER", 4))
    }

    private fun saveConnection() {
        getSharedPreferences("liamliz_connection", MODE_PRIVATE).edit()
            .putString("host", host.text.toString().trim())
            .putString("connect_port", connectPort.text.toString().trim())
            .putString("pair_port", pairPort.text.toString().trim())
            .apply()
    }

    private fun endpoint(rawPort: String): Pair<String, Int>? {
        val address = host.text.toString().trim()
        val port = rawPort.trim().toIntOrNull()
        if (address.isBlank() || address.length > 253 ||
            address.any { !(it.isLetterOrDigit() || it == '.' || it == ':' || it == '-') }) {
            status.text = "Dirección IP/host no válida"
            return null
        }
        if (port == null || port !in 1..65535) {
            status.text = "Puerto no válido (1–65535)"
            return null
        }
        return address to port
    }

    private fun connectDevice() {
        val target = endpoint(connectPort.text.toString()) ?: return
        saveConnection()
        continueButton.visibility = View.GONE
        status.text = "Conectando a " + target.first + ":" + target.second + "…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val candidate = Kadb.create(target.first, target.second, connectTimeout = 15000, socketTimeout = 15000)
                    try {
                        val response = candidate.shell("getprop ro.product.model")
                        if (response.exitCode != 0) throw IllegalStateException(response.allOutput.ifBlank { "El dispositivo rechazó la consulta ADB." })
                        candidate to response.allOutput.trim().ifBlank { "Android / Android TV" }
                    } catch (error: Throwable) {
                        candidate.close()
                        throw error
                    }
                }
            }
            result.onSuccess { connected ->
                client?.close()
                client = connected.first
                status.text = "CONECTADO · " + connected.second
                output.text = "Sesión ADB verificada con " + target.first + ":" + target.second
                continueButton.visibility = View.GONE
            }.onFailure { error ->
                status.text = "Conexión pendiente: " + (error.message ?: "sin respuesta")
                output.text = "Si aparece «¿Permitir depuración ADB?», acepta la huella RSA en el dispositivo destino y vuelve aquí. En Android 11+, primero pulsa VINCULAR DISPOSITIVO con su puerto temporal y código; después conecta con el puerto principal. Pulsa CONTINUAR para reintentar."
                continueButton.visibility = View.VISIBLE
            }
        }
    }

    private fun pairDevice() {
        val target = endpoint(pairPort.text.toString()) ?: return
        val code = pairCode.text.toString().trim()
        if (!code.matches(Regex("[0-9]{6}"))) {
            status.text = "El código debe tener 6 dígitos"
            return
        }
        saveConnection()
        status.text = "Vinculando…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Kadb.pair(target.first, target.second, code) } }
            pairCode.setText("")
            result.onSuccess {
                status.text = "VINCULACIÓN COMPLETADA"
                output.text = "Introduce ahora el puerto de conexión de la pantalla principal de Depuración inalámbrica. No es el mismo puerto de vinculación."
                continueButton.visibility = View.VISIBLE
            }.onFailure { error ->
                status.text = "No se pudo vincular: " + (error.message ?: "error")
                output.text = "Verifica que ambos equipos estén en la misma red y que el puerto/código temporal sigan vigentes. No reutilices un código anterior."
            }
        }
    }

    private fun connectedClient(): Kadb? {
        val current = client
        if (current == null || !current.connectionCheck()) {
            status.text = "Conecta primero el dispositivo"
            output.text = "La sesión no está activa. Introduce IP y puerto y pulsa CONECTAR / REINTENTAR."
            return null
        }
        return current
    }

    private fun diagnostics() {
        val current = connectedClient() ?: return
        status.text = "Ejecutando diagnóstico de solo lectura…"
        val checks = listOf(
            "Fabricante" to "getprop ro.product.manufacturer",
            "Modelo" to "getprop ro.product.model",
            "Versión Android" to "getprop ro.build.version.release",
            "Versión SDK" to "getprop ro.build.version.sdk",
            "Memoria" to "cat /proc/meminfo | head -8",
            "Almacenamiento /data" to "df -h /data",
            "Tiempo activo" to "uptime",
            "Resolución" to "wm size",
            "Densidad" to "wm density"
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    buildString {
                        checks.forEach { check ->
                            val response = current.shell(check.second)
                            append("=== ").append(check.first).append(" ===\n")
                            append(response.allOutput.ifBlank { "(sin salida)" }).append("\n\n")
                        }
                    }
                }
            }
            result.onSuccess { status.text = "Diagnóstico completado"; output.text = it }
                .onFailure { status.text = "Error de diagnóstico: " + (it.message ?: "error") }
        }
    }

    private fun installedApps() {
        val current = connectedClient() ?: return
        status.text = "Consultando aplicaciones instaladas…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { current.shell("pm list packages -3").allOutput } }
            result.onSuccess { text ->
                val packages = text.lineSequence().filter { it.startsWith("package:") }.sorted().toList()
                status.text = "Aplicaciones de terceros: " + packages.size
                output.text = packages.joinToString("\n").ifBlank { "No se encontraron aplicaciones de terceros." }
            }.onFailure { status.text = "Error al consultar aplicaciones: " + (it.message ?: "error") }
        }
    }

    private fun applySafeProfile() {
        val current = connectedClient() ?: return
        val prefs = getSharedPreferences("liamliz_connection", MODE_PRIVATE)
        status.text = "Guardando ajustes actuales y aplicando perfil…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val previous = linkedMapOf<String, String?>()
                    animationKeys.forEach { key ->
                        val response = current.shell("settings get global " + key)
                        if (response.exitCode != 0) throw IllegalStateException("No se pudo leer " + key + ": " + response.allOutput)
                        val value = response.allOutput.trim()
                        previous[key] = if (value.isBlank() || value == "null") null else value
                    }
                    try {
                        animationKeys.forEach { key ->
                            val response = current.shell("settings put global " + key + " 0.5")
                            if (response.exitCode != 0) throw IllegalStateException("No se pudo aplicar " + key + ": " + response.allOutput)
                        }
                    } catch (error: Throwable) {
                        previous.forEach { (key, value) ->
                            val restore = if (value == null) "settings delete global " + key else "settings put global " + key + " " + value
                            runCatching { current.shell(restore) }
                        }
                        throw error
                    }
                    val edit = prefs.edit()
                        .putString("profile_host", host.text.toString().trim())
                        .putInt("profile_port", connectPort.text.toString().trim().toInt())
                        .putBoolean("profile_saved", true)
                    previous.forEach { (key, value) ->
                        edit.putBoolean("profile_has_" + key, value != null)
                        if (value != null) edit.putString("profile_value_" + key, value)
                        else edit.remove("profile_value_" + key)
                    }
                    edit.apply()
                    "Perfil aplicado: animaciones a 0.5x.\nValores anteriores guardados para restauración."
                }
            }
            result.onSuccess { status.text = "PERFIL APLICADO"; output.text = it }
                .onFailure { status.text = "No se pudo aplicar el perfil"; output.text = it.message ?: "Error desconocido" }
        }
    }

    private fun restoreSafeProfile() {
        val current = connectedClient() ?: return
        val prefs = getSharedPreferences("liamliz_connection", MODE_PRIVATE)
        if (!prefs.getBoolean("profile_saved", false)) {
            status.text = "No hay un perfil previo guardado"
            return
        }
        val savedHost = prefs.getString("profile_host", "") ?: ""
        val savedPort = prefs.getInt("profile_port", -1)
        if (savedHost != host.text.toString().trim() || savedPort != connectPort.text.toString().trim().toIntOrNull()) {
            status.text = "Conéctate al mismo dispositivo antes de restaurar"
            output.text = "El perfil se aplicó en " + savedHost + ":" + savedPort + ". Vuelve a ese destino para restaurar sus valores."
            return
        }
        status.text = "Restaurando valores originales…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val failures = mutableListOf<String>()
                    animationKeys.forEach { key ->
                        val hasValue = prefs.getBoolean("profile_has_" + key, false)
                        val value = prefs.getString("profile_value_" + key, "") ?: ""
                        val command = if (hasValue) "settings put global " + key + " " + value else "settings delete global " + key
                        val response = current.shell(command)
                        if (response.exitCode != 0) failures += key + ": " + response.allOutput
                    }
                    if (failures.isNotEmpty()) throw IllegalStateException("Restauración parcial. Reintenta: " + failures.joinToString("; "))
                    prefs.edit().putBoolean("profile_saved", false).apply()
                    "Se restauraron los valores anteriores de las tres escalas de animación."
                }
            }
            result.onSuccess { status.text = "VALORES RESTAURADOS"; output.text = it }
                .onFailure { status.text = "No se completó la restauración"; output.text = it.message ?: "Error desconocido" }
        }
    }

    private fun sendKey(keyCode: Int) {
        if (keyCode !in setOf(3, 4, 19, 20, 21, 22, 23, 24, 25)) return
        val current = connectedClient() ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val response = current.shell("input keyevent " + keyCode)
                    if (response.exitCode != 0) throw IllegalStateException(response.allOutput)
                    response.allOutput
                }
            }
            result.onSuccess { status.text = "Tecla enviada: " + keyCode; output.text = "El dispositivo recibió el evento de control remoto." }
                .onFailure { status.text = "No se pudo enviar la tecla: " + (it.message ?: "error") }
        }
    }

    private fun disconnect() {
        client?.close()
        client = null
        status.text = "Desconectado"
        output.text = "La sesión ADB se cerró. La identidad del host permanece guardada para futuras conexiones."
        continueButton.visibility = View.GONE
    }

    override fun onDestroy() {
        client?.close()
        client = null
        scope.cancel()
        super.onDestroy()
    }
}
