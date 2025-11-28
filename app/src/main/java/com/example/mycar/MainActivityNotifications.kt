package com.example.mycar

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        notificationManager = NotificationManager() // Инициализация менеджера уведомлений
        initializeViews()
        setupClickListeners()
        loadNotifications()
    }

    private fun initializeViews() {
        closeImageView = findViewById(R.id.imageViewClose)
        recyclerViewUrgent = findViewById(R.id.recyclerViewUrgent)
        recyclerViewRecommendations = findViewById(R.id.recyclerViewRecommendations)
        recyclerViewInfo = findViewById(R.id.recyclerViewInfo)

        setupRecyclerViews()
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
                // Используем NotificationManager для загрузки уведомлений из БД
                val urgentNotifications = notificationManager.getUrgentNotifications(this@MainActivityNotifications)
                val recommendations = notificationManager.getMaintenanceRecommendations(this@MainActivityNotifications)
                val infoNotifications = notificationManager.getInfoNotifications(this@MainActivityNotifications)

                withContext(Dispatchers.Main) {
                    updateUI(urgentNotifications, recommendations, infoNotifications)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Показываем тестовые данные при ошибке
                    Toast.makeText(this@MainActivityNotifications, "Ошибка загрузки уведомлений", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

        // Скрываем карточки если нет уведомлений
        findViewById<View>(R.id.urgentNotificationsCard).visibility =
            if (urgent.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.maintenanceRecommendationsCard).visibility =
            if (recommendations.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.infoNotificationsCard).visibility =
            if (info.isEmpty()) View.GONE else View.VISIBLE

        // Показываем сообщение если нет уведомлений вообще
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

        // Помечаем как прочитанное
        notificationManager.markAsRead(notification.id)

        // Обновляем UI чтобы убрать подсветку
        loadNotifications()
    }

    private fun showUrgentNotificationDialog(notification: Notification) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ " + notification.title)
            .setMessage(notification.message)
            .setPositiveButton("Перейти к обслуживанию") { dialog, which ->
                // Здесь можно добавить переход к экрану обслуживания
                // val intent = Intent(this, MainActivityMaintenance::class.java)
                // startActivity(intent)
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showMaintenanceDialog(notification: Notification) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔧 " + notification.title)
            .setMessage(notification.message)
            .setPositiveButton("Запланировать ТО") { dialog, which ->
                // Здесь можно добавить переход к экрану планирования ТО
                // val intent = Intent(this, MainActivityMaintenance::class.java)
                // startActivity(intent)
            }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun showInfoDialog(notification: Notification) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("ℹ️ " + notification.title)
            .setMessage(notification.message)
            .setPositiveButton("ОК", null)
            .show()
    }

    private fun showNoNotificationsMessage() {
        // Можно добавить TextView с сообщением "Нет уведомлений"
        Toast.makeText(this, "Нет новых уведомлений", Toast.LENGTH_SHORT).show()
    }



    // Адаптер для уведомлений
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

            // Устанавливаем иконку в зависимости от типа
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

            // Скрываем разделитель для последнего элемента
            holder.divider.visibility = if (position == notifications.size - 1) View.GONE else View.VISIBLE

            // Подсвечиваем непрочитанные уведомления
            if (!notification.isRead) {
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
}