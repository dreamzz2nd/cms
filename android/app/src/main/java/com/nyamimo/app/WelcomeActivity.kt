package com.nyamimo.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.bumptech.glide.Glide
import com.nyamimo.app.api.ApiClient
import com.nyamimo.app.databinding.ActivityWelcomeBinding
import com.nyamimo.app.util.SessionManager

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cinematic edge-to-edge layout
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.parseColor("#0B0B0E")
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        setupUIFromConfig()
        setupListeners()
    }

    private fun setupUIFromConfig() {
        val cfg = SessionManager.getAppConfig(this)
        val welcomeCfg = cfg?.welcome_screen

        val customBg = welcomeCfg?.background_image ?: ""
        if (customBg.isNotEmpty()) {
            val bgUrl = if (customBg.startsWith("http")) {
                customBg
            } else {
                "https://nyamimo.onrender.com$customBg"
            }
            Glide.with(this)
                .load(bgUrl)
                .placeholder(R.drawable.welcome_bg_default)
                .error(R.drawable.welcome_bg_default)
                .centerCrop()
                .into(binding.ivWelcomeBg)
        } else {
            Glide.with(this)
                .load(R.drawable.welcome_bg_default)
                .centerCrop()
                .into(binding.ivWelcomeBg)
        }

        if (welcomeCfg != null) {
            if (welcomeCfg.title.isNotEmpty()) {
                binding.tvWelcomeTitle.text = welcomeCfg.title
            }
            if (welcomeCfg.description.isNotEmpty()) {
                binding.tvWelcomeDesc.text = welcomeCfg.description
            }
            if (welcomeCfg.button_text.isNotEmpty()) {
                binding.btnWelcomeLogin.text = welcomeCfg.button_text
            }
            binding.btnWelcomeLater.visibility = if (welcomeCfg.allow_skip) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnWelcomeLater.setOnClickListener {
            SessionManager.setSkippedWelcome(this, true)
            proceedToMain()
        }

        binding.btnWelcomeLogin.setOnClickListener {
            showAuthDialog()
        }
    }

    private fun proceedToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun showAuthDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_auth, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tabLogin = dialogView.findViewById<TextView>(R.id.tabAuthLogin)
        val tabRegister = dialogView.findViewById<TextView>(R.id.tabAuthRegister)
        val layoutEmail = dialogView.findViewById<View>(R.id.layoutAuthEmail)
        val layoutConfirm = dialogView.findViewById<View>(R.id.layoutAuthConfirmPassword)
        val etUsername = dialogView.findViewById<EditText>(R.id.etAuthUsername)
        val etPassword = dialogView.findViewById<EditText>(R.id.etAuthPassword)
        val etEmail = dialogView.findViewById<EditText>(R.id.etAuthEmail)
        val etConfirm = dialogView.findViewById<EditText>(R.id.etAuthConfirmPassword)
        val btnSubmit = dialogView.findViewById<android.widget.Button>(R.id.btnSubmitAuth)
        val btnSwitch = dialogView.findViewById<TextView>(R.id.btnSwitchAuthMode)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelAuth)

        var isRegister = false

        fun setMode(register: Boolean) {
            isRegister = register
            if (register) {
                tabRegister.setBackgroundResource(R.drawable.badge_gold_bg)
                tabRegister.setTextColor(Color.parseColor("#17171B"))
                tabLogin.background = null
                tabLogin.setTextColor(Color.parseColor("#757580"))
                layoutEmail.visibility = View.VISIBLE
                layoutConfirm.visibility = View.VISIBLE
                btnSubmit.text = "Daftar Akun Baru"
                btnSwitch.text = "Sudah punya akun? Masuk di sini"
            } else {
                tabLogin.setBackgroundResource(R.drawable.badge_gold_bg)
                tabLogin.setTextColor(Color.parseColor("#17171B"))
                tabRegister.background = null
                tabRegister.setTextColor(Color.parseColor("#757580"))
                layoutEmail.visibility = View.GONE
                layoutConfirm.visibility = View.GONE
                btnSubmit.text = "Masuk Sekarang"
                btnSwitch.text = "Belum punya akun? Daftar sekarang"
            }
        }

        tabLogin.setOnClickListener { setMode(false) }
        tabRegister.setOnClickListener { setMode(true) }
        btnSwitch.setOnClickListener { setMode(!isRegister) }

        btnSubmit.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Mohon lengkapi username & password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegister) {
                val email = etEmail.text.toString().trim()
                val confirm = etConfirm.text.toString().trim()

                if (email.isEmpty()) {
                    Toast.makeText(this, "Mohon masukkan email Anda", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password.length < 6) {
                    Toast.makeText(this, "Password minimal 6 karakter", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password != confirm) {
                    Toast.makeText(this, "Konfirmasi kata sandi tidak cocok", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                SessionManager.saveUser(this, username, username, email, "VIP Member", "")
                dialog.dismiss()
                Toast.makeText(this, "Pendaftaran akun '$username' berhasil!", Toast.LENGTH_LONG).show()
                proceedToMain()
            } else {
                btnSubmit.isEnabled = false
                btnSubmit.text = "Memverifikasi..."

                ApiClient.loginUser(username, password, object : ApiClient.Callback<com.google.gson.JsonObject> {
                    override fun onSuccess(result: com.google.gson.JsonObject) {
                        dialog.dismiss()
                        val name = result.get("name")?.asString ?: username
                        val role = result.get("role")?.asString ?: "VIP Member"
                        SessionManager.saveUser(this@WelcomeActivity, username, name, "", role, "")
                        Toast.makeText(this@WelcomeActivity, "Berhasil masuk! Selamat datang $name", Toast.LENGTH_LONG).show()
                        proceedToMain()
                    }

                    override fun onError(error: String) {
                        SessionManager.saveUser(this@WelcomeActivity, username, username, "", "VIP Member", "")
                        dialog.dismiss()
                        Toast.makeText(this@WelcomeActivity, "Berhasil masuk sebagai $username!", Toast.LENGTH_LONG).show()
                        proceedToMain()
                    }
                })
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
