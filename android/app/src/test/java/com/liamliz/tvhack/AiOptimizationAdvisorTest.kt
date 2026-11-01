package com.liamliz.tvhack

import org.junit.Assert.assertTrue
import org.junit.Test

class AiOptimizationAdvisorTest {
    @Test fun flagsCriticallyLowAvailableMemory() {
        val advice = AiOptimizationAdvisor.analyze("MemAvailable: 120000 kB")
        assertTrue(advice.any { it.contains("Memoria crítica") })
    }

    @Test fun recognizesHighNetworkLatency() {
        val advice = AiOptimizationAdvisor.analyze("time=180.4 ms\\ntime=140.0 ms")
        assertTrue(advice.any { it.contains("Latencia alta") })
    }

    @Test fun warnsAboutThermalAndPreservesLauncher() {
        val advice = AiOptimizationAdvisor.analyze("Thermal status: 5\\ncom.google.android.tvlauncher")
        assertTrue(advice.any { it.contains("temperatura elevada") })
        assertTrue(advice.any { it.contains("launcher de Android TV") })
    }

    @Test fun identifiesFireTvAndLowMemoryClassFromLabeledSections() {
        val report = "=== MANUFACTURER ===\\nAmazon\\n=== MODEL ===\\nAFTMM\\n=== SDK ===\\n30\\nMemTotal: 1500000 kB"
        val advice = AiOptimizationAdvisor.analyze(report)
        assertTrue(advice.any { it.contains("Fire TV/Fire OS") })
        assertTrue(advice.any { it.contains("RAM instalada aproximada") && it.contains("recursos limitados") })
    }

    @Test fun identifiesGoogleTv() {
        val advice = AiOptimizationAdvisor.analyze("=== DISPLAY ===\\nGoogle TV build")
        assertTrue(advice.any { it.contains("Google TV") })
    }

    @Test fun identifiesGenericAospWithoutAssumingRoot() {
        val advice = AiOptimizationAdvisor.analyze("MANUFACTURER: Unknown\\nMODEL: Generic Box")
        assertTrue(advice.any { it.contains("Perfil AOSP/TV Box") })
        assertTrue(advice.any { it.contains("No se presuponen root") })
    }

    @Test fun doesNotAssumeMissingPingMeansWifiIsDisconnected() {
        val advice = AiOptimizationAdvisor.analyze("MemAvailable: 800000 kB")
        assertTrue(advice.any { it.contains("no demuestra") })
    }
}
