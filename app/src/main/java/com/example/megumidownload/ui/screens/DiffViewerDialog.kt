package com.example.megumidownload.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun DiffViewerDialog(
    localContent: String,
    remoteContent: String,
    onDismiss: () -> Unit,
    onResolve: (String) -> Unit // Returns the final content to save (to both if merge, or just one if overwrite)
) {
    // Simple diff logic: Find lines unique to each
    val localLines = localContent.lines()
    val remoteLines = remoteContent.lines()
    
    val uniqueToLocal = localLines - remoteLines.toSet()
    val uniqueToRemote = remoteLines - localLines.toSet()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Full width
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Conflict Resolution: replace.txt", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.weight(1f)) {
                    // Local Column
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text("Local (Your Device)", style = MaterialTheme.typography.titleMedium)
                        Divider()
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            localLines.forEach { line ->
                                Text(
                                    text = line,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (uniqueToLocal.contains(line) && line.isNotBlank()) Color.Green.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .padding(horizontal = 8.dp)
                    )
                    
                    // Remote Column
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text("Remote (Server)", style = MaterialTheme.typography.titleMedium)
                        Divider()
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            remoteLines.forEach { line ->
                                Text(
                                    text = line,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (uniqueToRemote.contains(line) && line.isNotBlank()) Color.Red.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onResolve(localContent) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Keep Local")
                    }
                    
                    Button(
                        onClick = { onResolve(remoteContent) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use Remote")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { 
                        // Merge: Union of all lines, distinct, sorted? Or just appended?
                        // Usually appending unique remote lines to local lines is safer for replace.txt rules
                        val merged = (localLines + uniqueToRemote).distinct().joinToString("\n")
                        onResolve(merged)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Merge Both")
                }
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}
