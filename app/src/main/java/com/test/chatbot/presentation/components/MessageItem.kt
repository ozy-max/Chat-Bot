package com.test.chatbot.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.chatbot.models.AiProvider
import com.test.chatbot.models.Message
import com.test.chatbot.ui.theme.AccentBlue
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.AccentPurple
import com.test.chatbot.ui.theme.UserMessageBg
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    // Определяем название и цвет провайдера
    val providerName = when {
        message.isUser -> "Вы"
        message.provider == AiProvider.YANDEX_GPT -> "YandexGPT"
        message.provider == AiProvider.CLAUDE -> "Claude"
        message.provider == AiProvider.OLLAMA -> "Ollama"
        else -> "AI"
    }
    
    val providerColor = when {
        message.isUser -> AccentBlue
        message.provider == AiProvider.YANDEX_GPT -> Color(0xFFFF5722) // Оранжевый для Яндекса
        message.provider == AiProvider.CLAUDE -> AccentPurple // Фиолетовый для Claude
        message.provider == AiProvider.OLLAMA -> Color(0xFF00D4AA) // Бирюзовый для Ollama
        else -> AccentYellow
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(4.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    UserMessageBg
                } else {
                    Color(0xFF1A1A1A)
                }
            ),
            border = if (message.isUser) {
                BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f))
            } else {
                BorderStroke(1.dp, providerColor.copy(alpha = 0.3f))
            },
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Метка отправителя
                Text(
                    text = providerName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = providerColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                // Текст сообщения с кликабельными ссылками
                MessageTextWithLinks(
                    text = message.text,
                    textColor = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                
                // Информация об использованных инструментах
                if (message.toolCalls != null && message.toolCalls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2D2D00)
                        ),
                        border = BorderStroke(1.dp, AccentYellow.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "🔧 Инструменты:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AccentYellow
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            message.toolCalls.forEach { toolCall ->
                                Text(
                                    text = "• ${toolCall.toolName}",
                                    fontSize = 11.sp,
                                    color = AccentYellow.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "  ${toolCall.input}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF888888)
                                )
                                toolCall.result?.let { result ->
                                    Text(
                                        text = "  → $result",
                                        fontSize = 10.sp,
                                        color = Color(0xFF00FF88)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Нижняя строка: время и токены
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Время
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        fontSize = 11.sp,
                        color = Color(0xFF666666)
                    )
                    
                    // Токены только для сообщений AI
                    if (!message.isUser) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.outputTokens != null) {
                                // Исходящие токены (ответ)
                                TokenBadge(
                                    label = "↓",
                                    value = message.outputTokens,
                                    color = AccentYellow
                                )
                            }
                            
                            if (message.inputTokens != null) {
                                // Входящие токены (контекст)
                                TokenBadge(
                                    label = "↑",
                                    value = message.inputTokens,
                                    color = AccentBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenBadge(
    label: String,
    value: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = formatTokens(value),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun formatTokens(count: Int): String {
    return when {
        count >= 1000 -> "${String.format("%.1f", count / 1000.0)}K"
        else -> count.toString()
    }
}

private fun tryOpenPdfFile(context: android.content.Context, pdfFile: java.io.File): Boolean {
    android.util.Log.i("MessageItem", "Попытка открыть PDF: ${pdfFile.absolutePath}")
    
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        
        android.util.Log.i("MessageItem", "URI: $uri")
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Проверяем есть ли приложение для открытия PDF
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            android.util.Log.i("MessageItem", "✅ PDF файл открыт")
            
            android.widget.Toast.makeText(
                context,
                "📄 Открываю PDF файл...",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            return true
        } else {
            android.util.Log.w("MessageItem", "⚠️ Нет приложения для открытия PDF")
            
            // Предлагаем установить PDF reader
            val chooserIntent = Intent.createChooser(intent, "Откройте PDF с помощью:")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
            return true
        }
    } catch (e: Exception) {
        android.util.Log.e("MessageItem", "❌ Ошибка открытия PDF: ${e.message}", e)
        return false
    }
}

private fun tryOpenFolder(context: android.content.Context, folder: java.io.File): Boolean {
    android.util.Log.i("MessageItem", "Пытаемся открыть папку: ${folder.absolutePath}")
    android.util.Log.i("MessageItem", "Папка существует: ${folder.exists()}")
    android.util.Log.i("MessageItem", "Это директория: ${folder.isDirectory}")
    
    if (!folder.exists() || !folder.isDirectory) {
        android.widget.Toast.makeText(
            context,
            "Папка не найдена: ${folder.absolutePath}",
            android.widget.Toast.LENGTH_LONG
        ).show()
        return false
    }
    
    // Список файлов в папке
    val files = folder.listFiles()
    android.util.Log.i("MessageItem", "Файлов в папке: ${files?.size ?: 0}")
    files?.forEach {
        android.util.Log.i("MessageItem", "  - ${it.name} (${it.length()} байт)")
    }
    
    // Способ 1: Открываем файловый менеджер с помощью Intent.ACTION_VIEW
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    folder
                ),
                "resource/folder"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            android.util.Log.i("MessageItem", "✅ Папка открыта через ACTION_VIEW")
            return true
        }
    } catch (e: Exception) {
        android.util.Log.e("MessageItem", "❌ Способ 1 не сработал: ${e.message}")
    }
    
    // Способ 2: Открываем популярные файловые менеджеры напрямую
    val fileManagerPackages = mapOf(
        "com.google.android.documentsui" to "com.android.documentsui.files.FilesActivity",
        "com.android.documentsui" to "com.android.documentsui.files.FilesActivity",
        "com.mi.android.globalFileexplorer" to "com.mi.android.globalFileexplorer.FileExplorerTabActivity"
    )
    
    for ((packageName, activityName) in fileManagerPackages) {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("android.provider.extra.INITIAL_URI", folder.absolutePath)
                putExtra("org.openintents.extra.ABSOLUTE_PATH", folder.absolutePath)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                android.util.Log.i("MessageItem", "✅ Папка открыта через $packageName")
                return true
            }
        } catch (e: Exception) {
            android.util.Log.e("MessageItem", "❌ $packageName не сработал: ${e.message}")
        }
    }
    
    // Способ 3: Открыть диалог выбора файлового менеджера
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "*/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooser = Intent.createChooser(intent, "Откройте файловый менеджер и перейдите в:\n${folder.absolutePath}")
        context.startActivity(chooser)
        
        // Копируем путь в буфер обмена
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Путь к папке", folder.absolutePath)
        clipboard.setPrimaryClip(clip)
        
        android.widget.Toast.makeText(
            context,
            "Путь скопирован в буфер обмена",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        android.util.Log.i("MessageItem", "✅ Открыт файловый менеджер с подсказкой")
        return true
    } catch (e: Exception) {
        android.util.Log.e("MessageItem", "❌ Способ 3 не сработал: ${e.message}")
    }
    
    return false
}

@Composable
fun MessageTextWithLinks(
    text: String,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit
) {
    val context = LocalContext.current
    
    val annotatedString = buildAnnotatedString {
        var processedText = text
        var currentIndex = 0
        
        // Сначала обрабатываем файловые ссылки [FILE:путь]текст[/FILE]
        val filePattern = Regex("\\[FILE:(.*?)\\](.*?)\\[/FILE\\]")
        val urlPattern = Regex("https?://[^\\s]+")
        
        // Находим все совпадения для файлов и URL
        val fileMatches = filePattern.findAll(text).toList()
        val urlMatches = urlPattern.findAll(text).toList()
        
        // Объединяем и сортируем по позиции
        val allMatches = mutableListOf<Pair<Int, () -> Unit>>()
        
        fileMatches.forEach { match ->
            allMatches.add(match.range.first to {
                val filePath = match.groupValues[1]
                val displayText = match.groupValues[2]
                val start = match.range.first
                
                append(text.substring(currentIndex, start))
                
                pushStringAnnotation(
                    tag = "FILE",
                    annotation = filePath
                )
                withStyle(
                    style = SpanStyle(
                        color = AccentBlue,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(displayText)
                }
                pop()
                
                currentIndex = match.range.last + 1
            })
        }
        
        urlMatches.forEach { match ->
            // Пропускаем URL внутри файловых маркеров
            val isInsideFileMarker = fileMatches.any { fileMatch ->
                match.range.first >= fileMatch.range.first && match.range.last <= fileMatch.range.last
            }
            
            if (!isInsideFileMarker) {
                allMatches.add(match.range.first to {
                    val fullUrl = match.value
                    val displayText = try {
                        val uri = Uri.parse(fullUrl)
                        uri.host ?: fullUrl
                    } catch (e: Exception) {
                        fullUrl
                    }
                    val start = match.range.first
                    
                    append(text.substring(currentIndex, start))
                    
                    pushStringAnnotation(
                        tag = "URL",
                        annotation = fullUrl
                    )
                    withStyle(
                        style = SpanStyle(
                            color = AccentBlue,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(displayText)
                    }
                    pop()
                    
                    currentIndex = match.range.last + 1
                })
            }
        }
        
        // Сортируем по позиции и применяем
        allMatches.sortedBy { it.first }.forEach { it.second() }
        
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
    
    ClickableText(
        text = annotatedString,
        style = androidx.compose.ui.text.TextStyle(
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight
        ),
        onClick = { offset ->
            // Проверяем клик по файлу
            annotatedString.getStringAnnotations(
                tag = "FILE",
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                try {
                    val file = java.io.File(annotation.item)
                    val parentDir = file.parentFile
                    
                    android.util.Log.i("MessageItem", "Клик на файл: ${annotation.item}")
                    android.util.Log.i("MessageItem", "Файл существует: ${file.exists()}")
                    android.util.Log.i("MessageItem", "Размер файла: ${file.length()} байт")
                    android.util.Log.i("MessageItem", "Родительская папка: ${parentDir?.absolutePath}")
                    
                    // Если это PDF файл, пытаемся открыть его напрямую
                    if (file.exists() && file.name.endsWith(".pdf", ignoreCase = true)) {
                        android.util.Log.i("MessageItem", "Открываем PDF файл: ${file.name}")
                        
                        val success = tryOpenPdfFile(context, file)
                        
                        if (!success) {
                            android.widget.Toast.makeText(
                                context,
                                "❌ Не удалось открыть PDF.\nУстановите PDF-просмотрщик.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else if (parentDir != null && parentDir.exists()) {
                        // Список файлов в папке
                        val filesInDir = parentDir.listFiles()
                        android.util.Log.i("MessageItem", "Файлов в папке: ${filesInDir?.size ?: 0}")
                        filesInDir?.forEach {
                            android.util.Log.i("MessageItem", "  - ${it.name} (${it.length()} байт)")
                        }
                        
                        // Создаём сообщение с содержимым папки
                        val folderContent = buildString {
                            append("📁 Папка: ${parentDir.name}\n\n")
                            append("📍 Полный путь:\n${parentDir.absolutePath}\n\n")
                            
                            if (filesInDir != null && filesInDir.isNotEmpty()) {
                                append("📄 Файлы (${filesInDir.size}):\n")
                                filesInDir.sortedByDescending { it.lastModified() }.forEach {
                                    val size = if (it.length() > 1024) {
                                        "${it.length() / 1024} KB"
                                    } else {
                                        "${it.length()} B"
                                    }
                                    val date = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(it.lastModified()))
                                    append("  • ${it.name}\n")
                                    append("    $size • $date\n")
                                }
                            } else {
                                append("⚠️ Папка пуста\n")
                            }
                            
                            append("\n💡 Нажмите на название PDF файла чтобы открыть")
                        }
                        
                        android.widget.Toast.makeText(
                            context,
                            folderContent,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        
                        // Копируем путь в буфер обмена
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Путь к папке", parentDir.absolutePath)
                        clipboard.setPrimaryClip(clip)
                        
                        // Пытаемся открыть папку
                        tryOpenFolder(context, parentDir)
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "❌ Файл не найден:\n${annotation.item}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MessageItem", "Ошибка при клике на файл: ${e.message}", e)
                    android.widget.Toast.makeText(
                        context,
                        "❌ Ошибка: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@ClickableText
            }
            
            // Проверяем клик по URL
            annotatedString.getStringAnnotations(
                tag = "URL",
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                context.startActivity(intent)
            }
        }
    )
}
