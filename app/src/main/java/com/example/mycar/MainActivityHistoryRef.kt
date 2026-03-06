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

class MainActivityHistoryRef : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var imageViewCancel: ImageView
    private lateinit var imageViewDelete: ImageView
    private lateinit var textViewTitle: TextView
    private lateinit var adapter: RefuelingAdapter

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
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val connectionHelper = ConnectionHelper()
                val connect: Connection? = connectionHelper.connectionclass()

                if (connect != null) {
                    val newRefuelings = mutableListOf<RefuelingItem>()

                    val query = """
                        SELECT r.refueling_id, r.car_id, r.date, r.mileage, r.volume, 
                               r.price_per_liter, r.total_amount, r.full_tank,
                               f.name as fuel_name, g.name as station_name
                        FROM Refueling r
                        LEFT JOIN Fuel f ON r.fuel_id = f.fuel_id
                        LEFT JOIN GasStations g ON r.station_id = g.station_id
                        WHERE r.car_id = ?
                        ORDER BY r.date DESC, r.refueling_id DESC
                    """.trimIndent()

                    val stmt: PreparedStatement = connect.prepareStatement(query)
                    stmt.setInt(1, currentCarId)
                    val rs: ResultSet = stmt.executeQuery()

                    while (rs.next()) {
                        newRefuelings.add(RefuelingItem(
                            id = rs.getInt("refueling_id"),
                            carId = rs.getInt("car_id"),
                            date = rs.getDate("date") ?: Date(),
                            fuelName = rs.getString("fuel_name") ?: "",
                            stationName = rs.getString("station_name") ?: "",
                            mileage = rs.getDouble("mileage"),
                            volume = rs.getDouble("volume"),
                            price = rs.getDouble("price_per_liter"),
                            total = rs.getDouble("total_amount"),
                            fullTank = rs.getBoolean("full_tank")
                        ))
                    }

                    rs.close()
                    stmt.close()
                    connect.close()

                    refuelings.clear()
                    refuelings.addAll(newRefuelings)

                    withContext(Dispatchers.Main) {
                        adapter.updateData(newRefuelings)
                        updateEmptyState()
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error loading refuelings: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
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
                val connectionHelper = ConnectionHelper()
                val connect: Connection? = connectionHelper.connectionclass()

                if (connect != null) {
                    connect.autoCommit = false

                    try {
                        val query = "DELETE FROM refueling WHERE refueling_id = ? AND car_id = ?"
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
                                    this@MainActivityHistoryRef,
                                    "Удалено записей: $deletedCount",
                                    Toast.LENGTH_SHORT
                                ).show()

                                adapter.setSelectionMode(false)
                                loadRefuelings()
                            } else {
                                Toast.makeText(
                                    this@MainActivityHistoryRef,
                                    "Ошибка при удалении",
                                    Toast.LENGTH_SHORT
                                ).show()
                                adapter.setSelectionMode(false)
                            }
                        }
                    } catch (ex: Exception) {
                        connect.rollback()
                        connect.autoCommit = true
                        throw ex
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting refuelings: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityHistoryRef,
                        "Ошибка: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
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