package com.example.myapplication

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.OutputStream

class DataActivity : AppCompatActivity() {

    private lateinit var btnRefresh: Button
    private lateinit var btnDownload: Button
    private lateinit var recyclerTable: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        btnRefresh = findViewById(R.id.btnRefreshCSV)
        btnDownload = findViewById(R.id.btnDownloadCSV)
        recyclerTable = findViewById(R.id.recyclerTable)

        loadCSVToTable()

        btnRefresh.setOnClickListener {
            loadCSVToTable()
        }

        btnDownload.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadCSV()
            }
        }
    }

    // ================= LOAD CSV KE TABEL =================
    private fun loadCSVToTable() {
        val file = File(filesDir, "data_mesin.csv")

        if (!file.exists()) {
            Toast.makeText(this, "File CSV belum ada", Toast.LENGTH_SHORT).show()
            recyclerTable.adapter = null
            return
        }

        val lines = file.readLines()

        if (lines.size <= 1) {
            Toast.makeText(this, "Data masih kosong", Toast.LENGTH_SHORT).show()
            recyclerTable.adapter = null
            return
        }

        val dataList = mutableListOf<TableRowData>()

        lines.drop(1).forEach { line ->
            val cols = line.split(",")
            if (cols.size >= 4) {
                dataList.add(
                    TableRowData(
                        tanggal = cols[0],
                        namaMesin = cols[1],
                        status = cols[2],
                        user = cols[3]
                    )
                )
            }
        }

        recyclerTable.layoutManager = LinearLayoutManager(this)
        recyclerTable.adapter = TableAdapter(dataList)
    }

    // ================= DOWNLOAD CSV =================
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadCSV() {

        val sourceFile = File(filesDir, "data_mesin.csv")

        if (!sourceFile.exists()) {
            Toast.makeText(this, "File CSV belum tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "data_mesin_${System.currentTimeMillis()}.csv"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        if (uri == null) {
            Toast.makeText(this, "Gagal membuat file", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val outputStream: OutputStream? = resolver.openOutputStream(uri)
            outputStream?.use { out ->
                out.write(sourceFile.readBytes())
            }

            Toast.makeText(
                this,
                "CSV berhasil disimpan di folder Download",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal menyimpan file", Toast.LENGTH_SHORT).show()
        }
    }
}
