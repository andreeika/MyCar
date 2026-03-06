package com.example.mycar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class MainActivityHelp : AppCompatActivity() {

    private lateinit var expandableListView: ExpandableListView
    private lateinit var expandableListAdapter: SimpleExpandableListAdapter
    private lateinit var listDataHeader: ArrayList<String>
    private lateinit var listDataChild: HashMap<String, List<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_help)
        setupStatusBarColors()
        // Настройка Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Помощь"

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Кнопка обратной связи
        val fabContact = findViewById<FloatingActionButton>(R.id.fabContact)
        fabContact.setOnClickListener {
            showContactDialog()
        }

        // Инициализация ExpandableListView
        expandableListView = findViewById(R.id.expandableListView)
        prepareListData()

        // Создаем адаптер с правильными типами
        val groupList = ArrayList<HashMap<String, String>>()
        for (header in listDataHeader) {
            val groupMap = HashMap<String, String>()
            groupMap["HEADER"] = header
            groupList.add(groupMap)
        }

        val childList = ArrayList<ArrayList<HashMap<String, String>>>()
        for (header in listDataHeader) {
            val childGroup = ArrayList<HashMap<String, String>>()
            listDataChild[header]?.forEach { child ->
                val childMap = HashMap<String, String>()
                childMap["HEADER"] = header
                childMap["CHILD"] = child
                childGroup.add(childMap)
            }
            childList.add(childGroup)
        }

        expandableListAdapter = SimpleExpandableListAdapter(
            this,
            groupList,
            android.R.layout.simple_expandable_list_item_1,
            arrayOf("HEADER"),
            intArrayOf(android.R.id.text1),
            childList,
            android.R.layout.simple_expandable_list_item_2,
            arrayOf("HEADER", "CHILD"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )

        expandableListView.setAdapter(expandableListAdapter)

        // Обработчик кликов
        expandableListView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
            onQuestionClicked(groupPosition, childPosition)
            false
        }
    }
    private fun setupStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.my_home_bar_color)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.my_status_bar_color)
        }
    }
    private fun prepareListData() {
        listDataHeader = ArrayList()
        listDataChild = HashMap()

        // Добавляем заголовки разделов
        listDataHeader.add("- Общие вопросы")
        listDataHeader.add("- Работа с автомобилем")
        listDataHeader.add("- Заправка и обслуживание")
        listDataHeader.add("- Статистика и отчеты")
        listDataHeader.add("- Техническая поддержка")

        // Добавляем данные для каждого раздела
        val generalQuestions: List<String> = listOf(
            "Как добавить новый автомобиль?",
            "Как переключаться между автомобилями?",
            "Как отредактировать информацию об автомобиле?",
            "Как удалить автомобиль?"
        )

        val carQuestions: List<String> = listOf(
            "Как ввести показания пробега?",
            "Как загрузить фотографию автомобиля?",
            "Какие данные об автомобиле можно сохранить?"
        )

        val fuelQuestions: List<String> = listOf(
            "Как добавить запись о заправке?",
            "Как рассчитать расход топлива?",
            "Как добавить напоминание о ТО?",
            "Как часто нужно проходить обслуживание?"
        )

        val statsQuestions: List<String> = listOf(
            "Какие отчеты доступны в приложении?",
            "Как экспортировать данные?",
            "За какой период доступна статистика?"
        )

        val supportQuestions: List<String> = listOf(
            "Как связаться с поддержкой?",
            "Как сообщить об ошибке?",
            "Где найти руководство пользователя?"
        )

        // Связываем заголовки с данными
        listDataChild[listDataHeader[0]] = generalQuestions
        listDataChild[listDataHeader[1]] = carQuestions
        listDataChild[listDataHeader[2]] = fuelQuestions
        listDataChild[listDataHeader[3]] = statsQuestions
        listDataChild[listDataHeader[4]] = supportQuestions
    }

    private fun onQuestionClicked(groupPosition: Int, childPosition: Int) {
        val question = listDataChild[listDataHeader[groupPosition]]?.get(childPosition)
        showAnswerDialog(question)
    }

    private fun showAnswerDialog(question: String?) {
        val answer = getAnswerForQuestion(question)

        AlertDialog.Builder(this)
            .setTitle("Ответ")
            .setMessage(answer)
            .setPositiveButton("Понятно") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun getAnswerForQuestion(question: String?): String {
        return when (question) {
            "Как добавить новый автомобиль?" ->
                "Нажмите кнопку «Добавить новую машину» на главном экране или откройте боковое меню. Заполните все необходимые поля: марку, модель, пробег и при желании загрузите фотографию."

            "Как переключаться между автомобилями?" ->
                "Откройте боковое меню (нажмите на иконку ☰). В разделе «Мои автомобили» вы увидите список всех добавленных машин. Нажмите на нужный автомобиль для переключения."

            "Как отредактировать информацию об автомобиле?" ->
                "Нажмите на карточку автомобиля на главном экране. Внесите изменения и нажмите «Сохранить»."

            "Как удалить автомобиль?" ->
                "Откройте редактирование автомобиля, прокрутите вниз и нажмите кнопку «Удалить автомобиль». Подтвердите удаление."

            "Как ввести показания пробега?" ->
                "При добавлении записи о заправке или обслуживании текущий пробег указывается автоматически. Вы также можете обновить пробег в разделе редактирования автомобиля."

            "Как загрузить фотографию автомобиля?" ->
                "При добавлении или редактировании автомобиля нажмите на иконку камеры. Выберите фото из галереи или сделайте новое фото."

            "Какие данные об автомобиле можно сохранить?" ->
                "Вы можете сохранить: марку, модель, год выпуска, текущий пробег, фотографию, VIN-номер (опционально)."

            "Как добавить запись о заправке?" ->
                "Нажмите на иконку заправки (⛽) в нижней панели. Укажите дату, объем топлива, стоимость и текущий пробег."

            "Как рассчитать расход топлива?" ->
                "Расход топлива рассчитывается автоматически на основе данных о заправках. Посмотреть статистику можно в разделе «Статистика»."

            "Как добавить напоминание о ТО?" ->
                "Откройте раздел «Обслуживание», нажмите «Добавить ТО». Укажите тип обслуживания, дату или пробег для напоминания."

            "Как часто нужно проходить обслуживание?" ->
                "Рекомендуемое межсервисное обслуживание: каждые 10 000 - 15 000 км или раз в год."

            "Какие отчеты доступны в приложении?" ->
                "Доступны отчеты: расход топлива, затраты на обслуживание, история заправок, статистика пробега."

            "Как экспортировать данные?" ->
                "В разделе «Статистика» нажмите кнопку «Экспорт». Данные можно сохранить в формате PDF или CSV."

            "За какой период доступна статистика?" ->
                "Статистика доступна за весь период использования приложения. Вы можете фильтровать данные по месяцам и годам."

            "Как связаться с поддержкой?" ->
                "Нажмите на плавающую кнопку с иконкой сообщения (💬) в правом нижнем углу или напишите на email: support@mycar.app"

            "Как сообщить об ошибке?" ->
                "Откройте меню → Помощь → Сообщить об ошибке. Опишите проблему и приложите скриншот."

            "Где найти руководство пользователя?" ->
                "Полное руководство доступно в меню → Помощь. Также вы можете посмотреть видео-инструкции."

            else -> "Если у вас остались вопросы, свяжитесь с нашей службой поддержки через кнопку в правом нижнем углу."
        }
    }

    private fun showContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_contact_support, null)

        AlertDialog.Builder(this)
            .setTitle("Связаться с поддержкой")
            .setView(dialogView)
            .setPositiveButton("Отправить") { dialog, _ ->
                val subject = dialogView.findViewById<EditText>(R.id.editSubject).text.toString()
                val message = dialogView.findViewById<EditText>(R.id.editMessage).text.toString()
                sendSupportEmail(subject, message)
            }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun sendSupportEmail(subject: String, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("andrey.senko2017@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "MyCar App: $subject")
            putExtra(Intent.EXTRA_TEXT, message)
        }

        try {
            startActivity(Intent.createChooser(intent, "Отправить письмо"))
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть почтовый клиент", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}