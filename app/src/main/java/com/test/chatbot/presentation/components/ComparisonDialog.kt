package com.test.chatbot.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(PureBlack)
                .border(1.dp, AccentYellow.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Заголовок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentYellow.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = null,
                                tint = AccentYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "Сравнение",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Разделитель
                GradientDivider()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Поле ввода
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = {
                        Text(
                            "Введите запрос для сравнения...",
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !isComparing,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentYellow,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFF0D0D0D),
                        unfocusedContainerColor = Color(0xFF0D0D0D),
                        cursorColor = AccentYellow,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Кнопка сравнения
                Button(
                    onClick = { onCompare(queryText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = queryText.isNotBlank() && !isComparing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow,
                        contentColor = PureBlack,
                        disabledContainerColor = AccentYellow.copy(alpha = 0.3f),
                        disabledContentColor = PureBlack.copy(alpha = 0.5f)
                    )
                ) {
                    if (isComparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PureBlack,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сравниваем...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("🚀 Сравнить модели", fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Результаты
                if (comparisonResult != null) {
                    // Кнопки действий
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val formattedText = formatComparisonResultForCopy(comparisonResult)
                                copyToClipboard(context, formattedText)
                                Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF00D4FF)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Копировать", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        
                        OutlinedButton(
                            onClick = onClearResult,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF44336)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Сбросить", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Запрос
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0D0D0D))
                                .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "📝 Запрос",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentYellow
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = comparisonResult.query,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Claude результат
                        comparisonResult.claudeResult?.let { claude ->
                            ModernModelResultCard(
                                response = claude,
                                icon = "🟣",
                                color = Color(0xFFBB86FC)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // YandexGPT результат
                        comparisonResult.yandexResult?.let { yandex ->
                            ModernModelResultCard(
                                response = yandex,
                                icon = "🔴",
                                color = Color(0xFFFF6B35)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Сводная таблица
                        ModernComparisonTable(comparisonResult)
                    }
                } else if (!isComparing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚡",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Введите запрос и нажмите\n\"Сравнить модели\"",
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        AccentYellow.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun ModernModelResultCard(
    response: ModelResponse,
    icon: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = icon, fontSize = 18.sp)
                    Text(
                        text = response.modelName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = color
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⏱️ ${response.responseTimeMs} мс",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (response.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF44336).copy(alpha = 0.15f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "❌ ${response.error}",
                        color = Color(0xFFF44336),
                        fontSize = 12.sp
                    )
                }
            } else {
                // Метрики
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PureBlack.copy(alpha = 0.5f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricBadge("↑", "${response.inputTokens}", Color(0xFF00D4FF))
                    MetricBadge("↓", "${response.outputTokens}", Color(0xFFBB86FC))
                    MetricBadge("Σ", "${response.totalTokens}", AccentYellow)
                    MetricBadge("$", String.format("%.5f", response.estimatedCostUsd), Color(0xFF4CAF50))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Ответ
                Text(
                    text = "Ответ:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = response.responseText.take(400) + 
                           if (response.responseText.length > 400) "..." else "",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ModernComparisonTable(result: ModelComparisonResult) {
    val claude = result.claudeResult
    val yandex = result.yandexResult
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D0D0D))
            .border(1.dp, AccentYellow.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "📊 Сводная таблица",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AccentYellow
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Заголовки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Метрика",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    "🟣 Claude",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBB86FC),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    "🔴 Yandex",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF333333))
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Строки
            TableRow("⏱️ Время", 
                claude?.responseTimeMs?.toString() ?: "-",
                yandex?.responseTimeMs?.toString() ?: "-",
                (claude?.responseTimeMs ?: Long.MAX_VALUE) < (yandex?.responseTimeMs ?: Long.MAX_VALUE)
            )
            TableRow("↑ Вход", 
                claude?.inputTokens?.toString() ?: "-",
                yandex?.inputTokens?.toString() ?: "-"
            )
            TableRow("↓ Выход", 
                claude?.outputTokens?.toString() ?: "-",
                yandex?.outputTokens?.toString() ?: "-"
            )
            TableRow("Σ Всего", 
                claude?.totalTokens?.toString() ?: "-",
                yandex?.totalTokens?.toString() ?: "-"
            )
            TableRow("💰 Цена", 
                if (claude != null) "$${String.format("%.5f", claude.estimatedCostUsd)}" else "-",
                if (yandex != null) "$${String.format("%.5f", yandex.estimatedCostUsd)}" else "-",
                (claude?.estimatedCostUsd ?: Double.MAX_VALUE) < (yandex?.estimatedCostUsd ?: Double.MAX_VALUE)
            )
        }
    }
}

@Composable
private fun TableRow(
    label: String,
    claudeValue: String,
    yandexValue: String,
    claudeWins: Boolean? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = claudeValue + if (claudeWins == true) " ✓" else "",
            fontSize = 12.sp,
            fontWeight = if (claudeWins == true) FontWeight.Bold else FontWeight.Normal,
            color = if (claudeWins == true) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = yandexValue + if (claudeWins == false) " ✓" else "",
            fontSize = 12.sp,
            fontWeight = if (claudeWins == false) FontWeight.Bold else FontWeight.Normal,
            color = if (claudeWins == false) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Comparison Result", text)
    clipboard.setPrimaryClip(clip)
}

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
    
    sb.appendLine("───────────────────────────────────────────────────────")
    sb.appendLine("🟣 CLAUDE SONNET 4")
    sb.appendLine("───────────────────────────────────────────────────────")
    result.claudeResult?.let { claude ->
        if (claude.error != null) {
            sb.appendLine("❌ Ошибка: ${claude.error}")
        } else {
            sb.appendLine("⏱️ Время: ${claude.responseTimeMs} мс")
            sb.appendLine("↑ Входные токены: ${claude.inputTokens}")
            sb.appendLine("↓ Выходные токены: ${claude.outputTokens}")
            sb.appendLine("Σ Всего токенов: ${claude.totalTokens}")
            sb.appendLine("💰 Стоимость: $${String.format("%.6f", claude.estimatedCostUsd)}")
            sb.appendLine()
            sb.appendLine("ОТВЕТ:")
            sb.appendLine(claude.responseText)
        }
    } ?: sb.appendLine("Результат отсутствует")
    sb.appendLine()
    
    sb.appendLine("───────────────────────────────────────────────────────")
    sb.appendLine("🔴 YANDEXGPT LITE")
    sb.appendLine("───────────────────────────────────────────────────────")
    result.yandexResult?.let { yandex ->
        if (yandex.error != null) {
            sb.appendLine("❌ Ошибка: ${yandex.error}")
        } else {
            sb.appendLine("⏱️ Время: ${yandex.responseTimeMs} мс")
            sb.appendLine("↑ Входные токены: ${yandex.inputTokens}")
            sb.appendLine("↓ Выходные токены: ${yandex.outputTokens}")
            sb.appendLine("Σ Всего токенов: ${yandex.totalTokens}")
            sb.appendLine("💰 Стоимость: $${String.format("%.6f", yandex.estimatedCostUsd)}")
            sb.appendLine()
            sb.appendLine("ОТВЕТ:")
            sb.appendLine(yandex.responseText)
        }
    } ?: sb.appendLine("Результат отсутствует")
    sb.appendLine()
    
    sb.appendLine("═══════════════════════════════════════════════════════")
    
    return sb.toString()
}
