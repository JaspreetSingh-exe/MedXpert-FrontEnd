package com.example.medxpert

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.medxpert.fragments.UploadFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class DashBoardActivity : AppCompatActivity() {

    private lateinit var imgLogout: ImageView
    private lateinit var tvProfileInitial: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dash_board)

        hideSystemUI() // Hide status & navigation bars

        imgLogout = findViewById(R.id.imgLogout)
        tvProfileInitial = findViewById(R.id.tvProfileInitial)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Load UploadFragment by default
        if (savedInstanceState == null) {
            switchFragment(UploadFragment())
        }

        // Fetch and set user name from Firestore
        fetchUserName()

        // Handle Logout Click
        imgLogout.setOnClickListener {
            logoutUser()
        }

    }

    /**
     * Fetches the user's name from Firestore and updates UI
     */
    private fun fetchUserName() {
        val user = auth.currentUser
        if (user != null) {
            if (user.isAnonymous) {
                // Guest User
                setProfileInitial("Guest")
            } else {
                // Regular User - Fetch from Fire store
                val userId = user.uid
                firestore.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val userName = document.getString("name") ?: "User"
                            setProfileInitial(userName)
                        } else {
                            setProfileInitial("User")
                        }
                    }
                    .addOnFailureListener {
                        setProfileInitial("User")
                    }
            }
        } else {
            setProfileInitial("U") // Default case
        }
    }


    /**
     * Function to set the initial letter with a random background color
     */
    private fun setProfileInitial(userName: String) {
        val initial = userName.firstOrNull()?.uppercaseChar().toString()

        // Set initial
        tvProfileInitial.text = initial

        // Generate a random background color
        val random = Random()
        val color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))

        // Apply the color as background with rounded shape
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)

        tvProfileInitial.background = drawable
    }

    /**
     * Function to switch between fragments
     */
    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Function to logout user
     */
    private fun logoutUser() {
        auth.signOut() // Sign out from Firebase
        val intent = Intent(this, SignInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
