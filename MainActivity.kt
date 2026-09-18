package com.mkrobot.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mkrobot.assistant.ai.AiProviderException
import com.mkrobot.assistant.ai.HttpAiProvider
import com.mkrobot.assistant.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var appLauncher: AppLauncher
    private val aiProvider by lazy { HttpAiProvider(this) }

    /** "rw" or "en" — drives both speech recognition and TTS locale. */
    private var currentLanguage = "rw"
    private var isFlashlightOn = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Individual actions re-check permissions themselves before running. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentLanguage = SecurePrefs.getLanguage(this)
        appLauncher = AppLauncher(this)
        tts = TextToSpeech(this, this)

        requestCorePermissions()
        setIdleUi()

        binding.btnMic.setOnClickListener { onMicTapped() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.txtLangToggle.setOnClickListener { toggleLanguage() }
    }

    // ---------------------------------------------------------------- setup

    private fun requestCorePermissions() {
        val needed = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // No extra runtime permission needed for QUERY_ALL_PACKAGES (normal perm).
        }
        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest.toTypedArray())
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = if (currentLanguage == "rw") {
                // Most devices don't ship a Kinyarwanda TTS voice; fall back
                // to Swahili/English gracefully if "rw" isn't installed.
                val rw = Locale("rw", "RW")
                if (tts?.isLanguageAvailable(rw) == TextToSpeech.LANG_MISSING_DATA ||
                    tts?.isLanguageAvailable(rw) == TextToSpeech.LANG_NOT_SUPPORTED
                ) Locale.ENGLISH else rw
            } else {
                Locale.ENGLISH
            }
        }
    }

    private fun toggleLanguage() {
        currentLanguage = if (currentLanguage == "rw") "en" else "rw"
        SecurePrefs.saveLanguage(this, currentLanguage)
        binding.txtLangToggle.text = if (currentLanguage == "rw") "RW / EN" else "EN / RW"
        onInit(TextToSpeech.SUCCESS)
        Toast.makeText(
            this,
            if (currentLanguage == "rw") "Ururimi: Ikinyarwanda" else "Language: English",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ------------------------------------------------------------- mic flow

    private fun onMicTapped() {
        if (!hasPermission(android.Manifest.permission.RECORD_AUDIO)) {
            requestCorePermissions()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak(getString(R.string.status_error))
            return
        }
        startListening()
    }

    private fun startListening() {
        setListeningUi()
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }

        val locale = if (currentLanguage == "rw") "rw-RW" else "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            // If the device has no Kinyarwanda language pack, Android will
            // silently fall back to its default recognition language.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        speechRecognizer?.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            binding.txtRecognized.text = text
            if (text.isNotBlank()) {
                setThinkingUi()
                handleRecognizedText(text)
            } else {
                setIdleUi()
            }
        }

        override fun onError(error: Int) {
            setIdleUi()
            speak(getString(R.string.status_error))
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // -------------------------------------------------------- dispatch loop

    private fun handleRecognizedText(text: String) {
        val command = CommandParser.parse(text)
        if (command.requiresConfirmation) {
            askConfirmation(command)
        } else {
            execute(command)
        }
    }

    private fun askConfirmation(command: Command) {
        val message = when (command) {
            is Command.Call -> "Uzi neza ko ushaka guhamagara ${command.contactName}?"
            is Command.SendMessage -> "Uzi neza ko ushaka kwandikira ${command.contactName}?"
            is Command.ToggleBluetooth -> if (command.turnOn)
                "Uzi neza ko ushaka gufungura Bluetooth?" else "Uzi neza ko ushaka kuzimya Bluetooth?"
            else -> getString(R.string.confirm_dialog_title)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm_dialog_yes) { _, _ -> execute(command) }
            .setNegativeButton(R.string.confirm_dialog_no) { _, _ -> setIdleUi() }
            .setOnCancelListener { setIdleUi() }
            .show()
    }

    private fun execute(command: Command) {
        setActingUi()
        when (command) {
            is Command.OpenApp -> {
                val ok = appLauncher.launchByName(command.spokenName)
                finishAction(if (ok) null else "Sinabonye porogaramu '${command.spokenName}'.")
            }
            Command.GoBack -> {
                onBackPressedDispatcher.onBackPressed()
                finishAction(null)
            }
            Command.GoHome -> {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finishAction(null)
            }
            Command.OpenCamera -> {
                val ok = appLauncher.launchByName("camera")
                finishAction(if (ok) null else "Sinabonye kamera.")
            }
            Command.OpenSettingsApp -> {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finishAction(null)
            }
            Command.ToggleFlashlight -> {
                finishAction(toggleFlashlight())
            }
            Command.WhatTimeIsIt -> {
                val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
                finishAction(null, spokenReply = "Ni saa $time.")
            }
            Command.WhatIsTheDate -> {
                val date = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                finishAction(null, spokenReply = "Ni itariki $date.")
            }
            is Command.SetAlarm -> {
                if (command.hour < 0) {
                    finishAction("Ntabwo numvise igihe neza. Ongera uvuge, urugero: 'Shyiraho alarm saa kumi'.")
                } else {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        finishAction(null)
                    } else {
                        finishAction("Nta porogaramu ya alarm yabonetse.")
                    }
                }
            }
            is Command.ToggleBluetooth -> {
                finishAction(toggleBluetooth(command.turnOn))
            }
            is Command.Call -> {
                finishAction(placeCall(command.contactName))
            }
            is Command.SendMessage -> {
                finishAction(openMessageCompose(command.contactName))
            }
            is Command.SearchWeb -> {
                finishAction(openWebSearch(command.query))
            }
            is Command.AskAi -> askAi(command.question)
            is Command.Unknown -> finishAction("Sinabyumvise neza. Ongera uvuge.")
        }
    }

    /** Wraps up a synchronous action: shows/speaks an optional error or a
     *  custom spoken reply, then returns the UI to idle. */
    private fun finishAction(errorMessage: String?, spokenReply: String? = null) {
        when {
            errorMessage != null -> {
                binding.txtStatus.text = getString(R.string.status_error)
                speak(errorMessage)
            }
            spokenReply != null -> {
                setDoneUi()
                speak(spokenReply)
            }
            else -> setDoneUi()
        }
        binding.root.postDelayed({ setIdleUi() }, 1500)
    }

    private fun askAi(question: String) {
        lifecycleScope.launch {
            try {
                val answer = aiProvider.answer(question)
                setDoneUi()
                speak(answer)
            } catch (e: AiProviderException) {
                binding.txtStatus.text = getString(R.string.status_error)
                speak(e.message ?: getString(R.string.status_error))
            } finally {
                binding.root.postDelayed({ setIdleUi() }, 2000)
            }
        }
    }

    // ------------------------------------------------------- phone actions

    private fun toggleFlashlight(): String? {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "Iyi telefoni nta itara ifite."
            isFlashlightOn = !isFlashlightOn
            cameraManager.setTorchMode(cameraId, isFlashlightOn)
            null
        } catch (e: CameraAccessException) {
            "Ntibyashobotse gukoresha itara."
        }
    }

    private fun toggleBluetooth(turnOn: Boolean): String? {
        // Android 13+ requires BLUETOOTH_CONNECT and, from Android 13 onward,
        // apps can no longer silently flip Bluetooth on/off — the system
        // routes this to a user-facing settings panel instead.
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT))
        }
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return null
    }

    private fun placeCall(contactName: String): String? {
        val number = findContactNumber(contactName) ?: return "Sinabonye nimero ya $contactName mu makonti."
        val hasCallPerm = hasPermission(android.Manifest.permission.CALL_PHONE)
        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return null
    }

    private fun openMessageCompose(contactName: String): String? {
        val number = findContactNumber(contactName)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${number.orEmpty()}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return null
    }

    private fun findContactNumber(name: String): String? {
        if (!hasPermission(android.Manifest.permission.READ_CONTACTS)) {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS))
            return null
        }
        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactName = cursor.getString(nameIdx) ?: continue
                if (contactName.lowercase(Locale.getDefault()).contains(name.lowercase(Locale.getDefault()))) {
                    return cursor.getString(numberIdx)
                }
            }
        }
        return null
    }

    private fun openWebSearch(query: String): String? {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            null
        } else {
            // Fall back to opening a browser search URL directly.
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(browserIntent)
            null
        }
    }

    // -------------------------------------------------------------- UI state

    private fun setIdleUi() {
        binding.txtStatus.text = getString(R.string.status_idle)
        binding.imgRobotFace.setImageResource(R.drawable.robot_face_idle)
    }

    private fun setListeningUi() {
        binding.txtStatus.text = getString(R.string.status_listening)
        binding.imgRobotFace.setImageResource(R.drawable.robot_face_listening)
    }

    private fun setThinkingUi() {
        binding.txtStatus.text = getString(R.string.status_thinking)
    }

    private fun setActingUi() {
        binding.txtStatus.text = getString(R.string.status_acting)
    }

    private fun setDoneUi() {
        binding.txtStatus.text = getString(R.string.status_done)
        binding.imgRobotFace.setImageResource(R.drawable.robot_face_idle)
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mk_robot_utterance")
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
