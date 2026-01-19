package com.test.chatbot.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.test.chatbot.models.AiProvider
import com.test.chatbot.ui.theme.AccentYellow
import com.test.chatbot.ui.theme.PureBlack
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    currentTemperature: Double,
    currentMaxTokens: Int,
    currentProvider: AiProvider,
    onTemperatureChange: (Double) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onProviderChange: (AiProvider) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var temperature by remember { mutableFloatStateOf(currentTemperature.toFloat()) }
    var maxTokens by remember { mutableIntStateOf(currentMaxTokens) }
    var selectedProvider by remember { mutableStateOf(currentProvider) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(PureBlack)
                .border(1.dp, AccentYellow.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = AccentYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "Настройки",
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
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Разделитель
                GradientDivider()
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Секция выбора модели
                SectionTitle(icon = "🤖", title = "AI Модель")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelChip(
                        icon = "🟣",
                        name = "Claude",
                        isSelected = selectedProvider == AiProvider.CLAUDE,
                        color = Color(0xFFBB86FC),
                        onClick = { selectedProvider = AiProvider.CLAUDE },
                        modifier = Modifier.weight(1f)
                    )
                    
                    ModelChip(
                        icon = "🔴",
                        name = "Yandex",
                        isSelected = selectedProvider == AiProvider.YANDEX_GPT,
                        color = Color(0xFFFF6B35),
                        onClick = { selectedProvider = AiProvider.YANDEX_GPT },
                        modifier = Modifier.weight(1f)
                    )
                    
                    ModelChip(
                        icon = "🦙",
                        name = "Ollama",
                        isSelected = selectedProvider == AiProvider.OLLAMA,
                        color = Color(0xFF00D4AA),
                        onClick = { selectedProvider = AiProvider.OLLAMA },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Секция Temperature
                SectionTitle(icon = "🌡️", title = "Temperature")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Описание
                val tempDescription = when {
                    temperature <= 0.3f -> Pair("❄️ Точный", "Для кода и фактов")
                    temperature <= 0.7f -> Pair("🎯 Сбалансированный", "Универсальный режим")
                    else -> Pair("🔥 Креативный", "Для идей и историй")
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    AccentYellow.copy(alpha = 0.1f),
                                    AccentYellow.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(1.dp, AccentYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = tempDescription.first,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentYellow
                            )
                            Text(
                                text = tempDescription.second,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        
                        // Значение
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PureBlack)
                                .border(1.dp, AccentYellow.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${(temperature * 10).roundToInt() / 10.0}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentYellow
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Слайдер
                Column {
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..1f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentYellow,
                            activeTrackColor = AccentYellow,
                            inactiveTrackColor = Color(0xFF333333),
                            activeTickColor = AccentYellow,
                            inactiveTickColor = Color(0xFF444444)
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                        Text("1.0", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Быстрые кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0f, 0.3f, 0.5f, 0.7f, 1.0f).forEach { value ->
                        QuickTempButton(
                            value = value,
                            isSelected = temperature == value,
                            onClick = { temperature = value },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Секция Max Tokens
                SectionTitle(icon = "📝", title = "Max Tokens")
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Описание и значение
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    AccentYellow.copy(alpha = 0.1f),
                                    AccentYellow.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(1.dp, AccentYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Лимит ответа",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentYellow
                            )
                            Text(
                                text = "Максимум токенов в ответе",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PureBlack)
                                .border(1.dp, AccentYellow.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "$maxTokens",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentYellow
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Лимиты в зависимости от модели
                val maxTokenLimit = when (selectedProvider) {
                    AiProvider.CLAUDE -> 8192
                    AiProvider.YANDEX_GPT -> 2000
                    AiProvider.OLLAMA -> 2048
                }
                val quickTokenButtons = when (selectedProvider) {
                    AiProvider.CLAUDE -> listOf(64, 256, 1024, 4096, 8192)
                    AiProvider.YANDEX_GPT -> listOf(64, 256, 512, 1024, 2000)
                    AiProvider.OLLAMA -> listOf(64, 256, 512, 1024, 2048)
                }
                
                // Корректируем значение если оно превышает лимит
                if (maxTokens > maxTokenLimit) {
                    maxTokens = maxTokenLimit
                }
                
                // Слайдер для maxTokens
                Column {
                    Slider(
                        value = maxTokens.toFloat(),
                        onValueChange = { maxTokens = it.toInt() },
                        valueRange = 16f..maxTokenLimit.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentYellow,
                            activeTrackColor = AccentYellow,
                            inactiveTrackColor = Color(0xFF333333),
                            activeTickColor = AccentYellow,
                            inactiveTickColor = Color(0xFF444444)
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("16", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                        Text("$maxTokenLimit", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Быстрые кнопки для maxTokens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTokenButtons.forEach { value ->
                        QuickTokenButton(
                            value = value,
                            isSelected = maxTokens == value,
                            onClick = { maxTokens = value },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Text("Отмена", fontWeight = FontWeight.Medium)
                    }
                    
                    Button(
                        onClick = {
                            val roundedTemp = (temperature * 10).toInt() / 10.0
                            onTemperatureChange(roundedTemp)
                            onMaxTokensChange(maxTokens)
                            onProviderChange(selectedProvider)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentYellow,
                            contentColor = PureBlack
                        )
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.Bold)
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
private fun SectionTitle(icon: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun ModelChip(
    icon: String,
    name: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier
                        .background(color.copy(alpha = 0.15f))
                        .border(1.5.dp, color, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                }
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun QuickTempButton(
    value: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier
                        .background(AccentYellow)
                } else {
                    Modifier
                        .background(Color(0xFF1A1A1A))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                }
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (value == 0f) "0" else value.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) PureBlack else Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun QuickTokenButton(
    value: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayText = when {
        value >= 1000 -> "${value / 1000}K"
        else -> value.toString()
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier
                        .background(AccentYellow)
                } else {
                    Modifier
                        .background(Color(0xFF1A1A1A))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                }
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) PureBlack else Color.White.copy(alpha = 0.7f)
        )
    }
}
