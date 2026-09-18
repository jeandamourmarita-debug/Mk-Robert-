package com.mkrobot.assistant

/**
 * Every action the assistant can take. Each case is tagged with whether it
 * needs user confirmation before it runs (calls, messages, Bluetooth) so the
 * dispatcher in MainActivity can enforce that uniformly.
 */
sealed class Command {
    data class OpenApp(val spokenName: String) : Command()
    data class Call(val contactName: String) : Command()
    data class SendMessage(val contactName: String) : Command()
    data class SearchWeb(val query: String) : Command()
    data class SetAlarm(val hour: Int, val minute: Int) : Command()
    data class ToggleBluetooth(val turnOn: Boolean) : Command()
    data class AskAi(val question: String) : Command()

    object GoBack : Command()
    object GoHome : Command()
    object OpenCamera : Command()
    object OpenSettingsApp : Command()
    object ToggleFlashlight : Command()
    object WhatTimeIsIt : Command()
    object WhatIsTheDate : Command()

    data class Unknown(val raw: String) : Command()

    /** True if this command touches something private/irreversible enough
     *  to require an explicit "Yego / Oya" confirmation first. */
    val requiresConfirmation: Boolean
        get() = this is Call || this is SendMessage || this is ToggleBluetooth
}
