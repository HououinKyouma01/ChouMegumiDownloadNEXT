package com.example.megumidownload.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File

@Composable
fun FilePickerDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Default to user home if initial path invalid
    val safeInitial = if (initialPath.isNotBlank() && File(initialPath).exists()) initialPath else System.getProperty("user.home") ?: "/"
    
    var currentPath by remember(initialPath) { mutableStateOf(safeInitial) }
    val currentDir = File(currentPath)
    val files = remember(currentPath) {
        currentDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Directory", style = MaterialTheme.typography.titleLarge)
                Text(currentPath, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                // Up navigation
                if (currentDir.parentFile != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { 
                            currentPath = currentDir.parentFile!!.absolutePath 
                        }.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Up")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("..")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(files) { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { 
                                currentPath = file.absolutePath 
                            }.padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Dir")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(file.name)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onConfirm(currentPath) }) { Text("Select") }
                }
            }
        }
    }
}
