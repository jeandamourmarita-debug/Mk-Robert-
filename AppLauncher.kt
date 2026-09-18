package com.mkrobot.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import java.util.Locale

/**
 * Finds installed apps and launches them by a spoken name, matching either
 * the app's real label ("WhatsApp") or a handful of common Kinyarwanda
 * aliases people actually say ("wasap", "yutubu").
 */
class AppLauncher(private val context: Context) {

    private val aliases = mapOf(
        "youtube" to listOf("yutubu", "you tube"),
        "whatsapp" to listOf("wasap", "whatsap", "what's app"),
        "camera" to listOf("kamera"),
        "settings" to listOf("igenamiterere"),
        "facebook" to listOf("fesibuku", "face book"),
        "gmail" to listOf("g mail"),
        "phone" to listOf("telefoni", "ijwi", "dialer"),
        "messages" to listOf("ubutumwa", "sms"),
        "gallery" to listOf("amafoto", "photos")
    )

    /** Returns the list of (label, packageName) for every launchable app,
     *  cached by the caller — this call itself does the PackageManager scan. */
    fun getLaunchableApps(): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .map { pm.getApplicationLabel(it.applicationInfo).toString() to it.packageName }
            .distinctBy { it.second }
    }

    /**
     * Attempts to launch the app whose label or alias best matches
     * [spokenName]. Returns true if an app was found and launched.
     */
    fun launchByName(spokenName: String): Boolean {
        val query = spokenName.trim().lowercase(Locale.getDefault())
        if (query.isEmpty()) return false

        val apps = getLaunchableApps()

        // 1. Exact or "contains" match against the real app label.
        val labelMatch = apps.firstOrNull { (label, _) ->
            val l = label.lowercase(Locale.getDefault())
            l == query || l.contains(query) || query.contains(l)
        }
        if (labelMatch != null) return launchPackage(labelMatch.second)

        // 2. Match against known Kinyarwanda aliases, then find that app by label.
        val canonicalName = aliases.entries.firstOrNull { (_, aliasList) ->
            aliasList.any { query.contains(it) }
        }?.key

        if (canonicalName != null) {
            val byAlias = apps.firstOrNull {
                it.first.lowercase(Locale.getDefault()).contains(canonicalName)
            }
            if (byAlias != null) return launchPackage(byAlias.second)
        }

        return false
    }

    private fun launchPackage(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    private fun isSystemApp(info: ApplicationInfo): Boolean =
        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
}
