package com.example.medxpert

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnResetPassword: Button
    private lateinit var tvBackToLogin: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var videoPosition = 0 // Save video position
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        hideSystemUI()
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Bind UI Elements
        videoView = findViewById(R.id.videoView)
        etEmail = findViewById(R.id.etEmail)
        btnResetPassword = findViewById(R.id.btn_reset_password)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        // Set video path
        val videoPath = "android.resource://${packageName}/${R.raw.forgot_pass_video}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.start()

        // Loop video every 60 seconds
        videoView.setOnCompletionListener {
            handler.postDelayed({
                videoView.setVideoURI(Uri.parse(videoPath))
                videoView.start()
            }, 60000) // 60 seconds delay
        }

        // Reset Password Button Click
        btnResetPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Enter a valid email"
                return@setOnClickListener
            }

            sendPasswordResetEmail(email)
        }

        // Back to Login
        tvBackToLogin.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                showSuccessDialog()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reset_success, null)

        val alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnOkay = dialogView.findViewById<MaterialButton>(R.id.btnOkay)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)

        tvMessage.text = "A password reset link has been sent to your email. Please check your inbox."

        btnOkay.setOnClickListener {
            alertDialog.dismiss()
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }

        alertDialog.show()
    }


    override fun onPause() {
        super.onPause()
        videoPosition = videoView.currentPosition
        videoView.pause()
    }

    override fun onResume() {
        super.onResume()
        videoView.seekTo(videoPosition)
        videoView.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Function to hide the status bar and navigation bar for a fullscreen immersive experience
     */
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11+)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // For older versions
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }
}