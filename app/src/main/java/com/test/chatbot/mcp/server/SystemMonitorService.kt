package com.test.chatbot.mcp.server

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.text.DecimalFormat

class SystemMonitorService(private val context: Context) {
    
    companion object {
        private const val TAG = "SystemMonitorService"
    }
    
    /**
     * Получить полную информацию о системе
     */
    suspend fun getSystemInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Сбор информации о системе...")
            
            val batteryInfo = getBatteryInfoInternal()
            val memoryInfo = getMemoryInfoInternal()
            val cpuInfo = getCpuInfoInternal()
            val networkInfo = getNetworkInfoInternal()
            val storageInfo = getStorageInfoInternal()
            
            val info = buildString {
                append("📊 СИСТЕМНЫЙ МОНИТОРИНГ\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                
                append("🔋 БАТАРЕЯ\n")
                append(batteryInfo)
                append("\n")
                
                append("🧠 ПАМЯТЬ\n")
                append(memoryInfo)
                append("\n")
                
                append("⚙️ ПРОЦЕССОР\n")
                append(cpuInfo)
                append("\n")
                
                append("🌐 СЕТЬ\n")
                append(networkInfo)
                append("\n")
                
                append("💾 ХРАНИЛИЩЕ\n")
                append(storageInfo)
            }
            
            Log.i(TAG, "✅ Информация собрана")
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сбора информации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить информацию о батарее
     */
    suspend fun getBatteryInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val info = getBatteryInfoInternal()
            Result.success("🔋 БАТАРЕЯ\n$info")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getBatteryInfoInternal(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat())
        } else {
            -1f
        }
        
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                        status == BatteryManager.BATTERY_STATUS_FULL
        
        val chargePlug = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
        val wirelessCharge = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS
        } else false
        
        val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Разряжена"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Перенапряжение"
            BatteryManager.BATTERY_HEALTH_COLD -> "Холодная"
            else -> "Неизвестно"
        }
        
        // Дополнительная информация для Android 5.0+
        val capacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            batteryPct.toInt()
        }
        
        val currentNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000 // в mA
        } else {
            0
        }
        
        return buildString {
            append("Уровень: ${DecimalFormat("#.#").format(batteryPct)}% ")
            append(getBatteryEmoji(batteryPct.toInt(), isCharging))
            append("\n")
            
            append("Статус: ${if (isCharging) "⚡ Заряжается" else "🔌 От батареи"}\n")
            
            if (isCharging) {
                when {
                    acCharge -> append("Источник: 🔌 AC адаптер\n")
                    usbCharge -> append("Источник: 🔌 USB\n")
                    wirelessCharge -> append("Источник: 📡 Беспроводная\n")
                }
            }
            
            append("Здоровье: $healthStr\n")
            
            if (temperature > 0) {
                val tempC = temperature / 10.0
                append("Температура: ${DecimalFormat("#.#").format(tempC)}°C\n")
            }
            
            if (voltage > 0) {
                val voltageV = voltage / 1000.0
                append("Напряжение: ${DecimalFormat("#.##").format(voltageV)}V\n")
            }
            
            if (currentNow != 0) {
                append("Ток: ${currentNow}mA\n")
            }
        }
    }
    
    private fun getBatteryEmoji(level: Int, isCharging: Boolean): String {
        return when {
            isCharging -> "⚡"
            level > 80 -> "🟢"
            level > 50 -> "🟡"
            level > 20 -> "🟠"
            else -> "🔴"
        }
    }
    
    /**
     * Получить информацию о памяти
     */
    suspend fun getMemoryInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val info = getMemoryInfoInternal()
            Result.success("🧠 ПАМЯТЬ\n$info")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getMemoryInfoInternal(): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalMemory = memoryInfo.totalMem / (1024 * 1024) // MB
        val availMemory = memoryInfo.availMem / (1024 * 1024) // MB
        val usedMemory = totalMemory - availMemory
        val memoryPercent = (usedMemory * 100.0 / totalMemory)
        
        val runtime = Runtime.getRuntime()
        val appMaxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val appUsedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) // MB
        val appFreeMemory = appMaxMemory - appUsedMemory
        
        return buildString {
            append("Системная:\n")
            append("  Всего: ${totalMemory} MB\n")
            append("  Использовано: ${usedMemory} MB (${DecimalFormat("#.#").format(memoryPercent)}%)\n")
            append("  Доступно: ${availMemory} MB\n")
            append("  Низкая память: ${if (memoryInfo.lowMemory) "⚠️ Да" else "✅ Нет"}\n")
            append("\n")
            append("Приложение:\n")
            append("  Лимит: ${appMaxMemory} MB\n")
            append("  Использовано: ${appUsedMemory} MB\n")
            append("  Свободно: ${appFreeMemory} MB\n")
        }
    }
    
    /**
     * Получить информацию о процессоре
     */
    suspend fun getCpuInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val info = getCpuInfoInternal()
            Result.success("⚙️ ПРОЦЕССОР\n$info")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getCpuInfoInternal(): String {
        val runtime = Runtime.getRuntime()
        val cores = runtime.availableProcessors()
        
        // Читаем информацию о CPU из /proc/cpuinfo
        var cpuModel = "Неизвестно"
        var cpuFreq = "Неизвестно"
        
        try {
            val cpuInfoFile = File("/proc/cpuinfo")
            if (cpuInfoFile.exists()) {
                cpuInfoFile.readLines().forEach { line ->
                    when {
                        line.startsWith("Hardware") -> {
                            cpuModel = line.substringAfter(":").trim()
                        }
                        line.startsWith("processor") && cpuFreq == "Неизвестно" -> {
                            // Пытаемся прочитать частоту
                            val procNum = line.substringAfter(":").trim()
                            val freqFile = File("/sys/devices/system/cpu/cpu$procNum/cpufreq/scaling_cur_freq")
                            if (freqFile.exists()) {
                                val freqKHz = freqFile.readText().trim().toIntOrNull() ?: 0
                                if (freqKHz > 0) {
                                    cpuFreq = "${freqKHz / 1000} MHz"
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось прочитать /proc/cpuinfo: ${e.message}")
        }
        
        // Загрузка CPU
        val cpuUsage = getCpuUsage()
        
        return buildString {
            append("Ядер: $cores\n")
            append("Модель: $cpuModel\n")
            append("Частота: $cpuFreq\n")
            append("Загрузка: ${DecimalFormat("#.#").format(cpuUsage)}%\n")
            append("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
        }
    }
    
    private fun getCpuUsage(): Double {
        return try {
            val statFile = RandomAccessFile("/proc/stat", "r")
            val cpuLine = statFile.readLine()
            statFile.close()
            
            val tokens = cpuLine.split("\\s+".toRegex())
            val idle = tokens[4].toLong()
            val total = tokens.slice(1..7).sumOf { it.toLong() }
            
            val usage = 100.0 * (1.0 - idle.toDouble() / total.toDouble())
            usage
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * Получить информацию о сети
     */
    suspend fun getNetworkInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val info = getNetworkInfoInternal()
            Result.success("🌐 СЕТЬ\n$info")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getNetworkInfoInternal(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            if (capabilities == null) {
                "Статус: ❌ Нет подключения\n"
            } else {
                buildString {
                    append("Статус: ✅ Подключено\n")
                    
                    when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                            append("Тип: 📶 WiFi\n")
                        }
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                            append("Тип: 📱 Мобильная сеть\n")
                        }
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                            append("Тип: 🔌 Ethernet\n")
                        }
                        else -> {
                            append("Тип: ❓ Другое\n")
                        }
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val downSpeed = capabilities.linkDownstreamBandwidthKbps / 1024 // Mbps
                        val upSpeed = capabilities.linkUpstreamBandwidthKbps / 1024 // Mbps
                        
                        if (downSpeed > 0) {
                            append("Скорость ⬇️: $downSpeed Mbps\n")
                        }
                        if (upSpeed > 0) {
                            append("Скорость ⬆️: $upSpeed Mbps\n")
                        }
                    }
                    
                    append("Интернет: ${if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "✅" else "❌"}\n")
                    append("Проверенный: ${if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "✅" else "❌"}\n")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            
            if (networkInfo?.isConnected == true) {
                buildString {
                    append("Статус: ✅ Подключено\n")
                    append("Тип: ${networkInfo.typeName}\n")
                    append("Подтип: ${networkInfo.subtypeName}\n")
                }
            } else {
                "Статус: ❌ Нет подключения\n"
            }
        }
    }
    
    /**
     * Получить информацию о хранилище
     */
    suspend fun getStorageInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val info = getStorageInfoInternal()
            Result.success("💾 ХРАНИЛИЩЕ\n$info")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getStorageInfoInternal(): String {
        val internalDir = context.filesDir
        val externalDir = context.getExternalFilesDir(null)
        
        return buildString {
            // Внутренняя память
            append("Внутренняя:\n")
            val internalTotal = internalDir.totalSpace / (1024 * 1024 * 1024) // GB
            val internalFree = internalDir.freeSpace / (1024 * 1024 * 1024) // GB
            val internalUsed = internalTotal - internalFree
            append("  Всего: $internalTotal GB\n")
            append("  Использовано: $internalUsed GB\n")
            append("  Свободно: $internalFree GB\n")
            
            // Внешняя память
            if (externalDir != null && externalDir.exists()) {
                append("\n")
                append("Внешняя:\n")
                val externalTotal = externalDir.totalSpace / (1024 * 1024 * 1024) // GB
                val externalFree = externalDir.freeSpace / (1024 * 1024 * 1024) // GB
                val externalUsed = externalTotal - externalFree
                append("  Всего: $externalTotal GB\n")
                append("  Использовано: $externalUsed GB\n")
                append("  Свободно: $externalFree GB\n")
            }
        }
    }
    
    /**
     * Мониторинг в реальном времени (snapshot)
     */
    suspend fun getRealtimeSnapshot(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val memoryUsedPct = ((memoryInfo.totalMem - memoryInfo.availMem) * 100.0 / memoryInfo.totalMem)
            
            val cpuUsage = getCpuUsage()
            
            val info = buildString {
                append("⏰ $timestamp\n")
                append("━━━━━━━━━━━━━━\n")
                append("🔋 Батарея: $batteryPct%\n")
                append("🧠 Память: ${DecimalFormat("#.#").format(memoryUsedPct)}%\n")
                append("⚙️ CPU: ${DecimalFormat("#.#").format(cpuUsage)}%\n")
            }
            
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

