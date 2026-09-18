package com.mkrobot.assistant

import java.util.Locale

/**
 * Turns raw recognized speech (Kinyarwanda or English) into a [Command].
 *
 * This is deliberately a lightweight, rule-based matcher rather than an ML
 * model: it keeps the app fast and fully offline-capable for local phone
 * actions, and only ever falls through to [Command.AskAi] (which needs the
 * network) when nothing local matches.
 */
object CommandParser {

    // Common open-app phrasings: "Fungura X" (rw) / "Open X" (en) / "Injira muri X"
    private val openAppTriggers = listOf("fungura", "injira muri", "open")
    private val callTriggers = listOf("hamagara", "call")
    private val messageTriggers = listOf("andikira", "ohereza ubutumwa", "text", "message")
    private val searchTriggers = listOf("shakisha kuri internet", "shakisha", "search")
    private val alarmTriggers = listOf("shyiraho alarm", "set alarm", "shyiraho igihe cyo kubyuka")
    private val bluetoothOnTriggers = listOf("fungura bluetooth", "turn on bluetooth")
    private val bluetoothOffTriggers = listOf("zimya bluetooth", "turn off bluetooth", "hagarika bluetooth")

    fun parse(rawInput: String): Command {
        val text = rawInput.trim().lowercase(Locale.getDefault())
        if (text.isEmpty()) return Command.Unknown(rawInput)

        // --- Direct navigation / phone actions (no confirmation needed) ---
        if (matchesAny(text, "subiza inyuma", "garuka inyuma", "go back", "back")) {
            return Command.GoBack
        }
        if (matchesAny(text, "jya kuri home", "murugo", "go home", "home")) {
            return Command.GoHome
        }
        if (matchesAny(text, "fungura camera", "open camera", "kamera")) {
            return Command.OpenCamera
        }
        if (matchesAny(text, "fungura settings", "open settings", "igenamiterere")) {
            return Command.OpenSettingsApp
        }
        if (matchesAny(text, "fungura itara", "zimya itara", "flashlight", "torch")) {
            return Command.ToggleFlashlight
        }
        if (matchesAny(text, "ni saa ngahe", "what time is it", "saa ngahe")) {
            return Command.WhatTimeIsIt
        }
        if (matchesAny(text, "ni itariki ngahe", "what is the date", "itariki")) {
            return Command.WhatIsTheDate
        }

        // --- Bluetooth (confirmation required — see Command.requiresConfirmation) ---
        if (matchesAny(text, *bluetoothOffTriggers.toTypedArray())) {
            return Command.ToggleBluetooth(turnOn = false)
        }
        if (matchesAny(text, *bluetoothOnTriggers.toTypedArray())) {
            return Command.ToggleBluetooth(turnOn = true)
        }

        // --- Alarm: "Shyiraho alarm saa kumi" / "Set alarm at 10" ---
        alarmTriggers.firstOrNull { text.contains(it) }?.let { trigger ->
            val remainder = text.substringAfter(trigger).trim()
            parseSpokenTime(remainder)?.let { (h, m) -> return Command.SetAlarm(h, m) }
            // Trigger matched but no time understood — still route to alarm
            // flow so the UI can ask the user to repeat the time.
            return Command.SetAlarm(-1, -1)
        }

        // --- Call: "Hamagara Mama" ---
        callTriggers.firstOrNull { text.startsWith(it) }?.let { trigger ->
            val name = text.removePrefix(trigger).trim()
            if (name.isNotEmpty()) return Command.Call(name)
        }

        // --- Message: "Andikira Mama ubutumwa" ---
        messageTriggers.firstOrNull { text.contains(it) }?.let { trigger ->
            val name = text.substringBefore(trigger).trim()
                .ifEmpty { text.substringAfter(trigger).trim() }
            if (name.isNotEmpty()) return Command.SendMessage(name)
        }

        // --- Web search: "Shakisha amakuru kuri internet" ---
        searchTriggers.firstOrNull { text.contains(it) }?.let { trigger ->
            val query = text.replace(trigger, "").trim()
            if (query.isNotEmpty()) return Command.SearchWeb(query)
        }

        // --- Generic "open X" app launch ---
        openAppTriggers.firstOrNull { text.startsWith(it) }?.let { trigger ->
            val appName = text.removePrefix(trigger).trim()
            if (appName.isNotEmpty()) return Command.OpenApp(appName)
        }

        // --- Nothing local matched: treat it as a question for the AI ---
        return Command.AskAi(rawInput.trim())
    }

    private fun matchesAny(text: String, vararg phrases: String): Boolean =
        phrases.any { text.contains(it) }

    /**
     * Very small spoken-time parser. Understands digits ("10:30", "saa 4")
     * and a handful of Kinyarwanda number words for whole hours. Anything it
     * can't confidently parse returns null so the caller can ask again
     * rather than silently setting the wrong alarm.
     */
    private fun parseSpokenTime(text: String): Pair<Int, Int>? {
        val digitMatch = Regex("""(\d{1,2})[:h](\d{2})""").find(text)
        if (digitMatch != null) {
            val h = digitMatch.groupValues[1].toIntOrNull() ?: return null
            val m = digitMatch.groupValues[2].toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) return h to m
        }

        val bareHour = Regex("""(\d{1,2})""").find(text)
        if (bareHour != null) {
            val h = bareHour.groupValues[1].toIntOrNull()
            if (h != null && h in 0..23) return h to 0
        }

        val kinyarwandaHours = mapOf(
            "saa moya" to 7, "saa mbiri" to 8, "saa tatu" to 9, "saa kane" to 10,
            "saa tanu" to 11, "saa sita" to 12, "saa karindwi" to 13, "saa umunani" to 14,
            "saa cyenda" to 15, "saa cumi" to 16, "saa cumi n'imwe" to 17, "saa kumi n'ebyiri" to 18
        )
        kinyarwandaHours.forEach { (phrase, hour) ->
            if (text.contains(phrase)) return hour to 0
        }

        return null
    }
}
