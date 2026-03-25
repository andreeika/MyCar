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

class MainActivityHistoryMainte : BaseActivity() {

    private lateinit var listViewMaintenance: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var imageViewCancel: ImageView
    private lateinit var imageViewDelete: ImageView
    private lateinit var textViewTitle: TextView
    private lateinit var adapter: MaintenanceAdapter
    private lateinit var progressOverlay: android.widget.FrameLayout
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private var currentCarId: Int = 0
    private var currentCarModel: String = ""
    private val maintenanceList = mutableListOf<Maintenance>()
    private lateinit var sharedPreferences: android.content.SharedPreferences

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val TAG = "HistoryMainteActivity"

    companion object {
        private const val PREF_CAR_ID = "current_car_id"
        private const val PREF_CAR_NAME = "current_car_name"
        private const val REQUEST_CODE_MAINTENANCE_EDIT = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_history_mainte)

        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)
        currentCarId = sharedPreferences.getInt(PREF_CAR_ID, 0)
        currentCarModel = sharedPreferences.getString(PREF_CAR_NAME, "") ?: ""

        if (currentCarId == 0) {
            Toast.makeText(this, "Ошибка: автомобиль не выбран", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        loadMaintenanceFromDatabase()
    }

    private fun initializeViews() {
        listViewMaintenance = findViewById(R.id.listViewRefuelings)
        emptyTextView = findViewById(R.id.emptyTextView)
        imageViewCancel = findViewById(R.id.imageViewCancel)
        imageViewDelete = findViewById(R.id.imageViewDelete2)
        textViewTitle = findViewById(R.id.textView2)
        progressOverlay = findViewById(R.id.progressOverlay)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#228BE6"))
        swipeRefresh.setOnRefreshListener { loadMaintenanceFromDatabase() }

        textViewTitle.text = "История"

        adapter = MaintenanceAdapter(
            context = this,
            items = mutableListOf()
        )

        adapter.setOnItemActionListener(object : MaintenanceAdapter.OnItemActionListener {
            override fun onEditClick(item: Maintenance) {
                openEditActivity(item)
            }

            override fun onSelectionChanged(selectedCount: Int) {
                updateDeleteButtonState(selectedCount)
                updateTitle(selectedCount)
            }
        })

        listViewMaintenance.adapter = adapter
    }

    private fun updateTitle(selectedCount: Int) {
        if (selectedCount > 0) {
            textViewTitle.text = "Выбрано: $selectedCount"
        } else {
            textViewTitle.text = "История"
        }
    }

    private fun setupClickListeners() {
        // Кнопка "Назад"
        imageViewCancel.setOnClickListener {
            finish()
        }

        // Кнопка удаления
        imageViewDelete.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()

            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "Выберите записи для удаления", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showDeleteConfirmation(selectedIds)
        }
    }

    private fun loadMaintenanceFromDatabase() {
        progressOverlay.visibility = View.VISIBLE
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val arr = ApiClient.getMaintenance(currentCarId)
                val newMaintenanceList = mutableListOf<Maintenance>()
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val dateStr = obj.optString("date", "")
                    val formattedDate = try {
                        dateFormat.format(isoFormat.parse(dateStr) ?: return@launch)
                    } catch (e: Exception) { dateStr }

                    val nextDateStr = obj.optString("next_service_date", "")
                    val formattedNextDate = if (nextDateStr.isNotEmpty() && nextDateStr != "null") {
                        try { dateFormat.format(isoFormat.parse(nextDateStr)) }
                        catch (e: Exception) { "" }
                    } else ""

                    newMaintenanceList.add(Maintenance(
                        id = obj.getInt("maintenance_id"),
                        date = formattedDate,
                        mileage = obj.optDouble("mileage", 0.0),
                        totalAmount = obj.optDouble("total_amount", 0.0),
                        description = if (obj.isNull("description")) "" else obj.optString("description", ""),
                        nextServiceDate = formattedNextDate,
                        serviceTypeName = if (obj.isNull("service_type")) "" else obj.optString("service_type", "")
                    ))
                }

                maintenanceList.clear()
                maintenanceList.addAll(newMaintenanceList)

                withContext(Dispatchers.Main) {
                    adapter.updateData(newMaintenanceList)
                    progressOverlay.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    updateEmptyState()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading maintenance: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivityHistoryMainte,
                        "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                    emptyTextView.visibility = View.VISIBLE
                    emptyTextView.text = "Ошибка загрузки данных"
                }
            }
        }
    }

    private fun updateEmptyState() {
        if (adapter.count == 0) {
            listViewMaintenance.visibility = View.GONE
            emptyTextView.visibility = View.VISIBLE
            emptyTextView.text = "Нет записей об обслуживаниях"
        } else {
            listViewMaintenance.visibility = View.VISIBLE
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
                deleteSelectedMaintenance(ids)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteSelectedMaintenance(ids: List<Int>) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                var deletedCount = 0
                for (id in ids) {
                    try {
                        ApiClient.deleteMaintenance(currentCarId, id)
                        deletedCount++
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error deleting maintenance $id: ${ex.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    if (deletedCount > 0) {
                        Toast.makeText(this@MainActivityHistoryMainte, "Удалено записей: $deletedCount", Toast.LENGTH_SHORT).show()
                        adapter.clearSelection()
                        loadMaintenanceFromDatabase()
                    } else {
                        Toast.makeText(this@MainActivityHistoryMainte, "Ошибка при удалении", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting maintenance: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityHistoryMainte, "${friendlyError(ex)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openEditActivity(item: Maintenance) {
        try {
            val intent = Intent(this, MainActivityMaintenance::class.java).apply {
                putExtra("car_id", currentCarId)
                putExtra("car_model", currentCarModel)
                putExtra("maintenance_id", item.id)
                putExtra("mode", "edit")
            }
            startActivityForResult(intent, REQUEST_CODE_MAINTENANCE_EDIT)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening edit activity: ${e.message}", e)
            Toast.makeText(this, "Ошибка открытия редактора: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadMaintenanceFromDatabase()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_MAINTENANCE_EDIT && resultCode == RESULT_OK) {
            loadMaintenanceFromDatabase()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    data class Maintenance(
        val id: Int,
        val date: String,
        val mileage: Double,
        val totalAmount: Double,
        val description: String,
        val nextServiceDate: String,
        val serviceTypeName: String
    )
}