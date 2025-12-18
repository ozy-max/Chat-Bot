package com.test.chatbot.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
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

@Composable
fun ApiKeyDialog(
    currentApiKey: String,
    currentYandexApiKey: String = "",
    currentYandexFolderId: String = "",
    currentTodoistToken: String = "",
    selectedProvider: AiProvider = AiProvider.CLAUDE,
    onSave: (claudeKey: String, yandexKey: String, yandexFolderId: String, todoistToken: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var claudeApiKey by remember { mutableStateOf(currentApiKey) }
    var yandexApiKey by remember { mutableStateOf(currentYandexApiKey) }
    var yandexFolderId by remember { mutableStateOf(currentYandexFolderId) }
    var todoistToken by remember { mutableStateOf(currentTodoistToken) }
    
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
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = AccentYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "API Ключи",
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
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Claude секция
                SectionHeader(
                    icon = "🟣",
                    title = "Claude",
                    color = Color(0xFFBB86FC)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                ModernTextField(
                    value = claudeApiKey,
                    onValueChange = { claudeApiKey = it },
                    placeholder = "sk-ant-api03-...",
                    label = "API Key"
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // YandexGPT секция
                SectionHeader(
                    icon = "🔴",
                    title = "YandexGPT",
                    color = Color(0xFFFF6B35)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                ModernTextField(
                    value = yandexApiKey,
                    onValueChange = { yandexApiKey = it },
                    placeholder = "AQVN...",
                    label = "API Key"
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                ModernTextField(
                    value = yandexFolderId,
                    onValueChange = { yandexFolderId = it },
                    placeholder = "b1g...",
                    label = "Folder ID"
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Todoist секция
                SectionHeader(
                    icon = "✅",
                    title = "Todoist",
                    color = Color(0xFFE44332)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                ModernTextField(
                    value = todoistToken,
                    onValueChange = { todoistToken = it },
                    placeholder = "55ba907df1b33fd...",
                    label = "API Token"
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Отмена
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
                    
                    // Сохранить
                    Button(
                        onClick = { onSave(claudeApiKey, yandexApiKey, yandexFolderId, todoistToken) },
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
private fun SectionHeader(
    icon: String,
    title: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = icon, fontSize = 16.sp)
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color.White.copy(alpha = 0.3f)
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
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
    }
}
