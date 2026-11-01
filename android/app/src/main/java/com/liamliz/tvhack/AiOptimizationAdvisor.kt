package com.liamliz.tvhack

/**
 * Conservative offline advisor for Android TV, Google TV, Fire TV and generic AOSP boxes.
 * It only interprets diagnostics; it never executes commands or changes device settings.
 */
object AiOptimizationAdvisor {
    private fun value(report: String, key: String): String? {
        val explicit = Regex("(?im)^\\s*${Regex.escape(key)}\\s*[:=]\\s*(.+)$")
            .find(report)?.groupValues?.getOrNull(1)?.trim()
        if (!explicit.isNullOrBlank()) return explicit
        return Regex("(?im)^===\\s*${Regex.escape(key)}\\s*===\\s*\\r?\\n([^\\r\\n]+)")
            .find(report)?.groupValues?.getOrNull(1)?.trim()
    }

    fun analyze(report: String): List<String> {
        val advice = mutableListOf<String>()
        val lower = report.lowercase()
        val manufacturer = value(report, "MANUFACTURER").orEmpty()
        val model = value(report, "MODEL").orEmpty()
        val display = value(report, "DISPLAY").orEmpty()
        val fingerprintValue = value(report, "FINGERPRINT").orEmpty()
        val identity = listOf(manufacturer, model, display, fingerprintValue, report).joinToString(" ").lowercase()
        val sdk = value(report, "SDK")?.toIntOrNull()
        advice += when {
            identity.contains("fire tv") || identity.contains("amazon") || Regex("\\baft[a-z0-9]*\\b").containsMatchIn(identity) ->
                "Perfil detectado: Amazon Fire TV/Fire OS. ADB y pairing dependen del modelo y versión; prioriza conexión TCP si ese firmware no ofrece pairing."
            identity.contains("google tv") ->
                "Perfil detectado: Google TV. Conserva los componentes de Google TV y verifica HOME y el control remoto antes de cambiar el launcher."
            identity.contains("android tv") || identity.contains("bravia") || identity.contains("sony") ||
                identity.contains("tcl") || identity.contains("hisense") || identity.contains("xiaomi") ->
                "Perfil probable: Android TV de fabricante. Las restricciones varían por marca y firmware."
            else -> "Perfil AOSP/TV Box o fabricante no identificado. No se presuponen root, permisos ni rutas específicas."
        }
        if (sdk != null) advice += "Android API detectada: $sdk. La disponibilidad de pairing depende de la versión y del firmware."

        val totalKb = Regex("MemTotal:\\s*(\\d+)\\s*kB", RegexOption.IGNORE_CASE)
            .find(report)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val availableKb = Regex("MemAvailable:\\s*(\\d+)\\s*kB", RegexOption.IGNORE_CASE)
            .find(report)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (totalKb != null) {
            val totalMb = totalKb / 1024
            advice += when {
                totalMb < 1200 -> "RAM instalada aproximada: $totalMb MB. Prioriza apps ligeras y evita multitarea/servicios residentes."
                totalMb < 2500 -> "RAM instalada aproximada: $totalMb MB. Perfil de recursos limitados; mide consumo antes de añadir servicios."
                else -> "RAM instalada aproximada: $totalMb MB. No apliques límites agresivos de procesos sin medir."
            }
        }
        if (availableKb != null) {
            val mb = availableKb / 1024
            advice += when {
                mb < 180 -> "Memoria crítica ($mb MB disponibles): revisa apps en segundo plano y evita launchers pesados."
                mb < 400 -> "Memoria ajustada ($mb MB): prueba cambios de uno en uno y mide estabilidad."
                else -> "Memoria disponible: $mb MB. Android administra memoria; no se justifica limpiarla agresivamente."
            }
        } else advice += "MemAvailable no está disponible; no se puede estimar la presión de memoria con fiabilidad."

        val thermalStatus = Regex("(?i)(?:thermal status|current thermal status)\\s*[:=]\\s*(\\d+)")
            .find(report)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val thermalSeverity = Regex("(?i)severity\\s*[=:]\\s*(\\d+)")
            .find(report)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if ((thermalStatus ?: thermalSeverity ?: 0) >= 4) {
            advice += "Hay señales de temperatura elevada: mejora ventilación y revisa la fuente de alimentación."
        } else if (!lower.contains("thermal")) {
            advice += "El servicio térmico no entregó datos legibles; no se puede descartar calentamiento."
        }

        val pingTimes = Regex("time[=<]([0-9.]+)\\s*ms", RegexOption.IGNORE_CASE)
            .findAll(report).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList()
        if (pingTimes.isNotEmpty()) {
            val avg = pingTimes.average()
            val formatted = "%.0f".format(java.util.Locale.US, avg)
            advice += when {
                avg > 120 -> "Latencia alta (promedio $formatted ms): acerca el equipo al router y compara 5 GHz si la señal es estable."
                avg > 50 -> "Latencia moderada ($formatted ms): compara bandas y congestión del canal."
                else -> "Latencia a Internet correcta ($formatted ms). Si el control va lento, revisa launcher o carga del sistema."
            }
        } else advice += "No hubo respuesta de ping o el comando no está disponible; esto no demuestra que el Wi-Fi esté desconectado."

        if (lower.contains("com.google.android.tvlauncher") || lower.contains("com.android.tvlauncher")) {
            advice += "Se detectó un launcher de Android TV. Prueba otro compatible y verifica HOME y el control remoto antes de sustituirlo."
        } else if (lower.contains("launcher")) {
            advice += "Revisa el paquete HOME detectado. No deshabilites el launcher original sin una vía de recuperación."
        } else advice += "No se identificó claramente el launcher predeterminado; conserva el actual hasta confirmar su paquete HOME."

        advice += "El perfil reversible 0.5x puede reducir esperas visuales, pero no aumenta la potencia del procesador."
        advice += "No desactives servicios, DRM, actualizaciones ni componentes del fabricante de forma universal."
        advice += "Evita tweaks universales de DNS, kernel o GPU: pueden empeorar estabilidad, Wi-Fi y streaming."
        return advice.distinct()
    }
}
