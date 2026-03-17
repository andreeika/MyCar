package com.example.mycar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivityHistoryRef : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var imageViewCancel: ImageView
    private lateinit var imageViewDelete: ImageView
    private lateinit var textViewTitle: TextView
    private lateinit var adapter: RefuelingAdapter
    private lateinit var progressOverlay: android.widget.FrameLayout

    private var currentCarId: Int = 0
    private var currentCarModel: String = ""
    private val refuelings = mutableListOf<RefuelingItem>()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val TAG = "HistoryRefActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_history_ref)

        currentCarId = intent.getIntExtra("car_id", 0)
        currentCarModel = intent.getStringExtra("car_model") ?: ""

        if (currentCarId == 0) {
            Toast.makeText(this, "Ошибка: автомобиль не выбран", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupToolbar()
        loadRefuelings()
    }

    private fun initViews() {
        listView = findViewById(R.id.listViewRefuelings)
        emptyTextView = findViewById(R.id.emptyTextView)
        imageViewCancel = findViewById(R.id.imageViewCancel)
        imageViewDelete = findViewById(R.id.imageViewDelete)
        textViewTitle = findViewById(R.id.textView2)
        progressOverlay = findViewById(R.id.progressOverlay)

        adapter = RefuelingAdapter(
            context = this,
            items = mutableListOf()
        )

        adapter.setOnItemActionListener(object : RefuelingAdapter.OnItemActionListener {
            override fun onEditClick(item: RefuelingItem) {
                openEditActivity(item)
            }

            override fun onSelectionChanged(selectedCount: Int) {
                updateDeleteButtonState(selectedCount)
                updateTitle(selectedCount)
            }
        })

        listView.adapter = adapter

        imageViewCancel.setOnClickListener {
            if (adapter.getSelectionMode()) {
                adapter.setSelectionMode(false)
            } else {
                finish()
            }
        }

        imageViewDelete.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()

            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "Выберите записи для удаления", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showDeleteConfirmation(selectedIds)
        }
    }

    private fun updateTitle(selectedCount: Int) {
        if (selectedCount > 0) {
            textViewTitle.text = "Выбрано: $selectedCount"
        } else {
            textViewTitle.text = "История заправок"
        }
    }

    private fun setupToolbar() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun loadRefuelings() {
        progressOverlay.visibility = View.VISIBLE
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val arr = ApiClient.getRefueling(currentCarId)
                val newRefuelings = mutableListOf<RefuelingItem>()
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val dateStr = obj.optString("date", "")
                    val parsedDate = try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: Date()
                    } catch (e: Exception) { Date() }

                    newRefuelings.add(RefuelingItem(
                        id = obj.getInt("refueling_id"),
                        carId = currentCarId,
                        date = parsedDate,
                        fuelName = obj.optString("fuel", ""),
                        stationName = obj.optString("station", ""),
                        mileage = obj.optDouble("mileage", 0.0),
                        volume = obj.optDouble("volume", 0.0),
                        price = obj.optDouble("price_per_liter", 0.0),
                        total = obj.optDouble("total_amount", 0.0),
                        fullTank = obj.optBoolean("full_tank", false)
                    ))
                }

                refuelings.clear()
                refuelings.addAll(newRefuelings)

                withContext(Dispatchers.Main) {
                    adapter.updateData(newRefuelings)
                    progressOverlay.visibility = View.GONE
                    updateEmptyState()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading refuelings: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivityHistoryRef,
                        "Ошибка загрузки: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateEmptyState() {
        if (adapter.count == 0) {
            listView.visibility = View.GONE
            emptyTextView.visibility = View.VISIBLE
        } else {
            listView.visibility = View.VISIBLE
            emptyTextView.visibility = View.GONE
        }
    }

    private fun updateDeleteButtonState(selectedCount: Int) {
        if (selectedCount > 0) {
            imageViewDelete.isEnabled = true
            imageViewDelete.alpha = 1f
            imageViewDelete.contentDescription = "Удалить записей: $selectedCount"
        } else {
            imageViewDelete.isEnabled = false
            imageViewDelete.alpha = 0.5f
            imageViewDelete.contentDescription = "Удалить"
        }
    }

    private fun showDeleteConfirmation(ids: List<Int>) {
        val count = ids.size
        AlertDialog.Builder(this)
            .setTitle("Удаление записей")
            .setMessage("Вы уверены, что хотите удалить $count записей?\nЭто действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteSelectedRefuelings(ids)
            }
            .setNegativeButton("Отмена") { _, _ ->
                adapter.setSelectionMode(false)
            }
            .setOnCancelListener {
                adapter.setSelectionMode(false)
            }
            .show()
    }

    private fun deleteSelectedRefuelings(ids: List<Int>) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                var deletedCount = 0
                for (id in ids) {
                    try {
                        ApiClient.deleteRefueling(currentCarId, id)
                        deletedCount++
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error deleting refueling $id: ${ex.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    if (deletedCount > 0) {
                        Toast.makeText(this@MainActivityHistoryRef, "Удалено записей: $deletedCount", Toast.LENGTH_SHORT).show()
                        adapter.setSelectionMode(false)
                        loadRefuelings()
                    } else {
                        Toast.makeText(this@MainActivityHistoryRef, "Ошибка при удалении", Toast.LENGTH_SHORT).show()
                        adapter.setSelectionMode(false)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting refuelings: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityHistoryRef, "Ошибка: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                    adapter.setSelectionMode(false)
                }
            }
        }
    }

    private fun openEditActivity(item: RefuelingItem) {
        val intent = Intent(this, MainActivityRefueling::class.java).apply {
            putExtra("car_id", currentCarId)
            putExtra("car_model", currentCarModel)
            putExtra("refueling_id", item.id)
            putExtra("mode", "edit")
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        adapter.setSelectionMode(false)
        loadRefuelings()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}