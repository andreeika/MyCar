package com.example.mycar

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivityNotifications : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var closeImageView: ImageView
    private lateinit var recyclerViewUrgent: RecyclerView
    private lateinit var recyclerViewRecommendations: RecyclerView
    private lateinit var recyclerViewInfo: RecyclerView

    private lateinit var notificationManager: NotificationManager

    data class Notification(
        val id: Int,
        val type: NotificationType,
        val title: String,
        val message: String,
        val carId: Int,
        val carName: String,
        val date: Date,
        val isRead: Boolean = false,
        val actionRequired: Boolean = false
    )

    enum class NotificationType {
        URGENT, MAINTENANCE_RECOMMENDATION, INFO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_notifications)

        sharedPreferences = getSharedPreferences("my_car_prefs", MODE_PRIVATE)
        notificationManager = NotificationManager()
        initializeViews()
        setupClickListeners()
        setupStatusBarColors()
        loadNotifications()
    }

    private fun initializeViews() {
        closeImageView = findViewById(R.id.imageViewClose)
        recyclerViewUrgent = findViewById(R.id.recyclerViewUrgent)
        recyclerViewRecommendations = findViewById(R.id.recyclerViewRecommendations)
        recyclerViewInfo = findViewById(R.id.recyclerViewInfo)

        setupRecyclerViews()
    }

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun setupRecyclerViews() {
        recyclerViewUrgent.layoutManager = LinearLayoutManager(this)
        recyclerViewRecommendations.layoutManager = LinearLayoutManager(this)
        recyclerViewInfo.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        closeImageView.setOnClickListener {
            finish()
        }
    }

    private fun loadNotifications() {
        CoroutineScope(Dispatchers.IO).launch {
            try {

                val currentCarId = sharedPreferences.getInt("current_car_id", -1)

                val urgentNotifications = if (currentCarId > 0) {
                    filterNotificationsByCar(notificationManager.getUrgentNotifications(this@MainActivityNotifications), currentCarId)
                } else {
                    // Если автомобиль не выбран, показываем все уведомления пользователя
                    notificationManager.getUrgentNotifications(this@MainActivityNotifications)
                }

                val recommendations = if (currentCarId > 0) {
                    filterNotificationsByCar(notificationManager.getMaintenanceRecommendations(this@MainActivityNotifications), currentCarId)
                } else {
                    notificationManager.getMaintenanceRecommendations(this@MainActivityNotifications)
                }

                val infoNotifications = if (currentCarId > 0) {
                    filterNotificationsByCar(notificationManager.getInfoNotifications(this@MainActivityNotifications), currentCarId)
                } else {
                    notificationManager.getInfoNotifications(this@MainActivityNotifications)
                }

                withContext(Dispatchers.Main) {
                    updateUI(urgentNotifications, recommendations, infoNotifications)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivityNotifications, "Ошибка загрузки уведомлений", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterNotificationsByCar(notifications: List<Notification>, carId: Int): List<Notification> {
        return notifications.filter { it.carId == carId }
    }

    private fun updateUI(
        urgent: List<Notification>,
        recommendations: List<Notification>,
        info: List<Notification>
    ) {
        recyclerViewUrgent.adapter = NotificationAdapter(urgent) { notification ->
            onNotificationClick(notification)
        }

        recyclerViewRecommendations.adapter = NotificationAdapter(recommendations) { notification ->
            onNotificationClick(notification)
        }

        recyclerViewInfo.adapter = NotificationAdapter(info) { notification ->
            onNotificationClick(notification)
        }

        findViewById<View>(R.id.urgentNotificationsCard).visibility =
            if (urgent.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.maintenanceRecommendationsCard).visibility =
            if (recommendations.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.infoNotificationsCard).visibility =
            if (info.isEmpty()) View.GONE else View.VISIBLE

        if (urgent.isEmpty() && recommendations.isEmpty() && info.isEmpty()) {
            showNoNotificationsMessage()
        }
    }

    private fun onNotificationClick(notification: Notification) {
        when (notification.type) {
            NotificationType.URGENT -> showUrgentNotificationDialog(notification)
            NotificationType.MAINTENANCE_RECOMMENDATION -> showMaintenanceDialog(notification)
            NotificationType.INFO -> showInfoDialog(notification)
        }

        sharedPreferences.edit().putBoolean("notification_${notification.id}", true).apply()

        notificationManager.markAsRead(notification.id)

        loadNotifications()
    }

    private fun isNotificationRead(notificationId: Int): Boolean {
        return sharedPreferences.getBoolean("notification_$notificationId", false)
    }

    private fun showUrgentNotificationDialog(notification: Notification) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ " + notification.title)
            .setMessage("${notification.message}\n\nАвтомобиль: ${notification.carName}")
            .setPositiveButton("Перейти к обслуживанию") { dialog, which ->
                Toast.makeText(this, "Переход к обслуживанию", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showMaintenanceDialog(notification: Notification) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔧 " + notification.title)
            .setMessage("${notification.message}\n\nАвтомобиль: ${notification.carName}")
            .setPositiveButton("Запланировать ТО") { dialog, which ->
                Toast.makeText(this, "Планирование ТО", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun showInfoDialog(notification: Notification) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("ℹ️ " + notification.title)
            .setMessage("${notification.message}\n\nАвтомобиль: ${notification.carName}")
            .setPositiveButton("ОК", null)
            .show()
    }

    private fun showNoNotificationsMessage() {
        Toast.makeText(this, "Нет новых уведомлений", Toast.LENGTH_SHORT).show()
    }


    class NotificationAdapter(
        private val notifications: List<Notification>,
        private val onItemClick: (Notification) -> Unit
    ) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageViewIcon: ImageView = view.findViewById(R.id.imageViewIcon)
            val textViewTitle: TextView = view.findViewById(R.id.textViewTitle)
            val textViewMessage: TextView = view.findViewById(R.id.textViewMessage)
            val textViewDate: TextView = view.findViewById(R.id.textViewDate)
            val textViewCarInfo: TextView = view.findViewById(R.id.textViewCarInfo)
            val divider: View = view.findViewById(R.id.divider)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val notification = notifications[position]
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

            when (notification.type) {
                NotificationType.URGENT -> {
                    holder.imageViewIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                    holder.imageViewIcon.setColorFilter(Color.RED)
                }
                NotificationType.MAINTENANCE_RECOMMENDATION -> {
                    holder.imageViewIcon.setImageResource(android.R.drawable.ic_menu_edit)
                    holder.imageViewIcon.setColorFilter(Color.parseColor("#FF9800"))
                }
                NotificationType.INFO -> {
                    holder.imageViewIcon.setImageResource(android.R.drawable.ic_dialog_info)
                    holder.imageViewIcon.setColorFilter(Color.parseColor("#2196F3"))
                }
            }

            holder.textViewTitle.text = notification.title
            holder.textViewMessage.text = notification.message
            holder.textViewDate.text = dateFormat.format(notification.date)
            holder.textViewCarInfo.text = notification.carName
            holder.divider.visibility = if (position == notifications.size - 1) View.GONE else View.VISIBLE

            val context = holder.itemView.context
            val sharedPrefs = context.getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
            val isRead = sharedPrefs.getBoolean("notification_${notification.id}", false)

            if (!isRead) {
                holder.itemView.setBackgroundColor(Color.parseColor("#E3F2FD"))
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }

            holder.itemView.setOnClickListener {
                onItemClick(notification)
            }
        }

        override fun getItemCount() = notifications.size
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
    }
}