package com.example.medxpert

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
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
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)

        hideSystemUI()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val fullName = findViewById<TextInputEditText>(R.id.etFullName)
        val email = findViewById<TextInputEditText>(R.id.etEmail)
        val password = findViewById<TextInputEditText>(R.id.etPassword)
        val confirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnSignUp = findViewById<MaterialButton>(R.id.btn_signup)
        val tvSignInRedirect = findViewById<TextView>(R.id.tvSignInRedirect)

        btnSignUp.setOnClickListener {
            val name = fullName.text.toString().trim()
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()
            val confirmPasswordText = confirmPassword.text.toString().trim()

            if (name.isEmpty() || emailText.isEmpty() || passwordText.isEmpty() || confirmPasswordText.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
                Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidPassword(passwordText)) {
                Toast.makeText(this, "Password must have at least 1 uppercase, 1 lowercase, 1 number, and be at least 5 characters long.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (passwordText != confirmPasswordText) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(name, emailText, passwordText)
        }

        tvSignInRedirect.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
            return@setOnClickListener
        }
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordPattern = Regex("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{5,}\$")
        return passwordPattern.matches(password)
    }

    private fun registerUser(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        sendEmailVerification(user, name, email)
                    }
                } else {
                    Toast.makeText(this, "Sign-up failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun sendEmailVerification(user: com.google.firebase.auth.FirebaseUser, name: String, email: String) {
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showVerificationPopup()

                    // Save user data only after sending verification email
                    saveUserToFirestore(user.uid, name, email)
                } else {
                    Toast.makeText(this, "Failed to send verification email", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showVerificationPopup() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_verification, null)

        val alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnOkay = dialogView.findViewById<MaterialButton>(R.id.btnOkay)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)

        tvMessage.text = "A verification email has been sent to your inbox. Please verify your email before logging in."

        btnOkay.setOnClickListener {
            alertDialog.dismiss()
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }

        alertDialog.show()
    }


    private fun saveUserToFirestore(uid: String, name: String, email: String) {
        val userMap = hashMapOf(
            "name" to name,
            "email" to email,
            "plan" to "full", // Full access for signed-up users
            "reportsUploaded" to 0,
            "chatbotQueries" to 0,
            "createdAt" to System.currentTimeMillis(),
            "lastLogin" to System.currentTimeMillis(),
            "emailVerified" to false
        )

        firestore.collection("users").document(uid)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Sign-up successful!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to store data: ${e.message}", Toast.LENGTH_LONG).show()
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