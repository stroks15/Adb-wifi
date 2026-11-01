package com.liamliz.tvhack

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 24, 28, 24)
            setBackgroundColor(0xFF0F172A.toInt())
        }
        root.addView(TextView(this).apply {
            text = "LIAMLIZ TV HACK"
            textSize = 28f
            setTextColor(0xFF38BDF8.toInt())
            setPadding(0, 0, 0, 22)
        })
        root.addView(TextView(this).apply {
            text = "Aplicaciones instaladas"
            textSize = 18f
            setTextColor(0xFFE2E8F0.toInt())
        })
        val apps = installedLaunchableApps()
        if (apps.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No se encontraron aplicaciones iniciables."
                setTextColor(0xFFCBD5E1.toInt())
            })
        } else {
            apps.forEach { (label, packageName) ->
                root.addView(Button(this).apply {
                    text = label
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnClickListener { launchPackage(packageName) }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 8 }
                })
            }
        }
        setContentView(root)
    }

    @Suppress("DEPRECATION")
    private fun installedLaunchableApps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val leanback = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val candidates = if (leanback.isNotEmpty()) leanback else {
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL
            )
        }
        return candidates
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString() ?: packageName
                label to packageName
            }
            .distinctBy { it.second }
            .filter { it.second != packageName }
            .sortedBy { it.first.lowercase() }
    }

    private fun launchPackage(packageName: String) {
        val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "No hay actividad de inicio para esta aplicación.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(launchIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir la aplicación.", Toast.LENGTH_SHORT).show()
        }
    }
}
