package com.mkrobot.assistant

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mkrobot.assistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill with the existing key (if any) so the user can see it's set
        // without us ever printing it anywhere else in the app.
        binding.editApiKey.setText(SecurePrefs.getApiKey(this).orEmpty())

        binding.btnSaveKey.setOnClickListener {
            val key = binding.editApiKey.text?.toString()?.trim().orEmpty()
            SecurePrefs.saveApiKey(this, key)
            Toast.makeText(this, R.string.settings_saved_toast, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
