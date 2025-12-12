package com.test.chatbot.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.chatbot.data.memory.MemoryState
import com.test.chatbot.models.CompressionSettings
import com.test.chatbot.models.CompressionState
import com.test.chatbot.models.TokenStats
import com.test.chatbot.ui.theme.AccentYellow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bottom Sheet для управления AI функциями (компрессия + память + статистика)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFeaturesBottomSheet(
    sheetState: SheetState,
    // Компрессия
    compressionSettings: CompressionSettings,
    compressionState: CompressionState,
    isCompressing: Boolean,
    onToggleCompression: (Boolean) -> Unit,
    onUpdateThreshold: (Int) -> Unit,
    onManualCompress: () -> Unit,
    // Память
    memoryState: MemoryState,
    onToggleMemory: (Boolean) -> Unit,
    onClearAllMemories: () -> Unit,
    // Статистика токенов
    tokenStats: TokenStats,
    // Общее
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }
    
    // Диалог подтверждения очистки
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Очистить память?") },
            text = { Text("Summary предыдущего диалога будет удалён.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllMemories()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Отмена")
                }
            },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0D0D),
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Заголовок
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "AI Функции",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }
            
            // Вкладки
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = AccentYellow,
                divider = {
                    HorizontalDivider(color = Color(0xFF333333))
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compress,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Компрессия")
                            if (compressionState.isEnabled) {
                                Badge(
                                    containerColor = Color(0xFF4CAF50)
                                ) {
                                    Text("ON", fontSize = 8.sp)
                                }
                            }
                        }
                    },
                    selectedContentColor = AccentYellow,
                    unselectedContentColor = Color.White.copy(alpha = 0.5f)
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Память")
                            if (memoryState.hasSummary) {
                                Badge(
                                    containerColor = Color(0xFF4CAF50)
                                ) {
                                    Text("✓", fontSize = 8.sp)
                                }
                            }
                        }
                    },
                    selectedContentColor = AccentYellow,
                    unselectedContentColor = Color.White.copy(alpha = 0.5f)
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Токены")
                        }
                    },
                    selectedContentColor = AccentYellow,
                    unselectedContentColor = Color.White.copy(alpha = 0.5f)
                )
            }
            
            // Контент вкладок
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                when (selectedTab) {
                    0 -> CompressionTabContent(
                        compressionSettings = compressionSettings,
                        compressionState = compressionState,
                        isCompressing = isCompressing,
                        onToggleCompression = onToggleCompression,
                        onUpdateThreshold = onUpdateThreshold,
                        onManualCompress = onManualCompress
                    )
                    1 -> MemoryTabContent(
                        memoryState = memoryState,
                        onToggleMemory = onToggleMemory,
                        onClearClick = { showClearConfirm = true }
                    )
                    2 -> TokenStatsTabContent(
                        tokenStats = tokenStats
                    )
                }
            }
        }
    }
}

@Composable
private fun CompressionTabContent(
    compressionSettings: CompressionSettings,
    compressionState: CompressionState,
    isCompressing: Boolean,
    onToggleCompression: (Boolean) -> Unit,
    onUpdateThreshold: (Int) -> Unit,
    onManualCompress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Переключатель
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Сжатие диалога",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "Автоматически суммаризирует историю",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = compressionSettings.enabled,
                onCheckedChange = onToggleCompression,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentYellow,
                    checkedTrackColor = AccentYellow.copy(alpha = 0.3f)
                )
            )
        }
        
        if (compressionSettings.enabled) {
            HorizontalDivider(color = Color(0xFF333333))
            
            // Порог сжатия
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Порог сжатия",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${compressionSettings.threshold} сообщений",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentYellow
                    )
                }
                Slider(
                    value = compressionSettings.threshold.toFloat(),
                    onValueChange = { onUpdateThreshold(it.toInt()) },
                    valueRange = 6f..30f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentYellow,
                        activeTrackColor = AccentYellow,
                        inactiveTrackColor = Color(0xFF333333)
                    )
                )
            }
            
            // Статистика
            if (compressionState.compressionCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1A1A1A)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📊 Статистика сжатия",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AccentYellow
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("Сжатий", "${compressionState.compressionCount}")
                            StatItem("Было", "${compressionState.originalTokenCount}")
                            StatItem("Стало", "${compressionState.compressedTokenCount}")
                            StatItem("Экономия", "${compressionState.savingsPercent.toInt()}%")
                        }
                    }
                }
            }
            
            // Кнопка ручного сжатия
            Button(
                onClick = onManualCompress,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCompressing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentYellow.copy(alpha = 0.15f),
                    contentColor = AccentYellow
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCompressing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AccentYellow,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сжатие...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Compress,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сжать сейчас")
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun MemoryTabContent(
    memoryState: MemoryState,
    onToggleMemory: (Boolean) -> Unit,
    onClearClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Переключатель
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Долговременная память",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "Сохраняет summary диалога между сессиями",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = memoryState.isEnabled,
                onCheckedChange = onToggleMemory,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                )
            )
        }
        
        if (memoryState.isEnabled) {
            HorizontalDivider(color = Color(0xFF333333))
            
            // Информация о работе
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1A1A)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "💡 Как это работает",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AccentYellow
                    )
                    Text(
                        text = "• При сворачивании или закрытии приложения автоматически создаётся summary диалога",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "• Summary сохраняется в SQLite и доступен между сессиями",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "• При следующем запуске агент получит этот контекст и продолжит разговор с учётом прошлой информации",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                }
            }
            
            // Сохранённый summary
            if (memoryState.hasSummary) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Summary сохранён",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        
                        if (memoryState.summaryPreview.isNotBlank()) {
                            Text(
                                text = memoryState.summaryPreview,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                
                // Кнопка очистки
                OutlinedButton(
                    onClick = onClearClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red.copy(alpha = 0.8f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.Red.copy(alpha = 0.3f))
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Очистить память", fontSize = 13.sp)
                }
            } else {
                // Нет сохранённого summary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Память пуста",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Очистите чат чтобы сохранить диалог",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenStatsTabContent(
    tokenStats: TokenStats
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        Text(
            text = "📊 Статистика использования токенов",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
        
        // Последний запрос
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Последний запрос",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = AccentYellow
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TokenStatCard(
                        label = "Вход",
                        value = tokenStats.lastInputTokens,
                        icon = "📥",
                        color = Color(0xFF2196F3)
                    )
                    TokenStatCard(
                        label = "Выход",
                        value = tokenStats.lastOutputTokens,
                        icon = "📤",
                        color = Color(0xFF4CAF50)
                    )
                    TokenStatCard(
                        label = "Всего",
                        value = tokenStats.lastInputTokens + tokenStats.lastOutputTokens,
                        icon = "📊",
                        color = AccentYellow
                    )
                }
            }
        }
        
        // Общая статистика сессии
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "За сессию",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF9C27B0)
                    )
                    Badge(
                        containerColor = Color(0xFF9C27B0).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${tokenStats.requestCount} запросов",
                            color = Color(0xFF9C27B0),
                            fontSize = 10.sp
                        )
                    }
                }
                
                HorizontalDivider(color = Color(0xFF333333))
                
                // Детальная статистика
                TokenStatRow("📥 Входящие токены", tokenStats.totalInputTokens)
                TokenStatRow("📤 Исходящие токены", tokenStats.totalOutputTokens)
                
                HorizontalDivider(color = Color(0xFF333333))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎯 ВСЕГО ТОКЕНОВ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${tokenStats.totalTokens}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AccentYellow
                    )
                }
            }
        }
        
        // Подсказка
        Text(
            text = "💡 Токены — единицы текста, используемые API. Чем меньше токенов, тем дешевле запрос.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun TokenStatCard(
    label: String,
    value: Int,
    icon: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 20.sp)
        Text(
            text = "$value",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TokenStatRow(
    label: String,
    value: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = "$value",
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = Color.White
        )
    }
}


