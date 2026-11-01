package com.liamliz.tvhack

/**
 * Conservative, device-aware offline advisor for Android TV, Google TV, Fire TV and AOSP boxes.
 * It interprets diagnostic output only; it never executes commands or changes settings.
 */
object AiOptimizationAdvisor {
    fun analyze(report: String): List<String> {
        val advice = mutableListOf<String>()
        val lower = report.lowercase()
        val manufacturer = Regex("(?im)^MANUFACTURER\\s*[:=]\\s*(.+)$").find(report)?.groupValues?.get(1)?.trim().orEmpty()
        val model = Regex("(?im)^MODEL\\s*[:=]\\s*(.+)$").find(report)?.groupValues?.get(1)?.trim().orEmpty()
        val fingerprint = listOf(manufacturer, model, report).joinToString(" ").lowercase()
        val sdk = Regex("(?im)^SDK\\s*[:=]\\s*(\\d+)").find(report)?.groupValues?.get(1)?.toIntOrNull()
        val platform = when {
            fingerprint.contains("fire tv") || fingerprint.contains("amazon") || fingerprint.contains("aft") ->
                "Perfil detectado: Amazon Fire TV/Fire OS. Algunas funciones ADB y menús de depuración cambian según la versión de Fire OS; usa el método de conexión que ofrezca ese modelo."
            fingerprint.contains("google tv") ->
                "Perfil detectado: Google TV. Conserva los componentes de Google TV y verifica compatibilidad del control remoto antes de cambiar el launcher."
            fingerprint.contains("android tv") || fingerprint.contains("bravia") || fingerprint.contains("sony") ||
                fingerprint.contains("tcl") || fingerprint.contains("hisense") || fingerprint.contains("xiaomi") ->
                "Perfil probable: Android TV de fabricante. Las restricciones de sistema y permisos pueden variar por marca y firmware."
            else ->
                "Perfil AOSP/TV Box o fabricante no identificado. No se asumirán permisos root, servicios ni rutas específicas del fabricante."
        }
        advice += platform
        if (sdk != null) advice += "Android API detectada: $sdk. Las funciones se deben habilitar según versión; no se presupone que todos los dispositivos admitan pairing inalámbrico."

        val totalKb = Regex("MemTotal:\\s*(\\d+)\\s*kB", RegexOption.IGNORE_CASE)
            .find(report)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val memAvailableKb = Regex("MemAvailable:\\s*(\\d+)\\s*kB", RegexOption.IGNORE_CASE)
            .find(report)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (totalKb != null) {
            val totalMb = totalKb / 1024
            advice += when {
                totalMb < 1200 -> "RAM instalada aproximada: $totalMb MB. Prioriza apps ligeras; evita multitarea y launchers con servicios residentes."
                totalMb < 2500 -> "RAM instalada aproximada: $totalMb MB. Perfil de recursos limitados: compara consumo real antes de añadir servicios."
                else -> "RAM instalada aproximada: $totalMb MB. No apliques límites agresivos de procesos sin medir primero."
            }
        }
        if (memAvailableKb != null) {
            val mb = memAvailableKb / 1024
            when {
                mb < 180 -> advice += "Memoria crítica ($mb MB disponibles): revisa apps en segundo plano y evita launchers pesados."
                mb < 400 -> advice += "Memoria ajustada ($mb MB): prueba un launcher ligero y limita apps de inicio una por una."
                else -> advice += "Memoria disponible: $mb MB. No se justifica limpiar RAM agresivamente; Android administra la memoria."
            }
        } else advice += "MemAvailable no está disponible en este firmware; no se puede estimar la presión de memoria con fiabilidad."

        if (lower.contains("thermal status: 4") || lower.contains("thermal status: 5") ||
            lower.contains("severity=4") || lower.contains("severity=5")) {
            advice += "Hay señales de temperatura elevada: mejora ventilación y revisa fuente de alimentación."
        } else if (!lower.contains("thermal")) {
            advice += "El servicio térmico no entregó datos legibles; no se puede descartar calentamiento."
        }

        val pingTimes = Regex("time[=<]([0-9.]+)\\s*ms", RegexOption.IGNORE_CASE)
            .findAll(report).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList()
        if (pingTimes.isNotEmpty()) {
            val avg = pingTimes.average()
            when {
                avg > 120 -> advice += "Latencia alta (promedio ${"%.0f".format(java.util.Locale.US, avg)} ms): acerca el equipo al router y prueba 5 GHz si la señal es estable."
                avg > 50 -> advice += "Latencia moderada (${"%.0f".format(java.util.Locale.US, avg)} ms): compara bandas 2.4/5 GHz y congestión del canal."
                else -> advice += "Latencia a Internet correcta (${"%.0f".format(java.util.Locale.US, avg)} ms). Si el control va lento, revisa launcher o carga del sistema."
            }
        } else advice += "No hubo respuesta de ping o el comando no está disponible; esto no demuestra por sí solo que el Wi-Fi esté desconectado."

        if (lower.contains("com.google.android.tvlauncher") || lower.contains("com.android.tvlauncher")) {
            advice += "Se detectó un launcher de Android TV. Prueba otro compatible antes de sustituirlo y verifica HOME y el control remoto."
        } else if (lower.contains("launcher")) {
            advice += "Revisa el paquete HOME detectado. No deshabilites el launcher original hasta probar otro y confirmar recuperación."
        } else advice += "No se identificó claramente el launcher predeterminado; conserva el actual hasta confirmar su paquete HOME."

        advice += "El perfil reversible de animaciones a 0.5x puede reducir esperas visuales, pero no aumenta la potencia real del procesador."
        advice += "No se desactivarán servicios, apps del sistema, DRM, actualizaciones ni componentes del fabricante de forma universal."
        advice += "Evita tweaks universales de DNS, kernel, GPU o servicios: pueden empeorar estabilidad, Wi-Fi, streaming o actualizaciones."
        return advice.distinct()
    }
}
