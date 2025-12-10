package com.test.chatbot.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.chatbot.models.ContextStatus
import com.test.chatbot.models.TokenStats
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack

@Composable
fun TokenStatsBar(
    stats: TokenStats,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val contextColor by animateColorAsState(
        targetValue = when (stats.contextStatus) {
            ContextStatus.NORMAL -> AccentYellow
            ContextStatus.MODERATE -> Color(0xFFFFFF00)
            ContextStatus.WARNING -> Color(0xFFFF9800)
            ContextStatus.CRITICAL -> Color(0xFFF44336)
        },
        label = "contextColor"
    )
    
    val progressValue by animateFloatAsState(
        targetValue = (stats.contextUsagePercent / 100f).coerceIn(0f, 1f),
        label = "progress"
    )
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PureBlack
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Компактная шапка (всегда видна)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Левая часть: бейджи со статистикой
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Входящие токены
                    StatBadge(
                        icon = "↑",
                        value = stats.lastInputTokens,
                        color = Color(0xFF00D4FF) // Неоновый синий
                    )
                    
                    // Исходящие токены
                    StatBadge(
                        icon = "↓",
                        value = stats.lastOutputTokens,
                        color = Color(0xFFBB86FC) // Фиолетовый
                    )
                    
                    // Разделитель
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color(0xFF333333))
                    )
                    
                    // Всего за сессию
                    StatBadge(
                        icon = "Σ",
                        value = stats.totalTokens,
                        color = contextColor,
                        isHighlighted = true
                    )
                }
                
                // Правая часть: мини-прогресс и кнопка
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Мини прогресс-бар
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${String.format("%.1f", stats.contextUsagePercent)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contextColor
                        )
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1A1A1A))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressValue)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(contextColor, contextColor.copy(alpha = 0.6f))
                                        )
                                    )
                            )
                        }
                    }
                    
                    // Кнопка развернуть/свернуть
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                            .border(1.dp, AccentYellow.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            tint = AccentYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // Развёрнутое содержимое
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    // Градиентный разделитель
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        AccentYellow.copy(alpha = 0.5f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Три карточки статистики
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModernTokenCard(
                            title = "ЗАПРОС",
                            lastValue = stats.lastInputTokens,
                            totalValue = stats.totalInputTokens,
                            color = Color(0xFF00D4FF),
                            modifier = Modifier.weight(1f)
                        )
                        
                        ModernTokenCard(
                            title = "ОТВЕТ",
                            lastValue = stats.lastOutputTokens,
                            totalValue = stats.totalOutputTokens,
                            color = Color(0xFFBB86FC),
                            modifier = Modifier.weight(1f)
                        )
                        
                        ModernTokenCard(
                            title = "СЕССИЯ",
                            lastValue = stats.lastTotalTokens,
                            totalValue = stats.totalTokens,
                            color = contextColor,
                            isAccent = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Прогресс контекста
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D0D0D))
                            .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚡ Контекст модели",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "${formatTokenCount(stats.totalInputTokens)} / ${formatTokenCount(stats.modelInputLimit)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contextColor
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Стильный прогресс-бар
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1A1A1A))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressValue)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    contextColor,
                                                    contextColor.copy(alpha = 0.7f)
                                                )
                                            )
                                        )
                                )
                            }
                            
                            // Предупреждение
                            if (stats.contextStatus == ContextStatus.WARNING || stats.contextStatus == ContextStatus.CRITICAL) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(contextColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (stats.contextStatus == ContextStatus.CRITICAL) "🚨" else "⚠️",
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (stats.contextStatus == ContextStatus.CRITICAL) 
                                            "Контекст заполнен! Начните новый чат" 
                                        else 
                                            "Контекст заполняется (${String.format("%.0f", stats.contextUsagePercent)}%)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = contextColor
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Количество запросов
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "📝 Запросов: ${stats.requestCount}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            // Тонкая линия внизу
            if (!isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AccentYellow.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun StatBadge(
    icon: String,
    value: Int,
    color: Color,
    isHighlighted: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (isHighlighted) {
                    Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    color.copy(alpha = 0.2f),
                                    color.copy(alpha = 0.1f)
                                )
                            )
                        )
                        .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                } else {
                    Modifier.background(Color(0xFF1A1A1A))
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = formatTokenCount(value),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ModernTokenCard(
    title: String,
    lastValue: Int,
    totalValue: Int,
    color: Color,
    isAccent: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isAccent) {
                    Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    color.copy(alpha = 0.15f),
                                    color.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                }
            )
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Заголовок
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Последнее значение
            Text(
                text = formatTokenCount(lastValue),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Общее значение
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Σ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = formatTokenCount(totalValue),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatTokenCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${String.format("%.1f", count / 1_000_000.0)}M"
        count >= 10_000 -> "${String.format("%.0f", count / 1_000.0)}K"
        count >= 1_000 -> "${String.format("%.1f", count / 1_000.0)}K"
        else -> count.toString()
    }
}
