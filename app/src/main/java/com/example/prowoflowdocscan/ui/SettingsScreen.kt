package com.example.prowoflowdocscan.ui

import android.content.Context
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ProDocScanPrefs", Context.MODE_PRIVATE) }

    var quickSaveEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("quickSaveEnabled", false))
    }

    var defaultSaveFormat by remember {
        mutableStateOf(sharedPrefs.getString("defaultSaveFormat", "PDF") ?: "PDF")
    }

    val formats = listOf("PDF", "IMAGE", "BOTH")
    var expanded by remember { mutableStateOf(value = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Quick Save Enabled", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = quickSaveEnabled,
                    onCheckedChange = {
                        quickSaveEnabled = it
                        sharedPrefs.edit { putBoolean("quickSaveEnabled", it) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Default Save Format", style = MaterialTheme.typography.bodyLarge)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = defaultSaveFormat,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    formats.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format) },
                            onClick = {
                                defaultSaveFormat = format
                                sharedPrefs.edit { putString("defaultSaveFormat", format) }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
