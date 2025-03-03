package com.example.medxpert.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.medxpert.R
import com.example.medxpert.models.ChatMessage

class ChatAdapter(private val chatMessages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = chatMessages[position]
        holder.bind(message)

        // Debugging log to check if messages are added correctly
        Log.d("ChatAdapter", "Binding message at position $position: ${message.message}")
    }

    override fun getItemViewType(position: Int): Int {
        return if (chatMessages[position].isUser) R.layout.item_chat_user else R.layout.item_chat_ai
    }

    override fun getItemCount(): Int = chatMessages.size

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.message
        }
    }
}
