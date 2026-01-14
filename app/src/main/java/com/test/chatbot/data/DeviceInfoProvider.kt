package com.test.chatbot.data

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * Провайдер информации об устройстве
 */
class DeviceInfoProvider(private val context: Context) {

    /**
     * Модель устройства (например: "Samsung Galaxy S23", "Google Pixel 7")
     */
    val deviceModel: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}".capitalize()

    /**
     * Только модель без производителя
     */
    val model: String
        get() = Build.MODEL

    /**
     * Производитель (Samsung, Google, Xiaomi и т.д.)
     */
    val manufacturer: String
        get() = Build.MANUFACTURER.capitalize()

    /**
     * Версия Android (например: "14", "13")
     */
    val androidVersion: String
        get() = Build.VERSION.RELEASE

    /**
     * SDK версия (например: 34, 33)
     */
    val sdkVersion: Int
        get() = Build.VERSION.SDK_INT

    /**
     * Бренд устройства
     */
    val brand: String
        get() = Build.BRAND.capitalize()

    /**
     * Полная информация об устройстве
     */
    val fullDeviceInfo: String
        get() = buildString {
            appendLine("Производитель: $manufacturer")
            appendLine("Модель: $model")
            appendLine("Устройство: $deviceModel")
            appendLine("Android: $androidVersion (SDK $sdkVersion)")
            appendLine("Бренд: $brand")
        }

    /**
     * Краткая информация для отправки в поддержку
     */
    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val deviceModel: String,
        val androidVersion: String,
        val sdkVersion: Int,
        val isEmulator: Boolean
    )

    /**
     * Получить полную информацию об устройстве
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = manufacturer,
            model = model,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            sdkVersion = sdkVersion,
            isEmulator = isEmulator()
        )
    }

    /**
     * Определить, это эмулятор или реальное устройство
     */
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }

    private fun String.capitalize(): String {
        return this.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) 
            else it.toString() 
        }
    }
}
