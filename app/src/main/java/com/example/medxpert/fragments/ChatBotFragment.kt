package com.example.medxpert.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medxpert.R
import com.example.medxpert.adapters.ChatAdapter
import com.example.medxpert.models.ChatMessage
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Text

class ChatBotFragment : Fragment() {
    private lateinit var recyclerChat: RecyclerView
    private lateinit var etChatMessage: EditText
    private lateinit var btnSendMessage: ImageButton
    private lateinit var tvTypingIndicator: TextView
    private lateinit var chatInputLayout: ConstraintLayout
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val executorService = Executors.newSingleThreadExecutor()
    private lateinit var tvQueryLimit: TextView

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private var queriesLeft = 10


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_bot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerChat = view.findViewById(R.id.recyclerChat)
        etChatMessage = view.findViewById(R.id.etChatMessage)
        btnSendMessage = view.findViewById(R.id.btnSendMessage)
        tvTypingIndicator = view.findViewById(R.id.tvTypingIndicator)
        chatInputLayout = view.findViewById(R.id.chatInputLayout)
        tvQueryLimit = view.findViewById(R.id.tvQueryLimit)


        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        recyclerChat.layoutManager = layoutManager

        chatAdapter = ChatAdapter(chatMessages)
        recyclerChat.adapter = chatAdapter

        // Fetch the user's query limit from Firestore
        fetchQueryCount()

        btnSendMessage.setOnClickListener {
            val userMessage = etChatMessage.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                sendMessage(userMessage)
                etChatMessage.setText("")
            }
        }
    }

    private fun fetchQueryCount() {
        val user = firebaseAuth.currentUser
        if (user == null || user.isAnonymous) {
            tvQueryLimit.text = "Guest users cannot access chatbot."
            btnSendMessage.isEnabled = false
            return
        }

        val userId = user.uid
        val docRef = firestore.collection("users").document(userId)

        // Force Firestore to fetch latest data instead of cached values
        docRef.get(Source.SERVER).addOnSuccessListener { snapshot ->
            val queriesToday = snapshot.getLong("queries_today") ?: 0
            val lastQueryDay = snapshot.getLong("last_query_day") ?: 0
            val todayDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24) // Convert to days

            // ✅ Reset queries when a new day starts
            if (lastQueryDay < todayDay) {
                docRef.update(
                    mapOf(
                        "queries_today" to 0L,
                        "last_query_day" to todayDay // ✅ Store the correct new day
                    )
                ).addOnSuccessListener {
                    queriesLeft = 10
                    tvQueryLimit.text = "Queries left today: 10"
                }
            } else {
                queriesLeft = (10 - queriesToday).coerceAtLeast(0).toInt()
            }

            tvQueryLimit.text = "Queries left today: $queriesLeft"

            // Disable send button if limit is reached
            if (queriesLeft <= 0) {
                btnSendMessage.isEnabled = false
                tvQueryLimit.text = "Daily query limit reached. Come back tomorrow!"
            }
        }.addOnFailureListener {
            tvQueryLimit.text = "Error fetching queries"
        }
    }



    private fun sendMessage(userMessage: String) {
        if (queriesLeft <= 0) {
            Toast.makeText(requireContext(), "Daily query limit reached!", Toast.LENGTH_SHORT)
                .show()
            return
        }

        chatMessages.add(ChatMessage(userMessage, true))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        recyclerChat.scrollToPosition(chatMessages.size - 1)

        tvTypingIndicator.visibility = View.VISIBLE

        executorService.execute {
            try {
                val client = OkHttpClient()

                // Prepare JSON Body
                val jsonBody = JSONObject()
                jsonBody.put("question", userMessage)

                val requestBody =
                    jsonBody.toString().toRequestBody("application/json".toMediaType())

                val apiUrl = getString(R.string.api_url)
                val request = Request.Builder()
                    .url("$apiUrl/chat/chat/?question=${userMessage}")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        activity?.runOnUiThread {
                            tvTypingIndicator.visibility = View.GONE
                            Toast.makeText(
                                requireContext(),
                                "Error: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.body?.string()?.let { responseBody ->
                            val jsonResponse = JSONObject(responseBody)
                            val aiMessage =
                                jsonResponse.optString("response", "Sorry, I didn't understand.")

                            activity?.runOnUiThread {
                                tvTypingIndicator.visibility = View.GONE
                                chatMessages.add(ChatMessage(aiMessage, false))
                                chatAdapter.notifyItemInserted(chatMessages.size - 1)
                                recyclerChat.scrollToPosition(chatMessages.size - 1)

                                // Update query count in Firestore
                                updateQueryCount()
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("ChatBot Debug", "Error: ${e.message}")
            }
        }
    }

    private fun updateQueryCount() {
        val user = firebaseAuth.currentUser ?: return
        if (user.isAnonymous) return

        val userId = user.uid
        val docRef = firestore.collection("users").document(userId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val queriesToday = snapshot.getLong("queries_today") ?: 0
            val lastQueryDay = snapshot.getLong("last_query_day") ?: 0
            val todayDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)

            val newQueryCount = if (lastQueryDay < todayDay) 1 else queriesToday + 1
            transaction.update(docRef, "queries_today", newQueryCount)
            transaction.update(docRef, "last_query_day", todayDay)

            queriesLeft = (10 - newQueryCount).coerceAtLeast(0).toInt()
        }.addOnSuccessListener {
            tvQueryLimit.text = "Queries left today: $queriesLeft"

            // If the limit is reached, disable sending messages
            if (queriesLeft <= 0) {
                btnSendMessage.isEnabled = false
                tvQueryLimit.text = "Daily query limit reached. Come back tomorrow!"
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to update query count", Toast.LENGTH_SHORT).show()
        }
    }


}