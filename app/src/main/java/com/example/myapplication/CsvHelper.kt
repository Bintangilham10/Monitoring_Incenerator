package com.example.myapplication

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CsvHelper {

    private const val FILE_NAME = "data_mesin.csv"

    fun appendScanData(
        context: Context,
        machineName: String,
        status: Boolean,
        username: String
    ) {
        val file = File(context.filesDir, FILE_NAME)

        // Jika file belum ada → buat header
        if (!file.exists()) {
            file.writeText("Tanggal,Nama Mesin,Status,User\n")
        }

        val dateTime = getCurrentDateTime()
        val statusText = if (status) "ON" else "OFF"

        val line = "$dateTime,$machineName,$statusText,$username\n"
        file.appendText(line)
    }

    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }
}
