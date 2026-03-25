package com.example.mycar

import android.util.Patterns

object EmailValidator {

    private val ALLOWED_DOMAINS = setOf(
        // Gmail
        "gmail.com",
        // Яндекс
        "yandex.ru", "yandex.com", "ya.ru",
        // Mail.ru группа
        "mail.ru", "inbox.ru", "list.ru", "bk.ru",
        // Rambler
        "rambler.ru", "ro.ru", "lenta.ru", "autorambler.ru", "myrambler.ru",
        // Microsoft
        "outlook.com", "hotmail.com", "live.com", "msn.com",
        // Apple
        "icloud.com", "me.com", "mac.com",
        // Yahoo
        "yahoo.com", "yahoo.ru",
        // Прочие популярные
        "protonmail.com", "proton.me",
        "tutanota.com",
        "gmx.com", "gmx.net",
        "zoho.com",
        "ukr.net",
        "i.ua",
        "meta.ua",
    )

    /**
     * Возвращает null если email корректный, иначе текст ошибки.
     */
    fun validate(email: String): String? {
        val trimmed = email.trim()

        if (trimmed.isEmpty()) return "Введите email"

        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches())
            return "Некорректный формат email"

        val domain = trimmed.substringAfterLast("@").lowercase()

        if (domain !in ALLOWED_DOMAINS)
            return "Неподдерживаемый домен. Используйте gmail.com, mail.ru, yandex.ru и др."

        return null
    }
}