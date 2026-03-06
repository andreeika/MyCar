package com.example.mycar

import android.content.Context
import android.content.Intent
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

class RefuelingAdapter(
    private val context: Context,
    private var items: MutableList<RefuelingItem>
) : BaseAdapter() {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val selectedIds = mutableSetOf<Int>()
    private var selectionMode = false

    interface OnItemActionListener {
        fun onEditClick(item: RefuelingItem)
        fun onSelectionChanged(selectedCount: Int)
    }

    private var listener: OnItemActionListener? = null

    fun setOnItemActionListener(listener: OnItemActionListener) {
        this.listener = listener
    }

    fun updateData(newItems: List<RefuelingItem>) {
        items.clear()
        items.addAll(newItems)
        clearSelection()
        notifyDataSetChanged()
    }

    fun getSelectedIds(): List<Int> {
        return selectedIds.toList()
    }

    fun getSelectedItems(): List<RefuelingItem> {
        return items.filter { selectedIds.contains(it.id) }
    }

    fun toggleSelection(refuelingId: Int, isSelected: Boolean) {
        if (isSelected) {
            selectedIds.add(refuelingId)
        } else {
            selectedIds.remove(refuelingId)
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
        selectionMode = false
        listener?.onSelectionChanged(0)
        notifyDataSetChanged()
    }

    fun isSelected(refuelingId: Int): Boolean {
        return selectedIds.contains(refuelingId)
    }

    fun getSelectionMode(): Boolean = selectionMode

    fun setSelectionMode(mode: Boolean) {
        selectionMode = mode
        if (!mode) {
            clearSelection()
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): RefuelingItem = items[position]
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

        dateText.text = dateFormat.format(item.date)
        stationText.text = item.stationName
        fuelText.text = item.fuelName
        mileageText.text = "${item.mileage.toInt()} км"
        volumeText.text = "%.2f л".format(item.volume)
        priceText.text = "%.2f ₽/л".format(item.price)
        totalText.text = "%.2f ₽".format(item.total)
        fullTankText.visibility = if (item.fullTank) View.VISIBLE else View.GONE

        checkBox.isChecked = isSelected(item.id)

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            toggleSelection(item.id, isChecked)
        }

        btnEdit.setOnClickListener {
            if (!selectionMode) {
                listener?.onEditClick(item)
            }
        }

        val leftLayout = view.findViewById<LinearLayout>(R.id.leftLayout)
        leftLayout.setOnClickListener {
            if (selectionMode) {
                checkBox.isChecked = !checkBox.isChecked
            } else { }
        }

        leftLayout.setOnLongClickListener {
            if (!selectionMode) {
                setSelectionMode(true)
                checkBox.isChecked = true
                toggleSelection(item.id, true)
            }
            true
        }

        return view
    }
}

data class RefuelingItem(
    val id: Int,
    val carId: Int,
    val date: Date,
    val fuelName: String,
    val stationName: String,
    val mileage: Double,
    val volume: Double,
    val price: Double,
    val total: Double,
    val fullTank: Boolean
)