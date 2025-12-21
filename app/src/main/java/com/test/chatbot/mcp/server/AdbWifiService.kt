package com.test.chatbot.mcp.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

class AdbWifiService(private val context: Context) {
    
    companion object {
        private const val TAG = "AdbWifiService"
        private const val DEFAULT_ADB_PORT = 5555
    }
    
    /**
     * Получить информацию о подключении ADB over WiFi
     */
    suspend fun getAdbWifiInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ipAddress = getDeviceIpAddress()
            val wifiEnabled = isWifiEnabled()
            
            val info = buildString {
                append("📡 ADB OVER WIFI\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                
                if (!wifiEnabled) {
                    append("⚠️ WiFi не включен\n\n")
                    append("Включите WiFi для использования ADB over WiFi\n")
                } else if (ipAddress == null) {
                    append("⚠️ IP адрес не определён\n\n")
                    append("Убедитесь что вы подключены к WiFi сети\n")
                } else {
                    append("✅ WiFi подключен\n")
                    append("IP адрес: $ipAddress\n")
                    append("ADB порт: $DEFAULT_ADB_PORT\n\n")
                    
                    append("━━━━━━━━━━━━━━━━━━━━\n")
                    append("📥 КАК ПОДКЛЮЧИТЬСЯ:\n\n")
                    
                    append("1. На этом устройстве (требуется root):\n")
                    append("   setprop service.adb.tcp.port $DEFAULT_ADB_PORT\n")
                    append("   stop adbd\n")
                    append("   start adbd\n\n")
                    
                    append("2. На компьютере:\n")
                    append("   adb connect $ipAddress:$DEFAULT_ADB_PORT\n\n")
                    
                    append("━━━━━━━━━━━━━━━━━━━━\n")
                    append("⚠️ ВАЖНО:\n")
                    append("• Требуются root права на устройстве\n")
                    append("• Устройства должны быть в одной сети\n")
                    append("• Это может быть небезопасно\n\n")
                    
                    append("━━━━━━━━━━━━━━━━━━━━\n")
                    append("💡 АЛЬТЕРНАТИВА:\n")
                    append("Используйте Termux + sshd для безопасного удалённого доступа\n")
                }
            }
            
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения информации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Инструкции по настройке SSH через Termux (безопасная альтернатива)
     */
    suspend fun getSshInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ipAddress = getDeviceIpAddress()
            
            val info = buildString {
                append("🔐 SSH ДОСТУП ЧЕРЕЗ TERMUX\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                
                if (ipAddress != null) {
                    append("IP адрес: $ipAddress\n")
                    append("SSH порт: 8022 (по умолчанию)\n\n")
                }
                
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("📥 УСТАНОВКА SSH В TERMUX:\n\n")
                
                append("1. Установите Termux с F-Droid\n\n")
                
                append("2. В Termux выполните:\n")
                append("   pkg update && pkg upgrade\n")
                append("   pkg install openssh\n\n")
                
                append("3. Установите пароль:\n")
                append("   passwd\n\n")
                
                append("4. Запустите SSH сервер:\n")
                append("   sshd\n\n")
                
                append("5. На компьютере подключитесь:\n")
                if (ipAddress != null) {
                    append("   ssh -p 8022 $(whoami)@$ipAddress\n\n")
                } else {
                    append("   ssh -p 8022 username@device_ip\n\n")
                }
                
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("💡 ПОЛЕЗНЫЕ КОМАНДЫ:\n\n")
                
                append("Узнать имя пользователя:\n")
                append("   whoami\n\n")
                
                append("Остановить SSH:\n")
                append("   pkill sshd\n\n")
                
                append("Проверить статус:\n")
                append("   pgrep sshd\n\n")
                
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("🔒 БЕЗОПАСНОСТЬ:\n")
                append("• Используйте сложный пароль\n")
                append("• Подключайтесь только в доверенных сетях\n")
                append("• Настройте SSH ключи для безопасности\n")
            }
            
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения информации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить IP адрес устройства в WiFi сети
     */
    private fun getDeviceIpAddress(): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            
            // Метод 1: через WifiManager (может быть deprecated)
            val wifiInfo = wifiManager?.connectionInfo
            if (wifiInfo != null) {
                val ipAddress = wifiInfo.ipAddress
                if (ipAddress != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                }
            }
            
            // Метод 2: через NetworkInterface
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // Ищем WiFi интерфейс
                if (networkInterface.name.startsWith("wlan")) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        
                        // Берём только IPv4 и не loopback
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения IP адреса: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Проверить включен ли WiFi
     */
    private fun isWifiEnabled(): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Получить подробную информацию о сетевых интерфейсах
     */
    suspend fun getNetworkInterfaces(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val info = buildString {
                append("🌐 СЕТЕВЫЕ ИНТЕРФЕЙСЫ\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                
                val interfaces = NetworkInterface.getNetworkInterfaces()
                var count = 0
                
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    count++
                    
                    append("${count}. ${networkInterface.name}\n")
                    append("   Отображаемое имя: ${networkInterface.displayName}\n")
                    append("   Активен: ${if (networkInterface.isUp) "✅" else "❌"}\n")
                    
                    val addresses = networkInterface.inetAddresses
                    val addressList = mutableListOf<String>()
                    
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress) {
                            val type = if (address is Inet4Address) "IPv4" else "IPv6"
                            addressList.add("$type: ${address.hostAddress}")
                        }
                    }
                    
                    if (addressList.isNotEmpty()) {
                        append("   Адреса:\n")
                        addressList.forEach { addr ->
                            append("     • $addr\n")
                        }
                    }
                    
                    append("\n")
                }
                
                if (count == 0) {
                    append("Нет доступных интерфейсов\n")
                }
            }
            
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения интерфейсов: ${e.message}", e)
            Result.failure(e)
        }
    }
}

