package com.test.chatbot.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
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
import com.test.chatbot.models.CompressionSettings
import com.test.chatbot.models.CompressionState
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack

@Composable
fun CompressionPanel(
    compressionSettings: CompressionSettings,
    compressionState: CompressionState,
    isCompressing: Boolean,
    onToggleCompression: (Boolean) -> Unit,
    onUpdateThreshold: (Int) -> Unit,
    onManualCompress: () -> Unit,
    onShowInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF1A1A1A),
            Color(0xFF252525),
            Color(0xFF1A1A1A)
        )
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(brush = gradientBrush)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        AccentYellow.copy(alpha = 0.3f),
                        AccentYellow.copy(alpha = 0.6f),
                        AccentYellow.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .animateContentSize()
    ) {
        // Заголовок панели
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Compress,
                    contentDescription = "Compress",
                    tint = if (compressionSettings.enabled) AccentYellow else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "КОМПРЕССИЯ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentYellow,
                    letterSpacing = 1.sp
                )
                
                // Статус
                if (compressionSettings.enabled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentYellow.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ВКЛ",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentYellow
                        )
                    }
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Показатель экономии
                if (compressionState.hasSummary) {
                    Text(
                        text = "💾 ${compressionState.savingsPercent.toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Расширенная панель
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Divider(color = AccentYellow.copy(alpha = 0.2f))
                
                // Переключатель компрессии
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Автоматическая компрессия",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Сжимать историю каждые ${compressionSettings.threshold} сообщений",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    
                    Switch(
                        checked = compressionSettings.enabled,
                        onCheckedChange = onToggleCompression,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureBlack,
                            checkedTrackColor = AccentYellow,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
                
                // Слайдер порога
                if (compressionSettings.enabled) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Порог сжатия",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${compressionSettings.threshold} сообщений",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentYellow
                            )
                        }
                        
                        Slider(
                            value = compressionSettings.threshold.toFloat(),
                            onValueChange = { onUpdateThreshold(it.toInt()) },
                            valueRange = 6f..20f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentYellow,
                                activeTrackColor = AccentYellow,
                                inactiveTrackColor = Color(0xFF333333)
                            )
                        )
                    }
                }
                
                // Статистика компрессии
                if (compressionState.hasSummary) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D0D0D))
                            .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📊 Статистика компрессии",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = "×${compressionState.compressionCount}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(
                                    label = "Оригинал",
                                    value = "${compressionState.originalTokenCount}",
                                    unit = "токенов"
                                )
                                StatItem(
                                    label = "После",
                                    value = "${compressionState.compressedTokenCount}",
                                    unit = "токенов"
                                )
                                StatItem(
                                    label = "Экономия",
                                    value = "${compressionState.savedTokens}",
                                    unit = "токенов",
                                    highlight = true
                                )
                            }
                            
                            // Прогресс бар экономии
                            val progress by animateFloatAsState(
                                targetValue = compressionState.savingsPercent / 100f,
                                animationSpec = tween(500)
                            )
                            
                            Column {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF4CAF50),
                                    trackColor = Color(0xFF333333)
                                )
                                
                                Text(
                                    text = "Экономия: ${compressionState.savingsPercent.toInt()}%",
                                    fontSize = 10.sp,
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                // Кнопки действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Ручная компрессия
                    Button(
                        onClick = onManualCompress,
                        enabled = !isCompressing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentYellow,
                            contentColor = PureBlack,
                            disabledContainerColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCompressing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PureBlack
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Compress,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCompressing) "Сжатие..." else "Сжать сейчас",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Инфо
                    OutlinedButton(
                        onClick = onShowInfo,
                        modifier = Modifier.width(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentYellow
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(AccentYellow, AccentYellow))
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    unit: String,
    highlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFF4CAF50) else Color.White
        )
        Text(
            text = unit,
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

