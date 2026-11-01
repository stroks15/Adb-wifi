package com.liamliz.tvhack

/**
 * Conservative offline advisor for generic Android TV devices.
 * It interprets diagnostic output only; it never executes commands or changes settings.
 */
object AiOptimizationAdvisor {
    fun analyze(report: String): List<String> {
        val advice = mutableListOf<String>()
        val lower = report.lowercase()
        val memAvailableKb = Regex("MemAvailable:\\s*(\\d+)\\s*kB", RegexOption.IGNORE_CASE)
            .find(report)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (memAvailableKb != null) {
            val mb = memAvailableKb / 1024
            when {
                mb < 180 -> advice += "Memoria crítica (${mb} MB disponibles): revisa apps en segundo plano y evita launchers pesados."
                mb < 400 -> advice += "Memoria ajustada (${mb} MB): prueba un launcher ligero y limita apps de inicio una por una."
                else -> advice += "Memoria disponible: ${mb} MB. No se justifica limpiar RAM agresivamente; Android administra la memoria."
            }
        } else advice += "No se pudo leer MemAvailable; confirma /proc/meminfo antes de aplicar cambios."

        if (lower.contains("thermal status: 4") || lower.contains("thermal status: 5") ||
            lower.contains("severity=4") || lower.contains("severity=5")) {
            advice += "Hay señales de temperatura elevada: mejora ventilación y revisa fuente de alimentación."
        }
        val pingTimes = Regex("time[=<]([0-9.]+)\\s*ms", RegexOption.IGNORE_CASE)
            .findAll(report).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList()
        if (pingTimes.isNotEmpty()) {
            val avg = pingTimes.average()
            when {
                avg > 120 -> advice += "Latencia alta (promedio ${"%.0f".format(java.util.Locale.US, avg)} ms): acerca el TV al router y prueba 5 GHz si la señal es estable."
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
        advice += "Evita tweaks universales de DNS, kernel, GPU o servicios: en firmware genérico pueden empeorar estabilidad, Wi-Fi, DRM o actualizaciones."
        return advice.distinct()
    }
}
