package com.example.myapplication

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExcelWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {

            val db = FirebaseFirestore.getInstance()

            // 🔹 Ambil data mesin dari Firestore (sinkron)
            val snapshot = db.collection("Data_Incenerator")
                .get()
                .await()

            // 🔹 Lokasi file CSV
            val file = File(applicationContext.filesDir, "data_mesin.csv")

            // 🔹 Header CSV
            file.writeText("Tanggal,Nama Mesin,Status,User\n")

            for (doc in snapshot.documents) {

                val tanggalWaktu = getCurrentDateTime()
                val namaMesin = doc.id
                val status = if (doc.getBoolean("Status") == true) "ON" else "OFF"
                val userId = doc.getString("userId")

                // 🔹 Ambil nama user berdasarkan UID (sinkron)
                val username = if (!userId.isNullOrEmpty()) {
                    val userDoc = db.collection("users")
                        .document(userId)
                        .get()
                        .await()

                    userDoc.getString("name") ?: "-"
                } else {
                    "-"
                }

                val line = "$tanggalWaktu,$namaMesin,$status,$username\n"
                file.appendText(line)
            }

            Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    /**
     * Format tanggal + jam + menit
     * Contoh: 19-12-2025 16:45
     */
    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }
}
