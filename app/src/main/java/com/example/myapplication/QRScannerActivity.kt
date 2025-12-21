package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.myapplication.databinding.ActivityQrScannerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.io.File


class QRScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var dialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnBackHome.setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /* ================= CAMERA ================= */

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(cameraExecutor, QRAnalyzer { qrText ->
                        runOnUiThread {
                            if (!dialogShown) {
                                dialogShown = true
                                showStatusDialog(qrText)
                            }
                        }
                    })
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer
            )

        }, ContextCompat.getMainExecutor(this))
    }

    /* ================= DIALOG ================= */

    private fun showStatusDialog(credential: String) {
        AlertDialog.Builder(this)
            .setTitle("Ubah Status Mesin")
            .setMessage("Pilih status mesin:")
            .setPositiveButton("Aktifkan") { d, _ ->
                updateMachineStatus(credential, true)
                d.dismiss()
            }
            .setNegativeButton("Nonaktifkan") { d, _ ->
                updateMachineStatus(credential, false)
                d.dismiss()
            }
            .setOnCancelListener { dialogShown = false }
            .show()
    }

    /* ================= UPDATE STATUS ================= */

    private fun updateMachineStatus(credential: String, status: Boolean) {

        val user = FirebaseAuth.getInstance().currentUser
            ?: return Toast.makeText(this, "User tidak login", Toast.LENGTH_SHORT).show()

        val uid = user.uid

        db.collection("Data_Incenerator")
            .whereEqualTo("Credential", credential)
            .get()
            .addOnSuccessListener { documents ->

                if (documents.isEmpty) {
                    Toast.makeText(this, "Mesin tidak ditemukan", Toast.LENGTH_SHORT).show()
                    dialogShown = false
                    return@addOnSuccessListener
                }

                for (doc in documents) {

                    val machineName = doc.id

                    db.collection("users")
                        .document(uid)
                        .get()
                        .addOnSuccessListener { userDoc ->

                            val userName =
                                userDoc.getString("name") ?: user.email ?: "Unknown"

                            val updateData = mapOf(
                                "Status" to status,
                                "userId" to uid,
                                "updatedAt" to System.currentTimeMillis()
                            )

                            db.collection("Data_Incenerator")
                                .document(machineName)
                                .update(updateData)
                                .addOnSuccessListener {

                                    // 🔥 TULIS CSV REALTIME
                                    appendLogToCSV(
                                        machineName,
                                        status,
                                        userName
                                    )

                                    Toast.makeText(
                                        this,
                                        "Status $machineName berhasil diubah",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    finish()
                                }
                        }
                }
            }
    }

    private fun appendLogToCSV(
        machineName: String,
        status: Boolean,
        userName: String
    ) {
        val file = File(filesDir, "data_mesin.csv")

        // Header (hanya sekali)
        if (!file.exists()) {
            file.writeText("Tanggal,Nama Mesin,Status,User\n")
        }

        val sdf = java.text.SimpleDateFormat(
            "dd-MM-yyyy HH:mm",
            java.util.Locale.getDefault()
        )
        val dateTime = sdf.format(java.util.Date())

        val statusText = if (status) "ON" else "OFF"

        val line = "$dateTime,$machineName,$statusText,$userName\n"
        file.appendText(line)
    }



    /* ================= SAVE LOG ================= */

    private fun saveMachineLog(machineName: String, status: Boolean, userId: String) {

        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        val dateTime = sdf.format(Date())

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->

                val userName = userDoc.getString("name") ?: "Unknown"
                val statusText = if (status) "ON" else "OFF"

                val logData = hashMapOf(
                    "machineName" to machineName,
                    "status" to statusText,
                    "userId" to userId,
                    "userName" to userName,
                    "dateTime" to dateTime,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("machine_logs")
                    .add(logData)
            }
    }

    /* ================= PERMISSION ================= */

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

/* ================= QR ANALYZER ================= */

private class QRAnalyzer(
    private val onQRCodeFound: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let {
                    onQRCodeFound(it)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
