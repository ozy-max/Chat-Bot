package com.test.chatbot.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        else -> "AI"
    }
    
    val providerColor = when {
        message.isUser -> AccentBlue
        message.provider == AiProvider.YANDEX_GPT -> Color(0xFFFF5722) // Оранжевый для Яндекса
        message.provider == AiProvider.CLAUDE -> AccentPurple // Фиолетовый для Claude
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
                
                // Текст сообщения
                Text(
                    text = message.text,
                    color = Color.White,
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
