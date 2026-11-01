package com.liamliz.tvhack

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.*

class MainActivity : Activity() {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
 private lateinit var host: EditText
 private lateinit var connectPort: EditText
 private lateinit var pairPort: EditText
 private lateinit var pairCode: EditText
 private lateinit var status: TextView
 private lateinit var info: TextView
 private lateinit var continueButton: Button
 private var client: Kadb? = null

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("connection", MODE_PRIVATE)
  val root = LinearLayout(this).apply { orientation=1; setPadding(24,24,24,24); setBackgroundColor(0xFF0F172A.toInt()) }
  fun label(s:String, size:Float=15f)=TextView(this).apply { text=s; textSize=size; setTextColor(0xFFE2E8F0.toInt()); setPadding(0,12,0,6) }
  fun field(h:String,v:String,n:Boolean=false)=EditText(this).apply {
   hint=h; setText(v); textSize=16f; setSingleLine(true); setTextColor(-1); setHintTextColor(0xFF94A3B8.toInt())
   setPadding(14,12,14,12); setBackgroundColor(0xFF1E293B.toInt()); if(n) inputType=InputType.TYPE_CLASS_NUMBER
   layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=6 }
  }
  fun button(s:String, f:()->Unit)=Button(this).apply { text=s; isAllCaps=false; setOnClickListener { f() }; layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=7 } }
  root.addView(label("LIAMLIZ TV HACK",27f))
  root.addView(label("Conecta con cualquier dispositivo Android/Android TV. La IP y los puertos son configurables."))
  root.addView(label("IP del dispositivo"))
  host=field("Ej. 192.168.1.100",prefs.getString("host","") ?: "")
  connectPort=field("Puerto ADB de conexión (no el de pairing)",prefs.getString("port","5555") ?: "",true)
  root.addView(host); root.addView(connectPort)
  root.addView(label("Vinculación inalámbrica (si se solicita)"))
  pairPort=field("Puerto temporal de vinculación",prefs.getString("pairPort","") ?: "",true)
  pairCode=field("Código de vinculación de 6 dígitos","",true)
  root.addView(pairPort); root.addView(pairCode)
  root.addView(button("1 · Vincular dispositivo",::pairDevice))
  root.addView(button("Conectar / reintentar",::connectDevice))
  status=label("Estado: sin conexión",17f); root.addView(status)
  info=label("Introduce la IP y los datos que muestra la pantalla de depuración inalámbrica."); root.addView(info)
  continueButton=button("CONTINUAR · Ya autoricé ADB",::connectDevice).also { it.visibility=View.GONE }
  root.addView(continueButton)
  root.addView(label("Diagnóstico",20f))
  root.addView(button("Actualizar diagnóstico",::diagnostics))
  root.addView(button("Ver aplicaciones instaladas",::apps))
  root.addView(button("Desconectar",::disconnect))
  root.addView(label("Esta versión solo consulta información. No desinstala apps ni cambia ajustes automáticamente."))
  setContentView(ScrollView(this).apply { addView(root) })
 }

 private fun save() { getSharedPreferences("connection",MODE_PRIVATE).edit().putString("host",host.text.toString().trim()).putString("port",connectPort.text.toString().trim()).putString("pairPort",pairPort.text.toString().trim()).apply() }
 private fun endpoint(raw:String):Pair<String,Int>? {
  val h=host.text.toString().trim(); val p=raw.trim().toIntOrNull()
  if(h.isBlank() || h.any { !(it.isLetterOrDigit() || it=='.' || it==':' || it=='-') }) { status.text="IP o host no válido"; return null }
  if(p==null || p !in 1..65535) { status.text="Puerto no válido"; return null }
  return h to p
 }
 private fun pairDevice() {
  val ep=endpoint(pairPort.text.toString()) ?: return
  val code=pairCode.text.toString().trim()
  if(!code.matches(Regex("\\d{6}"))) { status.text="Introduce el código de 6 dígitos"; return }
  save(); status.text="Vinculando… mantén abierta la pantalla de pairing."
  scope.launch {
   val result=withContext(Dispatchers.IO) { runCatching { Kadb.pair(ep.first,ep.second,code) } }
   result.onSuccess {
    status.text="Vinculación completada. Pulsa CONTINUAR para conectar."
    info.text="Ahora usa el puerto de conexión ADB que aparece en la pantalla principal de Depuración inalámbrica. Es diferente al puerto de vinculación."
    continueButton.visibility=View.VISIBLE
   }.onFailure { status.text="No se pudo vincular: " + (it.message ?: "error"); info.text="Revisa IP, puerto y código; ambos equipos deben estar en la misma red." }
  }
 }
 private fun connectDevice() {
  val ep=endpoint(connectPort.text.toString()) ?: return
  save(); continueButton.visibility=View.GONE; status.text="Conectando a " + ep.first + ":" + ep.second + "…"
  scope.launch {
   val result=withContext(Dispatchers.IO) { runCatching {
    val c=Kadb.create(ep.first,ep.second,connectTimeout=15000,socketTimeout=15000)
    val model=try { c.shell("getprop ro.product.model").allOutput.trim().ifBlank { "Modelo desconocido" } } catch(e:Throwable) { c.close(); throw e }
    c to model
   } }
   result.onSuccess { pair ->
    client?.close(); client=pair.first; status.text="CONECTADO · " + pair.second
    info.text="ADB verificado con " + ep.first + ":" + ep.second
    continueButton.visibility=View.GONE
   }.onFailure { err ->
    status.text="Conexión pendiente: " + (err.message ?: "sin respuesta")
    info.text="Si el dispositivo muestra «¿Permitir depuración ADB?», acepta la huella RSA en el dispositivo. Después vuelve aquí y pulsa CONTINUAR. Si solicita pairing, usa el puerto temporal y el código de 6 dígitos."
    continueButton.visibility=View.VISIBLE
   }
  }
 }
 private fun runRead(command:String, title:String) {
  val c=client
  if(c==null || !c.connectionCheck()) { status.text="Conecta primero un dispositivo"; return }
  status.text="Consultando…"
  scope.launch {
   val result=withContext(Dispatchers.IO) { runCatching { c.shell(command).allOutput } }
   result.onSuccess { status.text="Consulta completada"; info.text=title + "\n" + it }
    .onFailure { status.text="Error: " + (it.message ?: "consulta fallida") }
  }
 }
 private fun diagnostics()=runRead("getprop ro.product.model; getprop ro.build.version.release; cat /proc/meminfo | head -5; df -h /data","Diagnóstico")
 private fun apps()=runRead("pm list packages -3","Aplicaciones de terceros")
 private fun disconnect() { client?.close(); client=null; status.text="Desconectado"; info.text="La sesión ADB se cerró."; continueButton.visibility=View.GONE }
 override fun onDestroy() { client?.close(); scope.cancel(); super.onDestroy() }
}