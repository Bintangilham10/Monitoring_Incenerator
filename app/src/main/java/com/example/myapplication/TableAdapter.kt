package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TableAdapter(private val dataList: List<TableRowData>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }

    override fun getItemCount(): Int = dataList.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_table_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_table_row, parent, false)
            RowViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        if (holder is RowViewHolder) {
            val row = dataList[position - 1]
            holder.tanggal.text = row.tanggal
            holder.mesin.text = row.namaMesin
            holder.status.text = row.status
            holder.user.text = row.user
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tanggal: TextView = view.findViewById(R.id.tvTanggal)
        val mesin: TextView = view.findViewById(R.id.tvMesin)
        val status: TextView = view.findViewById(R.id.tvStatus)
        val user: TextView = view.findViewById(R.id.tvUser)
    }
}
