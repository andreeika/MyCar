package com.example.mycar

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

class MainActivityNotifications : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var closeImageView: ImageView
    private lateinit var recyclerViewUrgent: RecyclerView
    private lateinit var recyclerViewRecommendations: RecyclerView
    private lateinit var recyclerViewInfo: RecyclerView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private lateinit var notificationManager: NotificationManager
    private var isLoaded = false

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
        progressOverlay = findViewById(R.id.progressOverlay)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#228BE6"))
        swipeRefresh.setOnRefreshListener { loadNotifications() }

        recyclerViewUrgent.layoutManager = LinearLayoutManager(this)
        recyclerViewRecommendations.layoutManager = LinearLayoutManager(this)
        recyclerViewInfo.layoutManager = LinearLayoutManager(this)
    }

    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }

    private fun setupClickListeners() {
        closeImageView.setOnClickListener { finish() }
    }

    private fun loadNotifications() {
        progressOverlay.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val all = notificationManager.getAllNotifications(this@MainActivityNotifications)
                progressOverlay.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                updateUI(all.urgent, all.recommendations, all.info)
                isLoaded = true
            } catch (ex: Exception) {
                ex.printStackTrace()
                progressOverlay.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                Toast.makeText(this@MainActivityNotifications, "Ошибка загрузки уведомлений", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(
        urgent: List<Notification>,
        recommendations: List<Notification>,
        info: List<Notification>
    ) {
        recyclerViewUrgent.adapter = NotificationAdapter(urgent) { onNotificationClick(it) }
        recyclerViewRecommendations.adapter = NotificationAdapter(recommendations) { onNotificationClick(it) }
        recyclerViewInfo.adapter = NotificationAdapter(info) { onNotificationClick(it) }

        findViewById<View>(R.id.urgentNotificationsCard).visibility =
            if (urgent.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.maintenanceRecommendationsCard).visibility =
            if (recommendations.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.infoNotificationsCard).visibility =
            if (info.isEmpty()) View.GONE else View.VISIBLE

        if (urgent.isEmpty() && recommendations.isEmpty() && info.isEmpty()) {
            findViewById<View>(R.id.textViewEmpty).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.textViewEmpty).visibility = View.GONE
        }
    }

    private fun onNotificationClick(notification: Notification) {
        notificationManager.markAsRead(this, notification.id)

        when (notification.type) {
            NotificationType.URGENT -> showUrgentNotificationDialog(notification)
            NotificationType.MAINTENANCE_RECOMMENDATION -> showMaintenanceDialog(notification)
            NotificationType.INFO -> showInfoDialog(notification)
        }
    }

    private fun showUrgentNotificationDialog(notification: Notification) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ " + notification.title)
            .setMessage("${notification.message}\n\nАвтомобиль: ${notification.carName}")
            .setPositiveButton("Перейти к обслуживанию") { _, _ ->
                Toast.makeText(this, "Переход к обслуживанию", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showMaintenanceDialog(notification: Notification) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔧 " + notification.title)
            .setMessage("${notification.message}\n\nАвтомобиль: ${notification.carName}")
            .setPositiveButton("Запланировать ТО") { _, _ ->
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

    class NotificationAdapter(
        private val notifications: List<Notification>,
        private val onItemClick: (Notification) -> Unit
    ) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: com.google.android.material.card.MaterialCardView =
                (view as android.widget.FrameLayout).getChildAt(0)
                    as com.google.android.material.card.MaterialCardView
            val divider: View = view.findViewById(R.id.divider)
            val iconBackground: View = view.findViewById(R.id.iconBackground)
            val imageViewIcon: ImageView = view.findViewById(R.id.imageViewIcon)
            val textViewTitle: TextView = view.findViewById(R.id.textViewTitle)
            val textViewMessage: TextView = view.findViewById(R.id.textViewMessage)
            val textViewDate: TextView = view.findViewById(R.id.textViewDate)
            val textViewCarInfo: TextView = view.findViewById(R.id.textViewCarInfo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val notification = notifications[position]
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

            val (accentColor, iconRes, bgColor) = when (notification.type) {
                NotificationType.URGENT -> Triple(
                    Color.parseColor("#F44336"),
                    android.R.drawable.ic_dialog_alert,
                    Color.parseColor("#FFF5F5")
                )
                NotificationType.MAINTENANCE_RECOMMENDATION -> Triple(
                    Color.parseColor("#FF9800"),
                    android.R.drawable.ic_menu_edit,
                    Color.parseColor("#FFF8F0")
                )
                NotificationType.INFO -> Triple(
                    Color.parseColor("#228BE6"),
                    android.R.drawable.ic_dialog_info,
                    Color.parseColor("#F0F4FF")
                )
            }

            // Цветная полоса слева
            holder.divider.setBackgroundColor(accentColor)

            // Иконка и фон круга
            holder.imageViewIcon.setImageResource(iconRes)
            holder.imageViewIcon.setColorFilter(accentColor)
            holder.iconBackground.setBackgroundColor(Color.argb(30,
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)))

            holder.textViewTitle.text = notification.title
            holder.textViewMessage.text = notification.message
            holder.textViewDate.text = dateFormat.format(notification.date)
            holder.textViewCarInfo.text = "🚗 ${notification.carName}"

            val isRead = holder.itemView.context
                .getSharedPreferences("my_car_prefs", Context.MODE_PRIVATE)
                .getBoolean("notification_${notification.id}", false)
            holder.card.setCardBackgroundColor(if (!isRead) bgColor else Color.WHITE)

            holder.itemView.setOnClickListener { onItemClick(notification) }
        }

        override fun getItemCount() = notifications.size
    }
}
