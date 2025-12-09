package com.test.chatbot.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.chatbot.models.AiProvider
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    currentTemperature: Double,
    currentProvider: AiProvider,
    onTemperatureChange: (Double) -> Unit,
    onProviderChange: (AiProvider) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var temperature by remember { mutableFloatStateOf(currentTemperature.toFloat()) }
    var selectedProvider by remember { mutableStateOf(currentProvider) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚙️ Настройки", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Секция выбора модели
                Text(
                    text = "🤖 AI Модель",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedProvider == AiProvider.CLAUDE,
                        onClick = { selectedProvider = AiProvider.CLAUDE },
                        label = { Text("Claude") }
                    )
                    FilterChip(
                        selected = selectedProvider == AiProvider.YANDEX_GPT,
                        onClick = { selectedProvider = AiProvider.YANDEX_GPT },
                        label = { Text("YandexGPT") }
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Секция Temperature
                Text(
                    text = "🌡️ Temperature",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                // Описание текущего значения
                val tempDescription = when {
                    temperature <= 0.3f -> "🧊 Точный (для кода, фактов)"
                    temperature <= 0.7f -> "⚖️ Сбалансированный (универсальный)"
                    else -> "🔥 Креативный (для идей, историй)"
                }
                
                Text(
                    text = tempDescription,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Слайдер
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("0", fontSize = 12.sp)
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..1f,
                        steps = 9,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text("1.0", fontSize = 12.sp)
                }
                
                // Текущее значение
                Text(
                    text = "Текущее значение: ${(temperature * 10).roundToInt() / 10.0}",
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                // Быстрые кнопки
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = temperature == 0f,
                        onClick = { temperature = 0f },
                        label = { Text("0") }
                    )
                    FilterChip(
                        selected = temperature == 0.5f,
                        onClick = { temperature = 0.5f },
                        label = { Text("0.5") }
                    )
                    FilterChip(
                        selected = temperature == 0.7f,
                        onClick = { temperature = 0.7f },
                        label = { Text("0.7") }
                    )
                    FilterChip(
                        selected = temperature == 1.0f,
                        onClick = { temperature = 1.0f },
                        label = { Text("1.0") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val roundedTemp = (temperature * 10).toInt() / 10.0
                    onTemperatureChange(roundedTemp)
                    onProviderChange(selectedProvider)
                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        modifier = modifier
    )
}
