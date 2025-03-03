package com.example.medxpert

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var imgSplash: ImageView
    private val images = intArrayOf(
        R.drawable.chat_bot_image,  // Replace with actual image names
        R.drawable.search_image,
        R.drawable.document_image
    )
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Set the activity to fullscreen and hide the notch area
                window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        setContentView(R.layout.activity_splash) // Keep your content

        hideSystemUI()

        imgSplash = findViewById(R.id.imgSplash)
        auth = FirebaseAuth.getInstance()

        startImageRotation()
    }

    private fun startImageRotation() {
        val imageSwitcher = object : Runnable {
            override fun run() {
                imgSplash.setImageResource(images[currentIndex])
                currentIndex = (currentIndex + 1) % images.size
                handler.postDelayed(this, 1000)
            }
        }

        handler.post(imageSwitcher)

        // Move to the appropriate screen after 3 seconds
        handler.postDelayed({
            checkUserStatus()
        }, 3000)
    }

    private fun checkUserStatus() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in → Redirect to Dashboard
            startActivity(Intent(this, DashBoardActivity::class.java))
        } else {
            // No user signed in → Redirect to Sign-in Screen
            startActivity(Intent(this, SignInActivity::class.java))
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)  // Stop handler to prevent leaks
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
