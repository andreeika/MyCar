package com.example.mycar

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class MaintenanceAdapter(
    private val context: Context,
    private var items: MutableList<MainActivityHistoryMainte.Maintenance>
) : BaseAdapter() {

    private val selectedIds = mutableSetOf<Int>()

    interface OnItemActionListener {
        fun onEditClick(item: MainActivityHistoryMainte.Maintenance)
        fun onSelectionChanged(selectedCount: Int)
    }

    private var listener: OnItemActionListener? = null

    fun setOnItemActionListener(listener: OnItemActionListener) {
        this.listener = listener
    }

    fun updateData(newItems: List<MainActivityHistoryMainte.Maintenance>) {
        items.clear()
        items.addAll(newItems)
        clearSelection()
        notifyDataSetChanged()
    }

    fun getSelectedIds(): List<Int> {
        return selectedIds.toList()
    }

    fun getSelectedItems(): List<MainActivityHistoryMainte.Maintenance> {
        return items.filter { selectedIds.contains(it.id) }
    }

    fun toggleSelection(maintenanceId: Int, isSelected: Boolean) {
        if (isSelected) {
            selectedIds.add(maintenanceId)
        } else {
            selectedIds.remove(maintenanceId)
        }
        listener?.onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(items.map { it.id })
        listener?.onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        listener?.onSelectionChanged(0)
        notifyDataSetChanged()
    }

    fun isSelected(maintenanceId: Int): Boolean {
        return selectedIds.contains(maintenanceId)
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): MainActivityHistoryMainte.Maintenance = items[position]
    override fun getItemId(position: Int): Long = items[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_refueling, parent, false)

        val item = items[position]
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
        val btnEdit: ImageView = view.findViewById(R.id.btnEdit)
        val dateText: TextView = view.findViewById(R.id.dateTextView)
        val stationText: TextView = view.findViewById(R.id.stationTextView)
        val fuelText: TextView = view.findViewById(R.id.fuelTextView)
        val mileageText: TextView = view.findViewById(R.id.mileageTextView)
        val volumeText: TextView = view.findViewById(R.id.volumeTextView)
        val priceText: TextView = view.findViewById(R.id.priceTextView)
        val totalText: TextView = view.findViewById(R.id.totalTextView)
        val fullTankText: TextView = view.findViewById(R.id.fullTankTextView)
        val leftLayout: LinearLayout = view.findViewById(R.id.leftLayout)

        dateText.text = item.date
        stationText.text = if (item.nextServiceDate.isNotEmpty())
            "След.: ${item.nextServiceDate}" else "След. дата не указана"
        stationText.setTextColor(context.getColor(R.color.my_home_bar_color))
        fuelText.text = if (item.serviceTypeName.isNotEmpty() && item.serviceTypeName != "null")
            item.serviceTypeName else "Тип не указан"
        fuelText.setTextColor(context.getColor(R.color.black))
        mileageText.text = "${item.mileage.toInt()} км"
        volumeText.text = if (item.description.isNotEmpty() && item.description != "null")
            item.description else "Без описания"
        volumeText.maxLines = 1
        volumeText.ellipsize = android.text.TextUtils.TruncateAt.END
        priceText.visibility = View.GONE
        totalText.text = "${String.format(Locale.getDefault(), "%.2f", item.totalAmount)} ₽"

        fullTankText.visibility = View.GONE
        checkBox.visibility = View.VISIBLE
        checkBox.isChecked = isSelected(item.id)

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            toggleSelection(item.id, isChecked)
        }

        btnEdit.setOnClickListener {
            listener?.onEditClick(item)
        }

        leftLayout.setOnClickListener {
            checkBox.isChecked = !checkBox.isChecked
        }

        leftLayout.setOnLongClickListener {
            checkBox.isChecked = !checkBox.isChecked
            true
        }

        return view
    }
}