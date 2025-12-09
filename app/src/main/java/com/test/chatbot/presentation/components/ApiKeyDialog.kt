package com.test.chatbot.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.chatbot.models.AiProvider

@Composable
fun ApiKeyDialog(
    currentApiKey: String,
    currentYandexApiKey: String = "",
    currentYandexFolderId: String = "",
    selectedProvider: AiProvider = AiProvider.CLAUDE,
    onSave: (claudeKey: String, yandexKey: String, yandexFolderId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var claudeApiKey by remember { mutableStateOf(currentApiKey) }
    var yandexApiKey by remember { mutableStateOf(currentYandexApiKey) }
    var yandexFolderId by remember { mutableStateOf(currentYandexFolderId) }
    var showYandex by remember { mutableStateOf(selectedProvider == AiProvider.YANDEX_GPT) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔑 API Ключи", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Claude API Key
                Text(
                    text = "Claude API Key",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = claudeApiKey,
                    onValueChange = { claudeApiKey = it },
                    placeholder = { Text("sk-ant-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // YandexGPT Section
                Text(
                    text = "YandexGPT API Key",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = yandexApiKey,
                    onValueChange = { yandexApiKey = it },
                    placeholder = { Text("AQVN...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "YandexGPT Folder ID",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = yandexFolderId,
                    onValueChange = { yandexFolderId = it },
                    placeholder = { Text("b1g...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onSave(claudeApiKey, yandexApiKey, yandexFolderId)
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
