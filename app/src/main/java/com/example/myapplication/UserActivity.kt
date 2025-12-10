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

    private val PICK_IMAGE = 100
    private var selectedImage: Uri? = null

    private val user = FirebaseAuth.getInstance().currentUser
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        imgProfile = findViewById(R.id.imgProfile)
        edtName = findViewById(R.id.edtName)
        tvCreatedAt = findViewById(R.id.tvCreatedAt)

        val btnPhoto = findViewById<Button>(R.id.btnUploadPhoto)
        val btnSaveName = findViewById<Button>(R.id.btnSaveName)

        loadUserData()

        btnPhoto.setOnClickListener { openGallery() }
        btnSaveName.setOnClickListener { saveName() }
    }

    private fun loadUserData() {
        val uid = user?.uid ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    edtName.setText(doc.getString("name") ?: "")

                    val url = doc.getString("photoUrl")
                    if (!url.isNullOrEmpty()) {
                        Picasso.get().load(url).into(imgProfile)
                    }
                }
            }

        val creation = user?.metadata?.creationTimestamp ?: 0L
        val date = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            .format(Date(creation))

        tvCreatedAt.text = "Tanggal dibuat: $date"
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
        val uid = user?.uid ?: return
        val fileRef = storage.reference.child("profile/$uid.jpg")

        selectedImage?.let { uri ->
            fileRef.putFile(uri).addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    db.collection("users").document(uid)
                        .update("photoUrl", downloadUrl.toString())
                }
            }
        }
    }

    private fun saveName() {
        val uid = user?.uid ?: return
        val name = edtName.text.toString()

        db.collection("users").document(uid)
            .update("name", name)
            .addOnSuccessListener {
                Toast.makeText(this, "Nama berhasil disimpan", Toast.LENGTH_SHORT).show()
            }
    }
}
