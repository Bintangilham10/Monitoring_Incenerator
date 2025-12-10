package com.example.myapplication

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExcelWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {

            val db = FirebaseFirestore.getInstance()

            // 🔥 Ambil data Firestore (sinkron)
            val snapshot = db.collection("Data_Incenerator").get().await()

            // 🔥 Lokasi file CSV
            val file = File(applicationContext.filesDir, "data_mesin.csv")

            // 🔥 Header CSV
            val header = "Tanggal,Nama Mesin,Status,User\n"
            file.writeText(header)

            // 🔥 Isi CSV
            snapshot.forEach { doc ->
                val tanggal = getTodayDate()
                val nama = doc.id
                val status = if (doc.getBoolean("Status") == true) "ON" else "OFF"
                val userId = doc.getString("userId")     // simpan UID saat update mesin
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId!!)
                    .get()
                    .addOnSuccessListener { userDoc ->
                        val username = userDoc.getString("name") ?: "-"
                    }

                val line = "$tanggal,$nama,$status,$userId\n"
                file.appendText(line)
            }

            Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return sdf.format(Date())
    }
}
