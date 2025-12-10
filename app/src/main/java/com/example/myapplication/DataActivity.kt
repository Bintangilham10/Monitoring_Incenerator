package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class DataActivity : AppCompatActivity() {

    private lateinit var btnRefresh: Button
    private lateinit var recyclerTable: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        btnRefresh = findViewById(R.id.btnRefreshCSV)
        recyclerTable = findViewById(R.id.recyclerTable)

        // 🔥 Load data CSV ke tabel saat halaman dibuka
        loadCSVToTable()

        // 🔥 Refresh data CSV saat tombol ditekan
        btnRefresh.setOnClickListener {
            loadCSVToTable()
        }
    }

    private fun loadCSVToTable() {
        val file = File(filesDir, "data_mesin.csv")

        if (!file.exists()) {
            Toast.makeText(this, "File CSV belum ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        val lines = file.readLines()

        if (lines.isEmpty()) {
            Toast.makeText(this, "CSV kosong", Toast.LENGTH_SHORT).show()
            return
        }

        // Skip header CSV
        val dataList = mutableListOf<TableRowData>()
        lines.drop(1).forEach { line ->

            if (line.isNotBlank()) {
                val cols = line.split(",")

                if (cols.size >= 4) {
                    dataList.add(
                        TableRowData(
                            tanggal = cols[0].trim(),
                            namaMesin = cols[1].trim(),
                            status = cols[2].trim(),
                            user = cols[3].trim()
                        )
                    )
                }
            }
        }

        // Set adapter
        recyclerTable.apply {
            layoutManager = LinearLayoutManager(this@DataActivity)
            adapter = TableAdapter(dataList)
        }
    }
}
