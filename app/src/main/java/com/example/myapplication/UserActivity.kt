package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class UserActivity : AppCompatActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var tvFullName: TextView
    private lateinit var tvUserId: TextView
    private lateinit var tvLastLogin: TextView
    private lateinit var tvCreatedAt: TextView
    private lateinit var btnEditProfile: Button

    private val auth = FirebaseAuth.getInstance()
    private val user = auth.currentUser
    private val userEmail = user?.email

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        imgProfile = findViewById(R.id.imgProfile)
        tvFullName = findViewById(R.id.tvFullName)
        tvUserId = findViewById(R.id.tvUserId)
        tvLastLogin = findViewById(R.id.tvLastLogin)
        tvCreatedAt = findViewById(R.id.tvCreatedAt)
        btnEditProfile = findViewById(R.id.btnEditProfile)

        if (user == null || userEmail == null) {
            Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadUserData()

        btnEditProfile.setOnClickListener {
            // Navigate to edit profile screen (you'll need to create this)
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }
    }

    /* ======================================
                LOAD USER DATA
       ====================================== */
    private fun loadUserData() {
        val email = userEmail ?: return

        db.collection("User").document(email)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val username = doc.getString("username") ?: "User Name"
                    tvFullName.text = username

                    val photoUrl = doc.getString("photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Picasso.get().load(photoUrl).into(imgProfile)
                    }
                } else {
                    createInitialUserData()
                }
            }

        // Set User ID (email)
        tvUserId.text = email

        // Set Last Login (current time)
        val currentTime = SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.getDefault()
        ).format(Date())
        tvLastLogin.text = currentTime

        // Set Account Created Date
        val creationTime = user?.metadata?.creationTimestamp ?: return
        val date = SimpleDateFormat(
            "MMMM dd, yyyy",
            Locale.getDefault()
        ).format(Date(creationTime))
        tvCreatedAt.text = date
    }

    /* ======================================
           CREATE USER FIRST TIME
       ====================================== */
    private fun createInitialUserData() {
        val email = userEmail ?: return

        val data = hashMapOf(
            "email" to email,
            "username" to "User Name",
            "photoUrl" to "",
            "createdAt" to Date()
        )

        db.collection("User").document(email).set(data)
            .addOnSuccessListener {
                loadUserData() // Reload after creating
            }
    }

    override fun onResume() {
        super.onResume()
        // Reload data when returning from edit screen
        loadUserData()
    }
}