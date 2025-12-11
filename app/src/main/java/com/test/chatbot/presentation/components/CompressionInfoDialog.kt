package com.test.chatbot.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.test.chatbot.models.CompressionState
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack

@Composable
fun CompressionInfoDialog(
    compressionState: CompressionState,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A),
                            Color(0xFF0D0D0D)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AccentYellow.copy(alpha = 0.6f),
                            AccentYellow.copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Заголовок
                Text(
                    text = "📦 Компрессия диалога",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentYellow,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Divider(color = AccentYellow.copy(alpha = 0.3f))
                
                // Описание
                InfoSection(
                    title = "Что это?",
                    content = "Компрессия диалога — это механизм сжатия истории разговора для экономии токенов и ускорения ответов модели."
                )
                
                InfoSection(
                    title = "Как это работает?",
                    content = """
• Каждые N сообщений (настраивается) система создаёт краткое резюме разговора
• Резюме заменяет оригинальные сообщения, сохраняя контекст
• Последние несколько сообщений остаются без изменений для точности
                    """.trimIndent()
                )
                
                InfoSection(
                    title = "Преимущества",
                    content = """
✅ Экономия токенов (до 60-80%)
✅ Быстрее ответы модели
✅ Возможность вести длинные диалоги
✅ Снижение стоимости запросов
                    """.trimIndent()
                )
                
                InfoSection(
                    title = "Особенности",
                    content = """
⚠️ Некоторые детали могут быть упрощены
⚠️ Лучше работает для информационных диалогов
⚠️ Для творческих задач рекомендуется отключить
                    """.trimIndent()
                )
                
                // Текущее summary если есть
                if (compressionState.hasSummary && compressionState.summaryPreview.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0A0A0A))
                            .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "📝 Текущее резюме",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = compressionState.summaryPreview,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                
                // Кнопка закрытия
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow,
                        contentColor = PureBlack
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Понятно",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AccentYellow
        )
        Text(
            text = content,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f),
            lineHeight = 18.sp
        )
    }
}

