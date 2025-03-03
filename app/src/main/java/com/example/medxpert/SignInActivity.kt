package com.example.medxpert

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class SignInActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnGuestLogin: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvSignUpRedirect: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_in)

        hideSystemUI()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btn_login)
        btnGuestLogin = findViewById(R.id.btn_guest_login)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvSignUpRedirect = findViewById(R.id.tvSignUpRedirect)





        // Login Button Click
        btnLogin.setOnClickListener {
            loginUser()
        }

        // Redirect to Forgot Password Activity
        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Login as Guest
        btnGuestLogin.setOnClickListener {
            loginAsGuest()
        }

        // Redirect to Sign Up
        tvSignUpRedirect.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password cannot be empty!", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_loading)
            .setCancelable(false)
            .show()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                loadingDialog.dismiss()
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user?.isEmailVerified == true) {
                        updateFirestoreEmailVerified(user.uid)
                    } else {
                        showVerificationDialog()
                    }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateFirestoreEmailVerified(userId: String) {
        val userRef = db.collection("users").document(userId)

        userRef.update("emailVerified", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Sign-in successful!", Toast.LENGTH_SHORT).show()
                // Navigate to Dashboard
                startActivity(Intent(this, DashBoardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update verification status: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showVerificationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_verification, null)
        val alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnOkay = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOkay)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)

        tvMessage.text = "Your email is not verified. Please check your inbox and verify your email."

        btnOkay.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun loginAsGuest() {
        auth.signInAnonymously()
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    Toast.makeText(this, "Signed in as Guest!", Toast.LENGTH_SHORT).show()
                    // Redirect to DashboardActivity
                    startActivity(Intent(this, DashBoardActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Guest Sign-In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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