package com.example.medxpert.fragments

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.example.medxpert.R
import com.example.medxpert.SignInActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class ResultsFragment : Fragment() {
    private lateinit var tvReportSummary: TextView
    private lateinit var tvAbnormalitiesTitle: TextView
    private lateinit var layoutAbnormalities: LinearLayout
    private lateinit var btnChatWithAI: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_results, container, false)

        tvReportSummary = view.findViewById(R.id.tvReportSummary)
        tvAbnormalitiesTitle = view.findViewById(R.id.tvAbnormalitiesTitle)
        layoutAbnormalities = view.findViewById(R.id.layoutAbnormalities)
        btnChatWithAI = view.findViewById(R.id.btnChatWithAI)

        // Get data from Bundle
        val jsonResponse = arguments?.getString("summary") ?: ""

        if (jsonResponse.isNotEmpty()) {
            parseAndDisplayResponse(jsonResponse)
        }

        // Navigate to ChatIntroFragment when button is clicked
        btnChatWithAI.setOnClickListener {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser?.isAnonymous == true) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Sign In Required")
                    .setMessage("Sign in to access AI Chatbot features.")
                    .setPositiveButton("Sign In") { _, _ ->
                        startActivity(Intent(requireContext(), SignInActivity::class.java))
                        requireActivity().finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                // Only allow signed-in users to access Chat
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ChatIntroFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        return view
    }

    private fun parseAndDisplayResponse(response: String) {
        try {
            val jsonObject = JSONObject(response)
            val summaryText = jsonObject.optString("summary", "No summary available")
            tvReportSummary.text = summaryText

            // Check if abnormalities exist
            if (jsonObject.has("abnormalities")) {
                val abnormalitiesObject = jsonObject.getJSONObject("abnormalities") // ✅ FIXED
                if (abnormalitiesObject.has("abnormalities")) {
                    val abnormalitiesArray = abnormalitiesObject.getJSONArray("abnormalities")

                    if (abnormalitiesArray.length() > 0) {
                        tvAbnormalitiesTitle.visibility = View.VISIBLE
                        layoutAbnormalities.visibility = View.VISIBLE

                        for (i in 0 until abnormalitiesArray.length()) {
                            val abnormality = abnormalitiesArray.getJSONObject(i)
                            addAbnormalityView(abnormality)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ResultsFragment", "Error parsing response: ${e.message}")
        }
    }



    private fun addAbnormalityView(abnormality: JSONObject) {
        val abnormalityView = TextView(requireContext())

        val formattedText = """
        <b>Parameter:</b> <br>${abnormality.optString("parameter", "N/A")}<br><br>
        <b>Value:</b> <br>${abnormality.optString("value", "N/A")}<br><br>
        <b>Explanation:</b> <br>${abnormality.optString("explanation", "N/A")}<br><br>
        <b>Possible Conditions:</b> <br>${abnormality.optJSONArray("possible_conditions")?.join("<br>") ?: "N/A"}<br><br>
        <b>Recommendations:</b> <br>${abnormality.optString("recommendations", "N/A")}
    """.trimIndent()

        abnormalityView.text = HtmlCompat.fromHtml(formattedText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        abnormalityView.textSize = 14f
        abnormalityView.setTextColor(resources.getColor(android.R.color.black, null))
        abnormalityView.setPadding(0, 8, 0, 16)
        abnormalityView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        abnormalityView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) // Fixing font issue

        layoutAbnormalities.addView(abnormalityView)
    }


}
