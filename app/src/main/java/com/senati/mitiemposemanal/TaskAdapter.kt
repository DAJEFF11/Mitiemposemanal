package com.senati.mitiemposemanal

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class TaskAdapter(private val context: Context, private val tasks: List<Triple<Int, String, String>>) : BaseAdapter() {

    override fun getCount(): Int = tasks.size

    override fun getItem(position: Int): Any = tasks[position]

    override fun getItemId(position: Int): Long = tasks[position].first.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_task_card, parent, false)

        val task = tasks[position]
        val tvTaskName = view.findViewById<TextView>(R.id.tvTaskName)
        val tvTaskCategory = view.findViewById<TextView>(R.id.tvTaskCategory)

        tvTaskName.text = task.second
        tvTaskCategory.text = task.third

        return view
    }
}