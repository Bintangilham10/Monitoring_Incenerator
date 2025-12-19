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

class EditProfileActivity : AppCompatActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var imgEditPhoto: ImageView
    private lateinit var edtName: EditText
    private lateinit var tvUserIdReadonly: TextView
    private lateinit var btnUploadPhoto: Button
    private lateinit var btnSaveName: Button
    private lateinit var btnCancel: Button

    private val PICK_IMAGE = 100
    private var selectedImage: Uri? = null

    private val auth = FirebaseAuth.getInstance()
    private val user = auth.currentUser
    private val userEmail = user?.email

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile) // You'll need to create this layout

        imgProfile = findViewById(R.id.imgProfile)
        imgEditPhoto = findViewById(R.id.imgEditPhoto)
        edtName = findViewById(R.id.edtName)
        tvUserIdReadonly = findViewById(R.id.tvUserIdReadonly)
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto)
        btnSaveName = findViewById(R.id.btnSaveName)
        btnCancel = findViewById(R.id.btnCancel)

        if (user == null || userEmail == null) {
            Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadUserData()

        btnUploadPhoto.setOnClickListener { openGallery() }
        imgEditPhoto.setOnClickListener { openGallery() }
        btnSaveName.setOnClickListener { saveName() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun loadUserData() {
        val email = userEmail ?: return

        // Set readonly user ID
        tvUserIdReadonly.text = email

        db.collection("User").document(email)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    edtName.setText(doc.getString("username") ?: "")

                    val photoUrl = doc.getString("photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Picasso.get().load(photoUrl).into(imgProfile)
                    }
                }
            }
    }

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
                finish() // Return to profile view
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menyimpan nama", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            selectedImage = data?.data
            imgProfile.setImageURI(selectedImage)
            uploadImage()
        }
    }

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
                        .addOnSuccessListener {
                            Toast.makeText(this, "Foto berhasil diupload", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Upload foto gagal", Toast.LENGTH_SHORT).show()
            }
    }
}