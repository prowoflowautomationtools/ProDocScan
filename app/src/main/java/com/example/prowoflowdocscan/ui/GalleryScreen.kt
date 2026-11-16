package com.example.prowoflowdocscan.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.prowoflowdocscan.data.Document
import com.example.prowoflowdocscan.data.DocumentRepository
import com.example.prowoflowdocscan.data.PageSize
import com.example.prowoflowdocscan.data.SaveFormat
import com.example.prowoflowdocscan.viewmodel.DocumentViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: DocumentViewModel,
    onDocumentClick: (Document) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = viewModel.repository

    val documents by viewModel.documents.collectAsState()
    val filteredDocuments by viewModel.filteredDocuments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortByName by viewModel.sortByName.collectAsState()

    val prefs = remember { context.getSharedPreferences("ProDocScanPrefs", android.content.Context.MODE_PRIVATE) }
    var quickSaveEnabled by remember { mutableStateOf(prefs.getBoolean("quickSaveEnabled", false)) }

    var pendingScanResult by remember { mutableStateOf<GmsDocumentScanningResult?>(null) }
    var documentToRename by remember { mutableStateOf<Document?>(null) }
    var showMergePrompt by remember { mutableStateOf(false) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null && quickSaveEnabled) {
                val customName = "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
                val savedFormatString = prefs.getString("defaultSaveFormat", "PDF") ?: "PDF"
                val mappedFormat = try {
                    SaveFormat.valueOf(savedFormatString)
                } catch (e: Exception) {
                    SaveFormat.PDF
                }
                repository.saveDocument(scanResult, mappedFormat, customName, PageSize.ORIGINAL)
                viewModel.triggerRefresh()
            } else {
                pendingScanResult = scanResult
            }
        }
    }

    val scope = rememberCoroutineScope()
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                repository.importPdf(context, uri)
                viewModel.triggerRefresh()
            }
        }
    }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember(options) {
        GmsDocumentScanning.getClient(options)
    }

    var selectedFiles by remember { mutableStateOf(emptySet<File>()) }
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selectedFiles.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedFiles.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedFiles = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        val onlyImages = selectedFiles.all { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
                        if (onlyImages && selectedFiles.size > 1) {
                            IconButton(onClick = { showMergePrompt = true }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Merge to PDF")
                            }
                        }

                        IconButton(onClick = {
                            val uris = selectedFiles.mapNotNull { file ->
                                if (file.exists()) {
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                } else null
                            }
                            if (uris.isNotEmpty()) {
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Documents"))
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            repository.deleteDocuments(selectedFiles.toList())
                            selectedFiles = emptySet()
                            viewModel.triggerRefresh()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                Column {
                    LargeTopAppBar(
                        title = {
                            Text(
                                text = "Scans",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(onClick = { viewModel.toggleSortByName() }) {
                                Icon(Icons.Default.Sort, contentDescription = "Toggle Sort")
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search documents") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showFabMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                modifier = Modifier.padding(bottom = 24.dp).height(64.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Document", modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Add Document", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        if (filteredDocuments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No documents found." else "No documents scanned yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredDocuments) { doc ->
                    val file = File(doc.path)
                    val isSelected = selectedFiles.contains(file)
                    DocumentItem(
                        document = doc,
                        isSelected = isSelected,
                        onClick = {
                            if (selectedFiles.isNotEmpty()) {
                                selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                            } else {
                                onDocumentClick(doc)
                            }
                        },
                        onLongClick = {
                            selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                        },
                        onRenameClick = {
                            documentToRename = doc
                        },
                        repository = repository
                    )
                }
            }
        }

        if (pendingScanResult != null) {
            var customName by remember {
                mutableStateOf("Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}")
            }
            var selectedPageSize by remember { mutableStateOf(PageSize.ORIGINAL) }
            var expanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { pendingScanResult = null },
                title = { Text("Save Document As") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Document Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedPageSize.name.replace("_", " "),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Page Size") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                PageSize.entries.forEach { size ->
                                    DropdownMenuItem(
                                        text = { Text(size.name.replace("_", " ")) },
                                        onClick = {
                                            selectedPageSize = size
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(onClick = {
                            repository.saveDocument(pendingScanResult!!, SaveFormat.PDF, customName, selectedPageSize)
                            pendingScanResult = null
                            viewModel.triggerRefresh()
                        }) {
                            Text("PDF Only")
                        }
                        TextButton(onClick = {
                            repository.saveDocument(pendingScanResult!!, SaveFormat.IMAGE, customName, selectedPageSize)
                            pendingScanResult = null
                            viewModel.triggerRefresh()
                        }) {
                            Text("Image(s) Only")
                        }
                        TextButton(onClick = {
                            repository.saveDocument(pendingScanResult!!, SaveFormat.BOTH, customName, selectedPageSize)
                            pendingScanResult = null
                            viewModel.triggerRefresh()
                        }) {
                            Text("Both")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { pendingScanResult = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (documentToRename != null) {
            var newName by remember { mutableStateOf(documentToRename!!.name.substringBeforeLast(".")) }
            AlertDialog(
                onDismissRequest = { documentToRename = null },
                title = { Text("Rename Document") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val doc = documentToRename
                        if (doc != null && newName.isNotBlank()) {
                            repository.renameDocument(doc, newName)
                            viewModel.triggerRefresh()
                        }
                        documentToRename = null
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { documentToRename = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showMergePrompt) {
            var mergeName by remember { mutableStateOf("Merged_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}") }
            val coroutineScope = rememberCoroutineScope()
            var isMerging by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isMerging) showMergePrompt = false },
                title = { Text("Merge to PDF") },
                text = {
                    Column {
                        if (isMerging) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            OutlinedTextField(
                                value = mergeName,
                                onValueChange = { mergeName = it },
                                label = { Text("PDF Name") },
                                singleLine = true
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!isMerging) {
                        TextButton(onClick = {
                            isMerging = true
                            coroutineScope.launch {
                                val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                                if (directory != null) {
                                    var destFile = File(directory, "$mergeName.pdf")
                                    var count = 1
                                    while (destFile.exists()) {
                                        destFile = File(directory, "${mergeName}_$count.pdf")
                                        count++
                                    }
                                    val success = com.example.prowoflowdocscan.util.PdfMergerHelper.mergeImagesToPdf(selectedFiles.toList(), destFile)
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Merged successfully", android.widget.Toast.LENGTH_SHORT).show()
                                        selectedFiles = emptySet()
                                        viewModel.triggerRefresh()
                                    } else {
                                        android.widget.Toast.makeText(context, "Merge failed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                showMergePrompt = false
                                isMerging = false
                            }
                        }) {
                            Text("Merge")
                        }
                    }
                },
                dismissButton = {
                    if (!isMerging) {
                        TextButton(onClick = { showMergePrompt = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }

        if (showFabMenu) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showFabMenu = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Add Document", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showFabMenu = false
                            scanner.getStartScanIntent(context as Activity).addOnSuccessListener { intentSender ->
                                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan with Camera")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            showFabMenu = false
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import from Files")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentItem(
    document: Document,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameClick: () -> Unit,
    repository: DocumentRepository
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .aspectRatio(0.75f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (document.name.endsWith(".pdf", ignoreCase = true)) {
                    var pdfThumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(document) {
                        pdfThumbnail = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            repository.getPdfThumbnail(File(document.path))
                        }
                    }
                    if (pdfThumbnail != null) {
                        AsyncImage(
                            model = pdfThumbnail,
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                        }
                    }
                } else {
                    AsyncImage(
                        model = document.path,
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentScale = ContentScale.Crop
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = document.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRenameClick,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                )
            }
        }
    }
}
