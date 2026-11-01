package com.liamliz.tvhack

import org.junit.Assert.assertTrue
import org.junit.Test

class AiOptimizationAdvisorTest {
    @Test
    fun flagsCriticallyLowAvailableMemory() {
        val advice = AiOptimizationAdvisor.analyze("MemAvailable: 120000 kB")
        assertTrue(advice.any { it.contains("Memoria crítica") })
    }

    @Test
    fun recognizesHighNetworkLatency() {
        val advice = AiOptimizationAdvisor.analyze("64 bytes from 1.1.1.1: time=180.4 ms\n64 bytes from 1.1.1.1: time=140.0 ms")
        assertTrue(advice.any { it.contains("Latencia alta") })
    }

    @Test
    fun warnsAboutThermalAndPreservesLauncher() {
        val advice = AiOptimizationAdvisor.analyze("Thermal status: 5\ncom.google.android.tvlauncher")
        assertTrue(advice.any { it.contains("temperatura elevada") })
        assertTrue(advice.any { it.contains("Prueba otro compatible") })
    }

    @Test
    fun identifiesFireTvAndLowMemoryClass() {
        val advice = AiOptimizationAdvisor.analyze(
            "MANUFACTURER: Amazon\\nMODEL: AFTMM\\nSDK: 30\\nMemTotal: 900000 kB"
        )
        assertTrue(advice.any { it.contains("Fire TV/Fire OS") })
        assertTrue(advice.any { it.contains("RAM instalada aproximada") && it.contains("recursos limitados") })
    }

    @Test
    fun doesNotAssumeMissingPingMeansWifiIsDisconnected() {
        val advice = AiOptimizationAdvisor.analyze("MemAvailable: 800000 kB")
        assertTrue(advice.any { it.contains("no demuestra") })
    }
}
