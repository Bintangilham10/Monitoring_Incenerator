package com.example.myapplication

import android.Manifest
import android.content.DialogInterface
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.firebase.auth.FirebaseAuth

class QRScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private val db = FirebaseFirestore.getInstance()
    private var dialogShown = false // cegah popup muncul berulang

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Tombol kembali
        binding.btnBackHome.setOnClickListener { finish() }

        // Cek izin kamera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRAnalyzer { qrText ->
                        runOnUiThread {
                            if (!dialogShown) { // hanya sekali tiap scan
                                dialogShown = true
                                showStatusDialog(qrText)
                            }
                        }
                    })
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal memulai kamera", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // 🔹 Tampilkan popup konfirmasi status mesin
    private fun showStatusDialog(credential: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Ubah Status Mesin")
        builder.setMessage("\nPilih status mesin :")

        builder.setPositiveButton("Aktifkan") { dialog, _ ->
            updateMachineStatus(credential, true)
            dialog.dismiss()
        }

        builder.setNegativeButton("Nonaktifkan") { dialog, _ ->
            updateMachineStatus(credential, false)
            dialog.dismiss()
        }

        builder.setOnCancelListener {
            dialogShown = false // agar bisa scan ulang kalau dibatalkan
        }

        val dialog = builder.create()
        dialog.show()
    }

    // 🔹 Update status mesin di Firestore
    private fun updateMachineStatus(credential: String, status: Boolean) {

        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return Toast.makeText(this, "User tidak terautentikasi", Toast.LENGTH_SHORT).show()

        db.collection("Data_Incenerator")
            .whereEqualTo("Credential", credential)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "Mesin tidak ditemukan!", Toast.LENGTH_SHORT).show()
                    dialogShown = false
                    return@addOnSuccessListener
                }

                for (doc in documents) {

                    val updateData = mapOf(
                        "Status" to status,
                        "userId" to uid,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    db.collection("Data_Incenerator")
                        .document(doc.id)
                        .update(updateData)
                        .addOnSuccessListener {
                            val state = if (status) "Aktif" else "Non Aktif"
                            Toast.makeText(
                                this,
                                "Status mesin '${doc.id}' diubah ke $state",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Gagal mengubah status", Toast.LENGTH_SHORT).show()
                            dialogShown = false
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal membaca data mesin", Toast.LENGTH_SHORT).show()
                dialogShown = false
            }
    }


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

// 🔹 Analyzer QR Code
private class QRAnalyzer(private val onQRCodeFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let {
                        onQRCodeFound(it)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener { it.printStackTrace() }
            .addOnCompleteListener { imageProxy.close() }
    }
}
