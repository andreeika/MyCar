package com.example.mycar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.text.DecimalFormat

class RefuelingAdapter(
    private val context: android.content.Context,
    private val refuelings: List<MainActivityHistoryRef.Refueling>
) : BaseAdapter() {

    private val decimalFormat = DecimalFormat("#,##0.00")
    private val mileageFormat = DecimalFormat("#,##0")

    override fun getCount(): Int = refuelings.size

    override fun getItem(position: Int): Any = refuelings[position]

    override fun getItemId(position: Int): Long = refuelings[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_refueling, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val refueling = refuelings[position]

        holder.dateTextView.text = refueling.date
        holder.stationTextView.text = refueling.stationName
        holder.fuelTextView.text = refueling.fuelName

        holder.mileageTextView.text = "${mileageFormat.format(refueling.mileage)} км"
        holder.volumeTextView.text = "${decimalFormat.format(refueling.volume)} л"
        holder.priceTextView.text = "${decimalFormat.format(refueling.pricePerLiter)} ₽/л"
        holder.totalTextView.text = "${decimalFormat.format(refueling.totalAmount)} ₽"

        if (refueling.fullTank) {
            holder.fullTankTextView.visibility = View.VISIBLE
        } else {
            holder.fullTankTextView.visibility = View.GONE
        }

        return view
    }

    private class ViewHolder(view: View) {
        val dateTextView: TextView = view.findViewById(R.id.dateTextView)
        val stationTextView: TextView = view.findViewById(R.id.stationTextView)
        val fuelTextView: TextView = view.findViewById(R.id.fuelTextView)
        val mileageTextView: TextView = view.findViewById(R.id.mileageTextView)
        val volumeTextView: TextView = view.findViewById(R.id.volumeTextView)
        val priceTextView: TextView = view.findViewById(R.id.priceTextView)
        val totalTextView: TextView = view.findViewById(R.id.totalTextView)
        val fullTankTextView: TextView = view.findViewById(R.id.fullTankTextView)
    }
}