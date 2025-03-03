package com.example.medxpert.fragments

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.medxpert.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class UploadFragment : Fragment() {

    private lateinit var imgUpload: ImageView
    private lateinit var tvUploadInstructions: TextView
    private lateinit var btnUpload: Button
    private lateinit var videoView: VideoView
    private lateinit var tvUploadLimit: TextView

    private lateinit var sharedPreferences: SharedPreferences

    private var selectedFileUri: Uri? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var videoPosition = 0 // Saves video position on background

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_upload, container, false)

        // Existing references
        imgUpload = view.findViewById(R.id.imgLogo)
        tvUploadInstructions = view.findViewById(R.id.tvUploadInstructions)
        btnUpload = view.findViewById(R.id.btnUpload)
        videoView = view.findViewById(R.id.videoView)
        tvUploadLimit = view.findViewById(R.id.tvUploadLimit)

        // Restore video position
        sharedPreferences = requireActivity().getSharedPreferences("video_prefs", Context.MODE_PRIVATE)
        videoPosition = sharedPreferences.getInt("video_position", 0)

        setupVideoView()

        imgUpload.setOnClickListener {
            requestStoragePermission()
        }

        // =================== NEW PART: We check daily limit before uploading ===================
        btnUpload.setOnClickListener {
            if (selectedFileUri != null) {
                checkUploadLimitAndProceed()
            } else {
                showToast("Please select a file first!")
            }
        }
        // =====================================================================================

        // NEW: Update the UI with remaining uploads
        updateUploadLimitDisplay()
        return view
    }

    private fun updateUploadLimitDisplay() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            tvUploadLimit.text = "No user found!"
            return
        }

        val isGuest = user.isAnonymous
        val userId: String
        val userCollection: String
        val dailyLimit: Int

        if (isGuest) {
            userId = getDeviceId()
            userCollection = "guest_users"
            dailyLimit = 2 // Guests get 2 uploads per day
        } else {
            userId = user.uid
            userCollection = "users"
            dailyLimit = 5 // Signed-in users get 5 uploads per day
        }

        val docRef = firestore.collection(userCollection).document(userId)

        // ✅ Force fetching from Firestore server to avoid cached data issues
        docRef.get(Source.SERVER).addOnSuccessListener { snapshot ->
            val uploadsToday = snapshot.getLong("uploads_today") ?: 0
            val lastUploadDay = snapshot.getLong("last_upload_day") ?: 0
            val todayDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24) // Convert to days

            var remainingUploads = dailyLimit - uploadsToday.toInt()

            // ✅ If it's a new day, reset the counter
            if (lastUploadDay < todayDay) {
                docRef.update(
                    mapOf(
                        "uploads_today" to 0L,
                        "last_upload_day" to todayDay
                    )
                ).addOnSuccessListener {
                    remainingUploads = dailyLimit // Reset to full daily limit
                    tvUploadLimit.text = "Uploads left today: $remainingUploads"
                }
            } else {
                // ✅ Show the remaining uploads for today
                tvUploadLimit.text = "Uploads left today: ${maxOf(0, remainingUploads)}"
            }
        }.addOnFailureListener {
            tvUploadLimit.text = "Error fetching upload limit"
        }
    }



    // =================== Existing method: sets up video logic ===================
    private fun setupVideoView() {
        val videoPath = "android.resource://" + requireContext().packageName + "/" + R.raw.video_upload
        videoView.setVideoURI(Uri.parse(videoPath))

        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = false
            videoView.seekTo(videoPosition)
            videoView.start()
        }

        videoView.setOnErrorListener { _, what, extra ->
            Log.e("VideoView", "Error: $what, Extra: $extra")
            true
        }

        videoView.setOnCompletionListener {
            handler.postDelayed({
                videoView.start()
            }, 30000)
        }
    }

    override fun onPause() {
        super.onPause()
        videoPosition = videoView.currentPosition
        videoView.pause()
        sharedPreferences.edit().putInt("video_position", videoPosition).apply()
    }

    override fun onResume() {
        super.onResume()
        videoView.seekTo(videoPosition)
        videoView.start()

        updateUploadLimitDisplay()
    }

    // =================== Existing method: request storage permission logic ===================
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        } else {
            openFilePicker()
        }
    }

    // =================== Existing method: handle permission response ===================
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                openFilePicker()
            } else {
                showToast("Permission required to access files")
            }
        }

    // =================== Existing method: open file picker ===================
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        filePickerLauncher.launch(intent)
    }

    // =================== Existing method: handle file picker result ===================
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedFileUri = uri
                    tvUploadInstructions.text = "Report uploaded. Click Upload button to see results"
                    imgUpload.setImageResource(R.drawable.ic_document)
                }
            }
        }

    // =================== EXISTING METHOD: Upload file to server ===================
    private fun uploadFileToServer(fileUri: Uri) {
        val file = createTempFileFromUri(fileUri)
        val fileName = getFileName(fileUri)

        Log.d("Upload Debug", "File Name: $fileName")
        Log.d("Upload Debug", "File Path: ${file.absolutePath}")

        val requestFile = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), file)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, requestFile)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val apiUrl = getString(R.string.api_url)
        val request = Request.Builder()
            .url("$apiUrl/upload/")
            .post(multipartBody)
            .build()

        Log.d("Upload Debug", "Sending request to server...")

        val progressDialog = showLoadingDialog()

        Executors.newSingleThreadExecutor().execute {
            try {
                val response = client.newCall(request).execute()
                Log.d("Upload Debug", "Response Code: ${response.code}")

                requireActivity().runOnUiThread {
                    progressDialog.dismiss()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("Upload Debug", "Response Body: $responseBody")
                        saveToFirestore(responseBody ?: "No response")
                    } else {
                        Log.e("Upload Error", "Upload failed with code: ${response.code}")
                        Log.e("Upload Error", "Response Message: ${response.message}")
                        showToast("Upload failed: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    progressDialog.dismiss()
                    Log.e("Upload Error", "Exception: ${e.message}")
                    showToast("Error uploading file: ${e.message}")
                }
            }
        }
    }

    // =================== EXISTING Method: progress dialog for uploading ===================
    private fun showLoadingDialog(): AlertDialog {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setView(LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading_upload, null))
            .setCancelable(false)
            .create()

        progressDialog.show()
        return progressDialog
    }

    // =================== EXISTING Method: save response to Firestore + navigate ===================
    private fun saveToFirestore(response: String) {
        val user = firebaseAuth.currentUser ?: return
        val userId: String
        val userCollection: String

        // Distinguish guest vs signed-in user
        if (user.isAnonymous) {
            userId = getDeviceId()
            userCollection = "guest_users"
        } else {
            userId = user.uid
            userCollection = "users"
        }

        val reportData = hashMapOf(
            "uploaded_at" to System.currentTimeMillis(),
            "file_url" to (selectedFileUri?.toString() ?: ""),
            "summary" to response
        )

        firestore.collection(userCollection).document(userId)
            .collection("reports").add(reportData)
            .addOnSuccessListener {
                showToast("Report uploaded successfully!")
                // Update daily count
                updateUploadCount(userId, userCollection)

                updateUploadLimitDisplay()

                // Navigate to result
                navigateToResults(response)
            }
            .addOnFailureListener {
                showToast("Failed to save report in Firestore")
            }
    }

    // =================== EXISTING Method: increment 'reportsUploaded' (we keep it if you want) ===================
    private fun updateUserReportsUploaded(userId: String) {
        val userRef = firestore.collection("users").document(userId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val currentCount = snapshot.getLong("reportsUploaded") ?: 0
            transaction.update(userRef, "reportsUploaded", currentCount + 1)
        }.addOnSuccessListener {
            Log.d("Firestore", "Successfully updated report count")
            showToast("Report count updated")
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Failed to update report count: ${e.message}")
            showToast("Failed to update report count")
        }
    }

    // =================== NEW PART: We also need to track daily count for user or guest ===================
    private fun updateUploadCount(userId: String, userCollection: String) {
        val docRef = firestore.collection(userCollection).document(userId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentCount = snapshot.getLong("uploads_today") ?: 0
            val lastUploadDay = snapshot.getLong("last_upload_day") ?: 0
            val todayDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)

            // If it's a new day, reset the count to 0
            if (lastUploadDay < todayDay) {
                transaction.update(docRef, mapOf("uploads_today" to 1, "last_upload_day" to todayDay))
            } else {
                transaction.update(docRef, "uploads_today", currentCount + 1)
            }
        }.addOnSuccessListener {
            Log.d("Firestore", "Upload count updated successfully")
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Failed to update upload count: ${e.message}")
        }
    }

    /**
     * After successful saving to Firestore, navigate to the result screen
     */
    private fun navigateToResults(response: String) {
        val resultsFragment = ResultsFragment()
        val bundle = Bundle()
        bundle.putString("summary", response)
        resultsFragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, resultsFragment)
            .addToBackStack(null)
            .commit()
    }

    // =================== NEW PART: Check limit before uploading (both user & guest) ===================
    private fun checkUploadLimitAndProceed() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            showToast("No user found!")
            return
        }

        val isGuest = user.isAnonymous
        val userId: String
        val userCollection: String
        val dailyLimit: Int

        if (isGuest) {
            userId = getDeviceId()
            userCollection = "guest_users"
            dailyLimit = 2 // 2 per day for guests
        } else {
            userId = user.uid
            userCollection = "users"
            dailyLimit = 5 // 5 per day for signed-in users
        }

        val docRef = firestore.collection(userCollection).document(userId)
        docRef.get().addOnSuccessListener { snapshot ->
            val uploadsToday = snapshot.getLong("uploads_today") ?: 0
            val lastUploadDay = snapshot.getLong("last_upload_day") ?: 0
            val todayDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)

            // If the document doesn't exist, create it first
            if (!snapshot.exists()) {
                docRef.set(
                    mapOf(
                        "uploads_today" to 0L,
                        "last_upload_day" to todayDay
                    )
                ).addOnSuccessListener {
                    Log.d("Firestore", "Created new document for user")
                }
            }

            // If it's a new day, reset count
            if (lastUploadDay < todayDay) {
                docRef.set(
                    mapOf("uploads_today" to 0L, "last_upload_day" to todayDay),
                    SetOptions.merge()
                )
            }

            // Check if user is at or beyond the limit
            if (uploadsToday >= dailyLimit && lastUploadDay == todayDay) {
                showLimitExceededDialog()
            } else {
                // Proceed with the upload
                uploadFileToServer(selectedFileUri!!)
            }
        }.addOnFailureListener {
            uploadFileToServer(selectedFileUri!!)
        }
    }

        private fun showLimitExceededDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Upload Limit Reached")
            .setMessage("You have reached your daily upload limit. Come back tomorrow.")
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * For guest users, generate a unique device-based ID
     */
    private fun getDeviceId(): String {
        // Possibly store in SharedPreferences if you want to keep the same device ID always
        // For demonstration, we use ANDROID_ID
        return Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
    }

    // =================== Existing method to create temp file from URI ===================
    private fun createTempFileFromUri(uri: Uri): File {
        val tempFile = File.createTempFile("upload_", ".tmp", requireContext().cacheDir)
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    // =================== Existing method: get file name from URI ===================
    private fun getFileName(uri: Uri): String {
        var name = "unknown_file"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }

    // =================== Existing method: show toast ===================
    private fun showToast(message: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
