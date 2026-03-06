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
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.util.*

class MainActivityHistoryMainte : AppCompatActivity() {

    private lateinit var listViewMaintenance: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var imageViewCancel: ImageView
    private lateinit var imageViewDelete: ImageView
    private lateinit var textViewTitle: TextView
    private lateinit var adapter: MaintenanceAdapter

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
        setupStatusBarColors()
        setupClickListeners()
        loadMaintenanceFromDatabase()
    }

    private fun initializeViews() {
        listViewMaintenance = findViewById(R.id.listViewRefuelings)
        emptyTextView = findViewById(R.id.emptyTextView)
        imageViewCancel = findViewById(R.id.imageViewCancel)
        imageViewDelete = findViewById(R.id.imageViewDelete2)
        textViewTitle = findViewById(R.id.textView2)

        textViewTitle.text = "История"

        // Инициализация адаптера
        adapter = MaintenanceAdapter(
            context = this,
            items = mutableListOf()
        )

        // Устанавливаем слушатель для адаптера
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

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
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
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    val query = """
                        SELECT 
                            m.maintenance_id,
                            m.date,
                            m.mileage,
                            m.total_amount,
                            m.description,
                            m.next_service_date,
                            st.name as service_type_name
                        FROM Maintenance m
                        LEFT JOIN ServiceTypes st ON m.service_type_id = st.service_type_id
                        WHERE m.car_id = ?
                        ORDER BY m.date DESC, m.maintenance_id DESC
                    """

                    val preparedStatement = connect.prepareStatement(query)
                    preparedStatement.setInt(1, currentCarId)
                    val resultSet: ResultSet = preparedStatement.executeQuery()

                    val newMaintenanceList = mutableListOf<Maintenance>()

                    while (resultSet.next()) {
                        val maintenanceId = resultSet.getInt("maintenance_id")
                        val date = resultSet.getDate("date")
                        val mileage = resultSet.getDouble("mileage")
                        val totalAmount = resultSet.getDouble("total_amount")
                        val description = resultSet.getString("description") ?: ""
                        val nextServiceDate = resultSet.getDate("next_service_date")
                        val serviceTypeName = resultSet.getString("service_type_name") ?: "Неизвестно"

                        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        val formattedDate = if (date != null) dateFormat.format(date) else "Нет данных"
                        val formattedNextDate = if (nextServiceDate != null) dateFormat.format(nextServiceDate) else "Не указана"

                        newMaintenanceList.add(Maintenance(
                            maintenanceId,
                            formattedDate,
                            mileage,
                            totalAmount,
                            description,
                            formattedNextDate,
                            serviceTypeName
                        ))
                    }

                    resultSet.close()
                    preparedStatement.close()
                    connect.close()

                    maintenanceList.clear()
                    maintenanceList.addAll(newMaintenanceList)

                    withContext(Dispatchers.Main) {
                        adapter.updateData(newMaintenanceList)
                        updateEmptyState()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivityHistoryMainte,
                            "Ошибка подключения к БД", Toast.LENGTH_SHORT).show()
                        emptyTextView.visibility = View.VISIBLE
                        emptyTextView.text = "Ошибка подключения к базе данных"
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading maintenance: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityHistoryMainte,
                        "Ошибка загрузки: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                    emptyTextView.visibility = View.VISIBLE
                    emptyTextView.text = "Ошибка: ${ex.localizedMessage}"
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
                val connectionHelper = ConnectionHelper()
                val connect = connectionHelper.connectionclass()

                if (connect != null) {
                    connect.autoCommit = false

                    try {
                        val query = "DELETE FROM Maintenance WHERE maintenance_id = ? AND car_id = ?"
                        val stmt: PreparedStatement = connect.prepareStatement(query)

                        var deletedCount = 0
                        for (id in ids) {
                            stmt.setInt(1, id)
                            stmt.setInt(2, currentCarId)
                            deletedCount += stmt.executeUpdate()
                        }

                        stmt.close()

                        if (deletedCount > 0) {
                            connect.commit()
                        } else {
                            connect.rollback()
                        }

                        connect.autoCommit = true
                        connect.close()

                        withContext(Dispatchers.Main) {
                            if (deletedCount > 0) {
                                Toast.makeText(
                                    this@MainActivityHistoryMainte,
                                    "Удалено записей: $deletedCount",
                                    Toast.LENGTH_SHORT
                                ).show()

                                adapter.clearSelection()
                                loadMaintenanceFromDatabase()
                            } else {
                                Toast.makeText(
                                    this@MainActivityHistoryMainte,
                                    "Ошибка при удалении",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (ex: Exception) {
                        connect.rollback()
                        connect.autoCommit = true
                        throw ex
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting maintenance: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityHistoryMainte,
                        "Ошибка: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
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