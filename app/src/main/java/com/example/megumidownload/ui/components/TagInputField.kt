package com.example.megumidownload.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    // Current text in the input field (being typed)
    var currentInput by remember { mutableStateOf("") }
    
    // Parse the current CSV value into a list of tags
    val tags = remember(value) {
        value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        
        // Tags display
        if (tags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                items(tags) { tag ->
                    InputChip(
                        selected = true,
                        onClick = { },
                        label = { Text(tag) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val newTags = tags.toMutableList().apply { remove(tag) }
                                    onValueChange(newTags.joinToString(","))
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                            }
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }

        // Input field
        OutlinedTextField(
            value = currentInput,
            onValueChange = { input ->
                if (input.endsWith(",") || input.endsWith("\n")) {
                    // Tag submitted
                    val newTag = input.trim().trim(',')
                    if (newTag.isNotEmpty() && !tags.contains(newTag)) {
                        val newTags = tags + newTag
                        onValueChange(newTags.joinToString(","))
                        currentInput = ""
                    } else if (newTag.isEmpty()) {
                        // Just comma entered
                        currentInput = ""
                    }
                } else {
                    currentInput = input
                }
            },
            placeholder = { Text("Enter group name and press comma") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val newTag = currentInput.trim()
                if (newTag.isNotEmpty() && !tags.contains(newTag)) {
                    val newTags = tags + newTag
                    onValueChange(newTags.joinToString(","))
                    currentInput = ""
                }
            })
        )
    }
}
