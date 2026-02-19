package com.example.mycar

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import java.util.Locale

class MaintenanceAdapter(
    context: Context,
    private val items: List<MainActivityHistoryMainte.Maintenance>
) : ArrayAdapter<MainActivityHistoryMainte.Maintenance>(context, R.layout.item_refueling, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_refueling, parent, false)

        val maintenance = items[position]

        view.findViewById<TextView>(R.id.dateTextView).text = maintenance.date
        view.findViewById<TextView>(R.id.stationTextView).text = "След.: ${maintenance.nextServiceDate}"

        view.findViewById<TextView>(R.id.fuelTextView).text = maintenance.serviceTypeName
        view.findViewById<TextView>(R.id.mileageTextView).text =
            "${String.format(Locale.getDefault(), "%.0f", maintenance.mileage)} км"

        view.findViewById<TextView>(R.id.volumeTextView).text =
            if (maintenance.description.isNotEmpty()) maintenance.description else "Без описания"

        view.findViewById<TextView>(R.id.priceTextView).visibility = View.GONE

        view.findViewById<TextView>(R.id.totalTextView).text =
            "${String.format(Locale.getDefault(), "%.2f", maintenance.totalAmount)} ₽"
        view.findViewById<TextView>(R.id.fullTankTextView).visibility = View.GONE

        return view
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): MainActivityHistoryMainte.Maintenance {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return items[position].id.toLong()
    }
}