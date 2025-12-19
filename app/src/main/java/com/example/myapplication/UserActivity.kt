package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class UserActivity : AppCompatActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var edtName: EditText
    private lateinit var tvCreatedAt: TextView
    private lateinit var btnUploadPhoto: Button
    private lateinit var btnSaveName: Button

    private val PICK_IMAGE = 100
    private var selectedImage: Uri? = null

    private val auth = FirebaseAuth.getInstance()
    private val user = auth.currentUser
    private val userEmail = user?.email

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        imgProfile = findViewById(R.id.imgProfile)
        edtName = findViewById(R.id.edtName)
        tvCreatedAt = findViewById(R.id.tvCreatedAt)
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto)
        btnSaveName = findViewById(R.id.btnSaveName)

        if (user == null || userEmail == null) {
            Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadUserData()

        btnUploadPhoto.setOnClickListener { openGallery() }
        btnSaveName.setOnClickListener { saveName() }
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
                    edtName.setText(doc.getString("username") ?: "")

                    val photoUrl = doc.getString("photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Picasso.get().load(photoUrl).into(imgProfile)
                    }
                } else {
                    createInitialUserData()
                }
            }

        val creationTime = user?.metadata?.creationTimestamp ?: return
        val date = SimpleDateFormat(
            "dd MMMM yyyy",
            Locale.getDefault()
        ).format(Date(creationTime))

        tvCreatedAt.text = "Akun dibuat: $date"
    }

    /* ======================================
           CREATE USER FIRST TIME
       ====================================== */
    private fun createInitialUserData() {
        val email = userEmail ?: return

        val data = hashMapOf(
            "email" to email,
            "username" to "",
            "photoUrl" to "",
            "createdAt" to Date()
        )

        db.collection("User").document(email).set(data)
    }

    /* ======================================
              SAVE USERNAME
       ====================================== */
    private fun saveName() {
        val email = userEmail ?: return
        val username = edtName.text.toString().trim()

        if (username.isEmpty()) {
            Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("User").document(email)
            .update("username", username)
            .addOnSuccessListener {
                Toast.makeText(this, "Nama berhasil disimpan", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menyimpan nama", Toast.LENGTH_SHORT).show()
            }
    }

    /* ======================================
               PICK IMAGE
       ====================================== */
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            selectedImage = data?.data
            imgProfile.setImageURI(selectedImage)
            uploadImage()
        }
    }

    /* ======================================
             UPLOAD PROFILE IMAGE
       ====================================== */
    private fun uploadImage() {
        val email = userEmail ?: return
        val uid = user?.uid ?: return
        val uri = selectedImage ?: return

        val fileRef = storage.reference.child("profile/$uid.jpg")

        fileRef.putFile(uri)
            .addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    db.collection("User").document(email)
                        .update("photoUrl", downloadUrl.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Upload foto gagal", Toast.LENGTH_SHORT).show()
            }
    }
}
