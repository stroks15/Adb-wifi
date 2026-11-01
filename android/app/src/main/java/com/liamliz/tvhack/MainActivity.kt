package com.liamliz.tvhack

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

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
    private var identityReady = false
    private var nsdManager: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val discovered = linkedMapOf<String, Pair<String, Int>>()
    private lateinit var discoveredView: TextView
    private lateinit var discoveredList: LinearLayout
    private var selectedEndpoint: Pair<String, Int>? = null
    private lateinit var pageConnection: LinearLayout
    private lateinit var pageDiagnostics: LinearLayout
    private lateinit var pageTools: LinearLayout
    private val stepButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("liamliz_connection", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 24)
            setBackgroundColor(0xFF06110C.toInt())
        }
        fun heading(value: String, size: Float = 15f, color: Int = 0xFFA6B8AA.toInt()) = TextView(this).apply {
            text = value; textSize = size; setTextColor(color); setPadding(0, 8, 0, 8)
        }
        fun field(hintText: String, value: String, numeric: Boolean = false) = EditText(this).apply {
            hint = hintText; setText(value); textSize = 16f; setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF789080.toInt())
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            setPadding(14, 12, 14, 12); setBackgroundColor(0xFF10251A.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
        }
        fun action(label: String, primary: Boolean = false, callback: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; textSize = 14f; setOnClickListener { callback() }
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) 0xFF56F58A.toInt() else 0xFF183425.toInt())
            setTextColor(if (primary) 0xFF031409.toInt() else 0xFFE6F5EA.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 5; bottomMargin = 5 }
        }
        fun panel(title: String, detail: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 14); setBackgroundColor(0xFF0C1D14.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
            addView(heading(title, 18f, 0xFF56F58A.toInt())); addView(heading(detail, 13f))
        }

        root.addView(heading("LIAMLIZ  /  TV HACK", 26f, 0xFF56F58A.toInt()))
        root.addView(heading("ADB CONTROL CENTER  •  GREEN NEON", 11f))
        root.addView(heading("Flujo guiado en tres pasos. Primero conecta, después diagnostica y finalmente usa las herramientas.", 13f))
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("1 · CONECTAR", "2 · DIAGNÓSTICO", "3 · HERRAMIENTAS").forEachIndexed { index, label ->
            val button = Button(this).apply {
                text = label; isAllCaps = false; textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(2, 5, 2, 10) }
                setOnClickListener { showStep(index) }
            }
            stepButtons.add(button); navigation.addView(button)
        }
        root.addView(navigation)

        pageConnection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val manual = panel("01  CONEXIÓN MANUAL", "Para ADB TCP clásico introduce la IP y el puerto 5555. Para depuración inalámbrica Android 11+ usa el puerto TLS que aparece en el destino.")
        host = field("IP del dispositivo (ej. 192.168.0.125)", prefs.getString("host", "192.168.0.125") ?: "192.168.0.125")
        connectPort = field("Puerto ADB de conexión (ej. 5555)", prefs.getString("connect_port", "5555") ?: "5555", true)
        manual.addView(host); manual.addView(connectPort)
        manual.addView(action("CONECTAR A ESTA IP", true, ::connectDevice))
        pageConnection.addView(manual)

        val scanner = panel("02  BUSCAR EQUIPOS EN WI-FI", "Los dispositivos detectados aparecen como tarjetas seleccionables. La app nunca escogerá automáticamente el primer resultado.")
        scanner.addView(action("⌁  ESCANEAR SERVICIOS ADB", true, ::scanWifi))
        discoveredView = heading("Escáner inactivo. Inicia una búsqueda.", 13f, 0xFF56F58A.toInt())
        scanner.addView(discoveredView)
        discoveredList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scanner.addView(discoveredList)
        scanner.addView(action("CONECTAR EQUIPO SELECCIONADO", true, ::connectDiscovered))
        pageConnection.addView(scanner)

        val pairing = panel("03  VINCULACIÓN SEGURA · ANDROID 11+", "En el dispositivo abre Depuración inalámbrica → Vincular dispositivo con código. El puerto temporal y el puerto de conexión son distintos.")
        pairPort = field("Puerto temporal de vinculación", prefs.getString("pair_port", "") ?: "", true)
        pairCode = field("Código de vinculación de 6 dígitos", "")
        pairing.addView(pairPort); pairing.addView(pairCode)
        pairing.addView(action("VINCULAR DISPOSITIVO", false, ::pairDevice))
        pageConnection.addView(pairing)

        pageDiagnostics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val diagnosticPanel = panel("DIAGNÓSTICO DEL EQUIPO", "Consultas de solo lectura para comprobar modelo, Android, memoria, almacenamiento y pantalla.")
        diagnosticPanel.addView(action("EJECUTAR DIAGNÓSTICO COMPLETO", true, ::diagnostics))
        diagnosticPanel.addView(action("VER APLICACIONES INSTALADAS", false, ::installedApps))
        diagnosticPanel.addView(action("ANÁLISIS INTELIGENTE DE RENDIMIENTO + WI-FI", true, ::smartAnalysis))
        diagnosticPanel.addView(action("MEDIR CONEXIÓN WI-FI", false, ::wifiDiagnostics))
        pageDiagnostics.addView(diagnosticPanel)

        pageTools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val optimizer = panel("OPTIMIZACIÓN SEGURA", "Perfil reversible: cambia únicamente las escalas de animación a 0.5x y guarda los valores anteriores.")
        optimizer.addView(action("APLICAR PERFIL 0.5X", true, ::applySafeProfile))
        optimizer.addView(action("RESTAURAR VALORES ANTERIORES", false, ::restoreSafeProfile))
        pageTools.addView(optimizer)
        val remote = panel("CONTROL REMOTO", "Envía navegación, OK, atrás e inicio al equipo conectado.")
        addDpad(remote, ::sendKey)
        remote.addView(action("DESCONECTAR", false, ::disconnect))
        pageTools.addView(remote)

        status = heading("🟡 PREPARANDO IDENTIDAD ADB…", 16f, 0xFF56F58A.toInt())
        output = heading("Todavía no existe una sesión verificada.", 13f)
        continueButton = action("REINTENTAR DESPUÉS DE AUTORIZAR", true, ::connectDevice).apply { visibility = View.GONE }
        val footer = panel("ESTADO DE CONEXIÓN", "La aplicación confirma la sesión ejecutando una consulta real al dispositivo.")
        footer.addView(status); footer.addView(output); footer.addView(continueButton)

        root.addView(pageConnection); root.addView(pageDiagnostics); root.addView(pageTools); root.addView(footer)
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        showStep(0)
        initializeAdbIdentity()
    }

    private fun showStep(step: Int) {
        pageConnection.visibility = if (step == 0) View.VISIBLE else View.GONE
        pageDiagnostics.visibility = if (step == 1) View.VISIBLE else View.GONE
        pageTools.visibility = if (step == 2) View.VISIBLE else View.GONE
        stepButtons.forEachIndexed { index, button ->
            val active = index == step
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(if (active) 0xFF56F58A.toInt() else 0xFF183425.toInt())
            button.setTextColor(if (active) 0xFF031409.toInt() else 0xFFE6F5EA.toInt())
        }
    }

    private fun scanWifi() {
        val manager = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = manager
        discovered.clear()
        selectedEndpoint = null
        discoveredList.removeAllViews()
        discoveredView.text = "🔎 Buscando servicios ADB…"
        runCatching {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("liamliz-adb-discovery").apply { setReferenceCounted(false); acquire() }
        }
        // Pairing endpoints are intentionally excluded: they require a PIN and are not connect ports.
        val types = listOf("_adb._tcp.", "_adb-tls-connect._tcp.")
        types.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("adb", true)) {
                        manager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val address = info.host?.hostAddress ?: return
                                val port = info.port
                                synchronized(discovered) { discovered[info.serviceName] = address to port }
                                runOnUiThread { renderDiscovered() }
                            }
                        })
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    synchronized(discovered) { discovered.remove(service.serviceName) }
                    runOnUiThread { discoveredView.text = if (discovered.isEmpty()) "El servicio ADB dejó de anunciarse." else discovered.entries.joinToString("\n") { "• ${it.key}  ${it.value.first}:${it.value.second}" } }
                }
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { runCatching { manager.stopServiceDiscovery(this) } }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { runCatching { manager.stopServiceDiscovery(this) } }
            }
            discoveryListeners += listener
            runCatching { manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
    }

    private fun renderDiscovered() {
        discoveredList.removeAllViews()
        val entries = synchronized(discovered) { discovered.entries.map { Triple(it.key, it.value.first, it.value.second) } }
        discoveredView.text = if (entries.isEmpty()) "Buscando dispositivos…" else entries.size.toString() + " equipos encontrados. Toca el correcto para seleccionarlo."
        entries.forEach { item ->
            val endpoint = item.second to item.third
            val selected = selectedEndpoint == endpoint
            val option = Button(this).apply {
                text = (if (selected) "✓ SELECCIONADO  " else "○ SELECCIONAR  ") + item.first + "\n" + item.second + ":" + item.third
                isAllCaps = false
                setOnClickListener {
                    selectedEndpoint = endpoint
                    host.setText(item.second)
                    connectPort.setText(item.third.toString())
                    renderDiscovered()
                    status.text = "Equipo seleccionado: " + item.second + ":" + item.third
                    output.text = "Confirma que sea tu equipo y pulsa CONECTAR EQUIPO SELECCIONADO."
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 5 }
            }
            discoveredList.addView(option)
        }
    }

    private fun connectDiscovered() {
        val endpoint = selectedEndpoint
        if (endpoint == null) {
            status.text = "Selecciona primero un equipo"
            output.text = "La app no elegirá el primer resultado automáticamente. Toca el dispositivo correcto en la lista."
            return
        }
        host.setText(endpoint.first)
        connectPort.setText(endpoint.second.toString())
        connectDevice()
    }

    private fun initializeAdbIdentity() {
        status.text = "Preparando identidad ADB segura…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val identityPrefs = getSharedPreferences("adb_identity", MODE_PRIVATE)
                    val cert = identityPrefs.getString("certificate", null)
                    val key = identityPrefs.getString("private_key", null)
                    if (cert != null && key != null) {
                        KadbCert.set(
                            Base64.decode(cert, Base64.DEFAULT),
                            Base64.decode(key, Base64.DEFAULT)
                        )
                    } else {
                        val pair = KadbCert.get()
                        check(identityPrefs.edit()
                            .putString("certificate", Base64.encodeToString(pair.first, Base64.NO_WRAP))
                            .putString("private_key", Base64.encodeToString(pair.second, Base64.NO_WRAP))
                            .commit()) { "No se pudo guardar la identidad ADB." }
                    }
                }
            }
            result.onSuccess {
                identityReady = true
                status.text = "Identidad ADB persistente lista · sin conexión"
                output.text = "La huella del controlador se conservará entre aperturas de la app."
            }.onFailure { error ->
                status.text = "No se pudo preparar la identidad ADB"
                output.text = error.message ?: "Error al crear o cargar las claves ADB. Borra los datos de la app para regenerarlas."
            }
        }
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
        if (!identityReady) {
            status.text = "Espera a que termine la inicialización de identidad ADB"
            return
        }
        val target = endpoint(connectPort.text.toString()) ?: return
        saveConnection()
        continueButton.visibility = View.GONE
        status.text = "🟡 PASO 1/2 · comprobando red hacia " + target.first + ":" + target.second
        output.text = "Primero se comprueba el puerto TCP; después se negocia ADB y se verifica el modelo del dispositivo."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val probe = Socket()
                    try {
                        probe.connect(InetSocketAddress(target.first, target.second), 5000)
                    } catch (networkError: Throwable) {
                        throw IllegalStateException("No se puede abrir TCP hacia " + target.first + ":" + target.second + ". Comprueba IP, puerto, misma Wi-Fi y que ADB esté habilitado. Detalle: " + (networkError.message ?: networkError.javaClass.simpleName), networkError)
                    } finally {
                        runCatching { probe.close() }
                    }
                    val candidate = Kadb.create(target.first, target.second, connectTimeout = 12000, socketTimeout = 12000)
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
                status.text = "🟢 CONECTADO Y VERIFICADO · " + connected.second
                output.text = "Red TCP accesible y protocolo ADB verificado con getprop. Destino: " + target.first + ":" + target.second
                continueButton.visibility = View.GONE
            }.onFailure { error ->
                val detail = error.message ?: "sin respuesta"
                val unauthorized = detail.contains("unauthorized", true) || detail.contains("auth", true)
                status.text = if (unauthorized) "🟠 AUTORIZACIÓN ADB PENDIENTE" else "🔴 CONEXIÓN FALLIDA"
                output.text = if (unauthorized) "El destino aún no autorizó esta identidad ADB. Acepta el diálogo RSA en la TV y vuelve a pulsar REINTENTAR. Detalle: $detail" else "La conexión no completó el protocolo ADB. Si TCP respondió, el puerto está abierto pero puede no ser el puerto ADB correcto o puede requerir pairing TLS. Para Android 11+ vincula con el puerto temporal y conecta después al puerto principal. En ADB TCP clásico el destino debe tener adb tcpip 5555 activo. Detalle: $detail"
                continueButton.visibility = View.VISIBLE
            }
        }
    }

    private fun pairDevice() {
        if (!identityReady) {
            status.text = "Espera a que termine la inicialización de identidad ADB"
            return
        }
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


    // Read-only performance and network snapshot. Recommendations are generated locally.
    private fun smartAnalysis() {
        val current = connectedClient() ?: return
        status.text = "Analizando rendimiento, launcher, red y respuesta…"
        val checks = listOf(
            "MANUFACTURER" to "getprop ro.product.manufacturer",
            "MODEL" to "getprop ro.product.model",
            "ANDROID" to "getprop ro.build.version.release",
            "SDK" to "getprop ro.build.version.sdk",
            "DISPLAY" to "getprop ro.build.display.id",
            "FINGERPRINT" to "getprop ro.build.fingerprint",
            "HARDWARE" to "getprop ro.hardware",
            "PRODUCT" to "getprop ro.product.device",
            "CPU" to "dumpsys cpuinfo",
            "MEMORIA" to "cat /proc/meminfo",
            "ALMACENAMIENTO" to "df -h /data",
            "TEMPERATURA" to "dumpsys thermalservice",
            "WIFI" to "dumpsys wifi",
            "RED" to "ip route; ip addr",
            "LATENCIA INTERNET" to "ping -c 4 -W 2 1.1.1.1",
            "LAUNCHER ACTUAL" to "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
            "ANIMACIONES" to "settings get global window_animation_scale; settings get global transition_animation_scale; settings get global animator_duration_scale",
            "REFRESCO" to "settings get system peak_refresh_rate; settings get system min_refresh_rate",
            "RESOLUCION" to "wm size; wm density"
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    buildString {
                        checks.forEach { (name, command) ->
                            val response = current.shell(command)
                            append("=== ").append(name).append(" ===\n")
                                .append(response.allOutput.ifBlank { "(sin salida)" }).append("\n\n")
                        }
                    }
                }
            }
            result.onSuccess { report ->
                val advice = AiOptimizationAdvisor.analyze(report)
                status.text = "ANÁLISIS COMPLETADO · recomendaciones priorizadas"
                output.text = report + "\n=== ASESOR INTELIGENTE LOCAL ===\n" +
                    advice.joinToString("\n") { "• " + it } +
                    "\n\nEste asesor es local y no modifica ajustes. La conexión con un modelo generativo externo requiere configurar un proveedor y una clave API propia."
            }.onFailure { status.text = "Falló el análisis: " + (it.message ?: "error") }
        }
    }

    private fun wifiDiagnostics() {
        val current = connectedClient() ?: return
        status.text = "Midiendo conectividad Wi-Fi…"
        val commands = listOf(
            "Estado Wi-Fi" to "dumpsys wifi", "Ruta y puerta de enlace" to "ip route",
            "Direcciones de red" to "ip addr", "Latencia Internet" to "ping -c 5 -W 2 1.1.1.1"
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    buildString {
                        commands.forEach { (label, command) ->
                            append("=== ").append(label).append(" ===\n")
                            append(current.shell(command).allOutput.ifBlank { "(sin salida)" }).append("\n\n")
                        }
                    }
                }
            }
            result.onSuccess { status.text = "Diagnóstico Wi-Fi completado"; output.text = it }
                .onFailure { status.text = "Error de red: " + (it.message ?: "error") }
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
        val profileHost = host.text.toString().trim()
        val profilePort = connectPort.text.toString().trim().toIntOrNull()
        if (profilePort == null) {
            status.text = "Puerto ADB no válido"
            return
        }
        if (prefs.getBoolean("profile_saved", false)) {
            status.text = "Hay un perfil pendiente de restaurar"
            output.text = "Restaura primero el perfil anterior. Así se conserva el estado original y no se guarda 0.5x como nuevo valor previo."
            return
        }
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
                        .putString("profile_host", profileHost)
                        .putInt("profile_port", profilePort)
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
        discoveryListeners.forEach { listener -> runCatching { nsdManager?.stopServiceDiscovery(listener) } }
        discoveryListeners.clear()
        runCatching { multicastLock?.let { if (it.isHeld) it.release() } }
        multicastLock = null
        client?.close()
        client = null
        scope.cancel()
        super.onDestroy()
    }
}
