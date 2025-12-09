package com.test.chatbot.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.test.chatbot.models.ModelComparisonResult
import com.test.chatbot.models.ModelResponse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ComparisonDialog(
    isComparing: Boolean,
    comparisonResult: ModelComparisonResult?,
    onDismiss: () -> Unit,
    onCompare: (String) -> Unit,
    onClearResult: () -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Заголовок
                Text(
                    text = "⚡ Сравнение моделей",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Поле ввода запроса
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    label = { Text("Введите запрос для сравнения") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !isComparing
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Кнопка сравнения
                Button(
                    onClick = { onCompare(queryText) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = queryText.isNotBlank() && !isComparing
                ) {
                    if (isComparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сравниваем...")
                    } else {
                        Text("🚀 Сравнить модели")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Результаты
                if (comparisonResult != null) {
                    // Кнопки действий с результатами
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Кнопка копирования
                        OutlinedButton(
                            onClick = {
                                val formattedText = formatComparisonResultForCopy(comparisonResult)
                                copyToClipboard(context, formattedText)
                                Toast.makeText(context, "Результаты скопированы!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Копировать")
                        }
                        
                        // Кнопка сброса
                        OutlinedButton(
                            onClick = onClearResult,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сбросить")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Запрос
                        Text(
                            text = "📝 Запрос:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = comparisonResult.query,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // Результаты Claude
                        comparisonResult.claudeResult?.let { claude ->
                            ModelResultCard(
                                response = claude,
                                cardColor = Color(0xFF6B5B95)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Результаты YandexGPT
                        comparisonResult.yandexResult?.let { yandex ->
                            ModelResultCard(
                                response = yandex,
                                cardColor = Color(0xFFFF6B35)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Сводная таблица
                        ComparisonSummaryTable(comparisonResult)
                    }
                } else if (!isComparing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Введите запрос и нажмите \"Сравнить модели\"\nдля параллельного тестирования Claude и YandexGPT",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Кнопка закрытия
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Закрыть")
                }
            }
        }
    }
}

/**
 * Копирование текста в буфер обмена
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Comparison Result", text)
    clipboard.setPrimaryClip(clip)
}

/**
 * Форматирование результатов сравнения в текст
 */
private fun formatComparisonResultForCopy(result: ModelComparisonResult): String {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    val timestamp = dateFormat.format(Date(result.timestamp))
    
    val sb = StringBuilder()
    sb.appendLine("═══════════════════════════════════════════════════════")
    sb.appendLine("           СРАВНЕНИЕ МОДЕЛЕЙ AI")
    sb.appendLine("═══════════════════════════════════════════════════════")
    sb.appendLine("Дата: $timestamp")
    sb.appendLine()
    sb.appendLine("📝 ЗАПРОС:")
    sb.appendLine(result.query)
    sb.appendLine()
    
    // Claude результаты
    sb.appendLine("───────────────────────────────────────────────────────")
    sb.appendLine("🟣 CLAUDE SONNET 4")
    sb.appendLine("───────────────────────────────────────────────────────")
    result.claudeResult?.let { claude ->
        if (claude.error != null) {
            sb.appendLine("❌ Ошибка: ${claude.error}")
        } else {
            sb.appendLine("⏱️ Время ответа: ${claude.responseTimeMs} мс")
            sb.appendLine("📥 Входные токены: ${claude.inputTokens}")
            sb.appendLine("📤 Выходные токены: ${claude.outputTokens}")
            sb.appendLine("📊 Всего токенов: ${claude.totalTokens}")
            sb.appendLine("💰 Стоимость: $${String.format("%.6f", claude.estimatedCostUsd)}")
            sb.appendLine("📝 Символов в ответе: ${claude.responseText.length}")
            sb.appendLine()
            sb.appendLine("ОТВЕТ:")
            sb.appendLine(claude.responseText)
        }
    } ?: sb.appendLine("Результат отсутствует")
    sb.appendLine()
    
    // YandexGPT результаты
    sb.appendLine("───────────────────────────────────────────────────────")
    sb.appendLine("🔴 YANDEXGPT LITE")
    sb.appendLine("───────────────────────────────────────────────────────")
    result.yandexResult?.let { yandex ->
        if (yandex.error != null) {
            sb.appendLine("❌ Ошибка: ${yandex.error}")
        } else {
            sb.appendLine("⏱️ Время ответа: ${yandex.responseTimeMs} мс")
            sb.appendLine("📥 Входные токены: ${yandex.inputTokens}")
            sb.appendLine("📤 Выходные токены: ${yandex.outputTokens}")
            sb.appendLine("📊 Всего токенов: ${yandex.totalTokens}")
            sb.appendLine("💰 Стоимость: $${String.format("%.6f", yandex.estimatedCostUsd)}")
            sb.appendLine("📝 Символов в ответе: ${yandex.responseText.length}")
            sb.appendLine()
            sb.appendLine("ОТВЕТ:")
            sb.appendLine(yandex.responseText)
        }
    } ?: sb.appendLine("Результат отсутствует")
    sb.appendLine()
    
    // Сводная таблица
    sb.appendLine("═══════════════════════════════════════════════════════")
    sb.appendLine("              СВОДНАЯ ТАБЛИЦА")
    sb.appendLine("═══════════════════════════════════════════════════════")
    sb.appendLine(String.format("%-20s │ %-15s │ %-15s", "Метрика", "Claude", "YandexGPT"))
    sb.appendLine("─".repeat(55))
    
    val claude = result.claudeResult
    val yandex = result.yandexResult
    
    sb.appendLine(String.format("%-20s │ %-15s │ %-15s", 
        "Время (мс)", 
        claude?.responseTimeMs?.toString() ?: "-",
        yandex?.responseTimeMs?.toString() ?: "-"))
    
    sb.appendLine(String.format("%-20s │ %-15s │ %-15s", 
        "Вход. токены", 
        claude?.inputTokens?.toString() ?: "-",
        yandex?.inputTokens?.toString() ?: "-"))
    
    sb.appendLine(String.format("%-20s │ %-15s │ %-15s", 
        "Вых. токены", 
        claude?.outputTokens?.toString() ?: "-",
        yandex?.outputTokens?.toString() ?: "-"))
    
    sb.appendLine(String.format("%-20s │ %-15s │ %-15s", 
        "Всего токенов", 
        claude?.totalTokens?.toString() ?: "-",
        yandex?.totalTokens?.toString() ?: "-"))
    
    sb.appendLine(String.format("%-20s │ %-15s │ %-15s", 
        "Стоимость ($)", 
        if (claude != null) String.format("%.6f", claude.estimatedCostUsd) else "-",
        if (yandex != null) String.format("%.6f", yandex.estimatedCostUsd) else "-"))
    
    sb.appendLine(String.format("%-20s │ %-15s │ %-15s", 
        "Символов", 
        claude?.responseText?.length?.toString() ?: "-",
        yandex?.responseText?.length?.toString() ?: "-"))
    
    sb.appendLine("═══════════════════════════════════════════════════════")
    
    return sb.toString()
}

@Composable
private fun ModelResultCard(
    response: ModelResponse,
    cardColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Заголовок модели
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = response.modelName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = cardColor
                )
                
                // Время ответа
                Text(
                    text = "⏱️ ${response.responseTimeMs} мс",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = cardColor
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Ошибка или ответ
            if (response.error != null) {
                Text(
                    text = "❌ ${response.error}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            } else {
                // Метрики в строку
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardColor.copy(alpha = 0.15f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricItem("📥", "${response.inputTokens}")
                    MetricItem("📤", "${response.outputTokens}")
                    MetricItem("📊", "${response.totalTokens}")
                    MetricItem("💰", "$${String.format("%.6f", response.estimatedCostUsd)}")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Ответ модели
                Text(
                    text = "Ответ:",
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
                Text(
                    text = response.responseText.take(500) + 
                           if (response.responseText.length > 500) "..." else "",
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun MetricItem(icon: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 14.sp)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ComparisonSummaryTable(result: ModelComparisonResult) {
    val claude = result.claudeResult
    val yandex = result.yandexResult
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "📊 Сводная таблица",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Заголовки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Метрика", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("Claude", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color(0xFF6B5B95))
                Text("YandexGPT", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color(0xFFFF6B35))
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            // Время ответа
            SummaryRow(
                label = "⏱️ Время (мс)",
                claudeValue = claude?.responseTimeMs?.toString() ?: "-",
                yandexValue = yandex?.responseTimeMs?.toString() ?: "-",
                claudeWins = (claude?.responseTimeMs ?: Long.MAX_VALUE) < (yandex?.responseTimeMs ?: Long.MAX_VALUE)
            )
            
            // Входные токены
            SummaryRow(
                label = "📥 Вход. токены",
                claudeValue = claude?.inputTokens?.toString() ?: "-",
                yandexValue = yandex?.inputTokens?.toString() ?: "-"
            )
            
            // Выходные токены
            SummaryRow(
                label = "📤 Вых. токены",
                claudeValue = claude?.outputTokens?.toString() ?: "-",
                yandexValue = yandex?.outputTokens?.toString() ?: "-"
            )
            
            // Всего токенов
            SummaryRow(
                label = "📊 Всего токенов",
                claudeValue = claude?.totalTokens?.toString() ?: "-",
                yandexValue = yandex?.totalTokens?.toString() ?: "-"
            )
            
            // Стоимость
            SummaryRow(
                label = "💰 Стоимость",
                claudeValue = if (claude != null) "$${String.format("%.6f", claude.estimatedCostUsd)}" else "-",
                yandexValue = if (yandex != null) "$${String.format("%.6f", yandex.estimatedCostUsd)}" else "-",
                claudeWins = (claude?.estimatedCostUsd ?: Double.MAX_VALUE) < (yandex?.estimatedCostUsd ?: Double.MAX_VALUE)
            )
            
            // Символов в ответе
            SummaryRow(
                label = "📝 Символов",
                claudeValue = claude?.responseText?.length?.toString() ?: "-",
                yandexValue = yandex?.responseText?.length?.toString() ?: "-"
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    claudeValue: String,
    yandexValue: String,
    claudeWins: Boolean? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(
            text = claudeValue + if (claudeWins == true) " ✓" else "",
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = if (claudeWins == true) FontWeight.Bold else FontWeight.Normal,
            color = if (claudeWins == true) Color(0xFF4CAF50) else Color.Unspecified
        )
        Text(
            text = yandexValue + if (claudeWins == false) " ✓" else "",
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = if (claudeWins == false) FontWeight.Bold else FontWeight.Normal,
            color = if (claudeWins == false) Color(0xFF4CAF50) else Color.Unspecified
        )
    }
}

