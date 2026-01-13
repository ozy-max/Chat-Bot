package com.test.chatbot.utils

/**
 * Утилиты для тестирования AI PR Review
 */
object TestUtils {
    
    /**
     * Проверить валидность email
     * TODO: Добавить более строгую валидацию
     */
    fun isValidEmail(email: String): Boolean {
        return email.contains("@")  // Простая проверка, нужно улучшить
    }
    
    /**
     * Форматировать имя пользователя
     */
    fun formatUserName(firstName: String, lastName: String): String {
        // Потенциальная проблема: не проверяем на null
        return "$firstName $lastName"
    }
    
    /**
     * Вычислить факториал числа
     */
    fun factorial(n: Int): Long {
        // Отсутствует проверка на отрицательные числа
        if (n == 0) return 1
        return n * factorial(n - 1)  // Рекурсия может привести к StackOverflow
    }
    
    /**
     * Получить случайное число
     */
    fun getRandomNumber(): Int {
        return (Math.random() * 100).toInt()
    }
}
