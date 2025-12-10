package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.myapplication.databinding.ActivityAddMachineBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom

class AddMachineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMachineBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // karakter yang boleh digunakan untuk credential
    private val CREDENTIAL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val secureRandom = SecureRandom()

    private var generatedCredential: String? = null
    private var machineNameForNavigation: String? = null

    // Launcher untuk memilih lokasi penyimpanan
    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            if (uri != null && generatedCredential != null) {
                saveQrCodeToUri(uri, generatedCredential!!)
            } else {
                Toast.makeText(this, "Penyimpanan dibatalkan", Toast.LENGTH_SHORT).show()
                navigateBackToMain()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMachineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Tambah Mesin"

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnGetCurrentLocation.setOnClickListener {
            getCurrentLocation()
        }

        binding.btnSaveMachine.setOnClickListener {
            saveMachine()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    binding.etLatitude.setText(location.latitude.toString())
                    binding.etLongitude.setText(location.longitude.toString())
                    Toast.makeText(this, "Lokasi berhasil didapatkan", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Tidak bisa mendapatkan lokasi. Coba lagi.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveMachine() {
        val machineName = binding.etMachineName.text.toString().trim()
        val latitudeStr = binding.etLatitude.text.toString().trim()
        val longitudeStr = binding.etLongitude.text.toString().trim()

        // Validasi input
        if (machineName.isEmpty()) {
            binding.etMachineName.error = "Nama mesin harus diisi"
            return
        }

        if (latitudeStr.isEmpty()) {
            binding.etLatitude.error = "Latitude harus diisi"
            return
        }

        if (longitudeStr.isEmpty()) {
            binding.etLongitude.error = "Longitude harus diisi"
            return
        }

        val latitude = latitudeStr.toDoubleOrNull()
        val longitude = longitudeStr.toDoubleOrNull()

        if (latitude == null || longitude == null) {
            Toast.makeText(this, "Koordinat tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        // Validasi range koordinat
        if (latitude < -90 || latitude > 90) {
            binding.etLatitude.error = "Latitude harus antara -90 dan 90"
            return
        }

        if (longitude < -180 || longitude > 180) {
            binding.etLongitude.error = "Longitude harus antara -180 dan 180"
            return
        }

        // Generate credential otomatis
        val credential = generateCredential(20)
        generatedCredential = credential
        machineNameForNavigation = machineName

        // Generate mapLink dari koordinat
        val mapLink = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

        // Simpan ke Firestore
        val machineData = hashMapOf(
            "Credential" to credential,
            "Status" to false,
            "Latitude" to latitude,
            "Longitude" to longitude,
            "mapLink" to mapLink
        )

        db.collection("Data_Incenerator")
            .document(machineName)
            .set(machineData)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Mesin \"$machineName\" berhasil ditambahkan",
                    Toast.LENGTH_SHORT
                ).show()

                // Setelah sukses, tawarkan user untuk menyimpan QR Code
                askToSaveQrCode(credential)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menambahkan mesin: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun askToSaveQrCode(credential: String) {
        Toast.makeText(this, "Pilih lokasi untuk menyimpan QR Code", Toast.LENGTH_SHORT).show()
        createFileLauncher.launch("QR_$credential.png")
    }

    private fun saveQrCodeToUri(uri: Uri, credential: String) {
        try {
            val qrBitmap = generateQrBitmap(credential)
            val outputStream = contentResolver.openOutputStream(uri)

            if (outputStream != null) {
                outputStream.use { stream ->
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                Toast.makeText(this, "QR Code berhasil disimpan", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gagal menyimpan: OutputStream null", Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menyimpan QR Code: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        } finally {
            // Kembali ke MainActivity setelah selesai
            navigateBackToMain()
        }
    }

    private fun generateQrBitmap(data: String): Bitmap {
        val size = 512
        val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bmp
    }

    private fun navigateBackToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Permission ditolak", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // -----------------------
    // Fungsi helper: generate credential acak
    // -----------------------
    private fun generateCredential(length: Int): String {
        val sb = StringBuilder(length)
        repeat(length) {
            val idx = secureRandom.nextInt(CREDENTIAL_CHARS.length)
            sb.append(CREDENTIAL_CHARS[idx])
        }
        return sb.toString()
    }
}
