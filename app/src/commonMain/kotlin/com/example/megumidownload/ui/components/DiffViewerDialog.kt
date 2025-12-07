package com.example.megumidownload.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun DiffViewerDialog(
    localContent: String,
    remoteContent: String,
    onDismiss: () -> Unit,
    onResolve: (String) -> Unit
) {
    var mergedContent by remember { mutableStateOf(localContent) } // Default to local
    
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Diff Viewer / Merger", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Simulated Diff View", style = MaterialTheme.typography.bodySmall)
                
                Text("Remote Content:", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = remoteContent,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Merged Content (Edit this):", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = mergedContent,
                    onValueChange = { mergedContent = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onResolve(mergedContent) }) { Text("Perform Merge") }
                }
            }
        }
    }
}
