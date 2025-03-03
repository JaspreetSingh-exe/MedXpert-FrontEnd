package com.example.medxpert.fragments

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.medxpert.R
import com.google.android.material.button.MaterialButton

class ChatIntroFragment : Fragment() {

    private lateinit var videoView: VideoView
    private lateinit var btnEnterChatbot: MaterialButton
    private val handler = Handler(Looper.getMainLooper()) // UI Thread Handler
    private var videoPosition = 0 // Store playback position

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_chat_intro, container, false)

        // Bind UI Elements
        videoView = view.findViewById(R.id.videoView)
        btnEnterChatbot = view.findViewById(R.id.btnEnterChatbot)

        // Apply window insets to adjust padding (optional)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup Video Playback
        setupVideoView()

        // Button Click to Open Chatbot
        btnEnterChatbot.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ChatBotFragment()) // Navigate to Chatbot
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    private fun setupVideoView() {
        val videoPath = "android.resource://${requireActivity().packageName}/${R.raw.chat_intro_video}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.start()

        // Loop video after 60 seconds
        videoView.setOnCompletionListener {
            handler.postDelayed({
                videoView.setVideoURI(Uri.parse(videoPath))
                videoView.start()
            }, 100) // 60 seconds delay before restart
        }
    }

    override fun onPause() {
        super.onPause()
        videoPosition = videoView.currentPosition // Save video position
        videoView.pause()
    }

    override fun onResume() {
        super.onResume()
        videoView.seekTo(videoPosition) // Restore position
        videoView.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Clean up handler
    }
}
