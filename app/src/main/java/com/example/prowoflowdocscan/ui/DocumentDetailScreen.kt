package com.example.prowoflowdocscan.ui

import android.content.Intent
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.prowoflowdocscan.DocumentDetail
import com.example.prowoflowdocscan.data.Document
import com.example.prowoflowdocscan.util.ImageAdjustmentHelper
import com.example.prowoflowdocscan.util.OcrHighlight
import com.example.prowoflowdocscan.util.SearchablePdfGenerator
import com.example.prowoflowdocscan.util.TextRecognitionHelper
import com.example.prowoflowdocscan.viewmodel.DocumentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    detail: DocumentDetail,
    viewModel: DocumentViewModel,
    onDocumentDeleted: () -> Unit,
    onDocumentRenamed: (String, String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val repository = viewModel.repository
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var imageTimestamp by remember { mutableStateOf(File(detail.path).lastModified()) }

    LaunchedEffect(detail) {
        viewModel.checkIfSearchable(detail.documentId)
        viewModel.loadOcrHighlights(detail.documentId)
    }

    val isSearchable by viewModel.isSearchable.collectAsState()
    var newSearchablePdf by remember { mutableStateOf<Document?>(null) }

    var showAdjustBottomSheet by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var originalBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(brightness, contrast, originalBitmap) {
        if (originalBitmap != null) {
            withContext(Dispatchers.IO) {
                val bmp = ImageAdjustmentHelper.adjustBitmap(originalBitmap!!, brightness, contrast)
                previewBitmap = bmp
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var extractedText by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    var isSearching by remember { mutableStateOf(false) }
    val searchQuery by viewModel.detailSearchQuery.collectAsState()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsState()

    val allOcrHighlights by viewModel.allOcrHighlights.collectAsState()
    val searchHighlightRects by viewModel.searchHighlightRects.collectAsState()

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Black,
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .blur(16.dp)
                )
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        if (isSearching) {
                            val focusRequester = remember { FocusRequester() }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    viewModel.setDetailSearchQuery(it)
                                },
                                placeholder = { Text("Search text...") },
                                modifier = Modifier.fillMaxWidth().height(50.dp).focusRequester(focusRequester),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                    keyboardController?.hide()
                                    viewModel.setCurrentSearchIndex(0)
                                })
                            )
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            Text(
                                text = detail.documentId,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (isSearching) {
                            if (searchHighlightRects.isNotEmpty()) {
                                Text("${currentSearchIndex + 1}/${searchHighlightRects.size}", modifier = Modifier.padding(end = 8.dp))
                                IconButton(onClick = {
                                    if (searchHighlightRects.isNotEmpty()) {
                                        viewModel.setCurrentSearchIndex(if (currentSearchIndex - 1 < 0) searchHighlightRects.size - 1 else currentSearchIndex - 1)
                                    }
                                }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
                                }
                                IconButton(onClick = {
                                    if (searchHighlightRects.isNotEmpty()) {
                                        viewModel.setCurrentSearchIndex((currentSearchIndex + 1) % searchHighlightRects.size)
                                    }
                                }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                                }
                            }
                            IconButton(onClick = {
                                isSearching = false
                                viewModel.setDetailSearchQuery("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                            }
                        } else {
                            var showMoreMenu by remember { mutableStateOf(false) }

                            if (isSearchable) {
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search PDF")
                                }
                            }

                            IconButton(onClick = {
                                val file = File(detail.path)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (detail.path.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Document"))
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }

                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                if (!detail.documentId.endsWith(".pdf", ignoreCase = true)) {
                                    DropdownMenuItem(
                                        text = { Text("Adjust Brightness/Contrast") },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            coroutineScope.launch {
                                                isExtracting = true
                                                val file = File(detail.path)
                                                originalBitmap = withContext(Dispatchers.IO) {
                                                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                                }
                                                previewBitmap = originalBitmap
                                                brightness = 0f
                                                contrast = 1f
                                                showAdjustBottomSheet = true
                                                isExtracting = false
                                            }
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Make Searchable PDF") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        isExtracting = true
                                        coroutineScope.launch {
                                            val originalFile = File(detail.path)
                                            val result = SearchablePdfGenerator.generate(context, originalFile)
                                            if (result != null) {
                                                val (newPdf, highlights) = result
                                                val newDoc = Document(
                                                    id = newPdf.name,
                                                    name = newPdf.name,
                                                    path = newPdf.absolutePath,
                                                    dateModified = newPdf.lastModified()
                                                )
                                                repository.markAsSearchable(newDoc.id)
                                                repository.saveOcrHighlights(newDoc.id, highlights)

                                                if (detail.documentId.endsWith(".pdf", ignoreCase = true)) {
                                                    viewModel.triggerOcrDataRefresh(detail.documentId)
                                                } else {
                                                    newSearchablePdf = newDoc
                                                }
                                            }
                                            isExtracting = false
                                        }
                                    }
                                )
                            DropdownMenuItem(
                                text = { Text("Extract Text (OCR)") },
                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    isExtracting = true
                                    coroutineScope.launch {
                                        val text = TextRecognitionHelper.extractText(File(detail.path))
                                        extractedText = text
                                        isExtracting = false
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            LaunchedEffect(currentSearchIndex, searchHighlightRects) {
                if (isSearching && searchHighlightRects.isNotEmpty() && currentSearchIndex < searchHighlightRects.size) {
                    val highlight = searchHighlightRects[currentSearchIndex]
                    val centerX = (highlight.leftNorm + highlight.rightNorm) / 2f - 0.5f
                    val centerY = (highlight.topNorm + highlight.bottomNorm) / 2f - 0.5f

                    if (scale < 2f) scale = 2f

                    val targetOffsetX = -centerX * containerWidth * scale
                    val targetOffsetY = -centerY * containerHeight * scale

                    androidx.compose.animation.core.Animatable(offset, Offset.VectorConverter).animateTo(
                        Offset(targetOffsetX, targetOffsetY),
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ) {
                        offset = this.value
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale <= 1f) {
                                offset = Offset.Zero
                            } else {
                                val maxX = (containerWidth * (scale - 1)) / 2
                                val maxY = (containerHeight * (scale - 1)) / 2
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                if (detail.documentId.endsWith(".pdf", ignoreCase = true)) {
                    PdfViewer(
                        pdfPath = detail.path,
                        modifier = Modifier.fillMaxSize(),
                        searchHighlights = if (isSearching) searchHighlightRects else emptyList(),
                        allHighlights = allOcrHighlights
                    )
                } else {
                    if (previewBitmap != null && showAdjustBottomSheet) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        key(imageTimestamp) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(detail.path))
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }


        if (newSearchablePdf != null) {
            AlertDialog(
                onDismissRequest = { newSearchablePdf = null },
                title = { Text("Searchable PDF Created") },
                text = { Text("A new searchable PDF has been created. Would you like to open it now?") },
                confirmButton = {
                    TextButton(onClick = {
                        val doc = newSearchablePdf!!
                        onDocumentRenamed(doc.id, doc.path)
                        newSearchablePdf = null
                    }) {
                        Text("Open")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { newSearchablePdf = null }) {
                        Text("Later")
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Document") },
                text = { Text("Are you sure you want to delete this document?") },
                confirmButton = {
                    TextButton(onClick = {
                        val file = File(detail.path)
                        val document = Document(detail.documentId, detail.documentId, detail.path, file.lastModified())
                        repository.deleteDocument(document)
                        showDeleteDialog = false
                        viewModel.triggerRefresh()
                        onDocumentDeleted()
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRenameDialog) {
            var newName by remember(detail.documentId) { mutableStateOf(detail.documentId.substringBeforeLast(".")) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Document") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Document Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val file = File(detail.path)
                        val document = Document(detail.documentId, detail.documentId, detail.path, file.lastModified())
                        val renamedDoc = repository.renameDocument(document, newName)
                        if (renamedDoc != null) {
                            viewModel.triggerRefresh()
                            onDocumentRenamed(renamedDoc.id, renamedDoc.path)
                        }
                        showRenameDialog = false
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAdjustBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showAdjustBottomSheet = false
                    originalBitmap = null
                    previewBitmap = null
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Adjust Image", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Brightness: ${brightness.toInt()}")
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = -255f..255f
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Contrast: ${String.format(Locale.US, "%.2f", contrast)}")
                    Slider(
                        value = contrast,
                        onValueChange = { contrast = it },
                        valueRange = 0f..5f
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isExtracting = true
                                val finalBitmap = withContext(Dispatchers.IO) {
                                    ImageAdjustmentHelper.adjustBitmap(originalBitmap!!, brightness, contrast)
                                }
                                val file = File(detail.path)
                                val doc = Document(detail.documentId, detail.documentId, detail.path, file.lastModified())
                                val updated = repository.updateDocumentImage(doc, finalBitmap)
                                if (updated != null) {
                                    imageTimestamp = System.currentTimeMillis()
                                }
                                showAdjustBottomSheet = false
                                originalBitmap = null
                                previewBitmap = null
                                isExtracting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (extractedText != null) {
            ModalBottomSheet(
                onDismissRequest = { extractedText = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Extracted Text", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = extractedText ?: "",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(extractedText ?: ""))
                            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy to Clipboard")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (isExtracting) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Processing...") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                confirmButton = { }
            )
        }
    }
}

@Composable
fun PdfViewer(
    pdfPath: String,
    modifier: Modifier = Modifier,
    searchHighlights: List<OcrHighlight> = emptyList(),
    allHighlights: List<OcrHighlight> = emptyList()
) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var pdfRenderer by remember { mutableStateOf<android.graphics.pdf.PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<android.os.ParcelFileDescriptor?>(null) }

    DisposableEffect(pdfPath) {
        val file = File(pdfPath)
        try {
            val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            fileDescriptor = fd
            pdfRenderer = renderer
            pageCount = renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    if (pageCount > 0) {
        LazyColumn(modifier = modifier) {
            items(pageCount) { index ->
                var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

                LaunchedEffect(index, pdfRenderer) {
                    withContext(Dispatchers.IO) {
                        try {
                            pdfRenderer?.let { renderer ->
                                synchronized(renderer) {
                                    val page = renderer.openPage(index)
                                    val width = context.resources.displayMetrics.widthPixels
                                    val height = (width.toFloat() / page.width * page.height).toInt()
                                    val bm = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                                    page.render(bm, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    page.close()
                                    bitmap = bm
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                bitmap?.let { b ->
                    SelectionContainer {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                bitmap = b.asImageBitmap(),
                                contentDescription = "PDF Page $index",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )

                            val pageAllHighlights = allHighlights.filter { it.pageIndex == index }
                            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                                val scaleX = constraints.maxWidth.toFloat()
                                val scaleY = constraints.maxHeight.toFloat()
                                val density = LocalDensity.current

                                for (h in pageAllHighlights) {
                                    val top = h.topNorm * scaleY
                                    val left = h.leftNorm * scaleX
                                    val height = (h.bottomNorm - h.topNorm) * scaleY
                                    val width = (h.rightNorm - h.leftNorm) * scaleX

                                    val spSize = with(density) { height.toSp() }

                                    Text(
                                        text = h.text,
                                        color = Color.Transparent,
                                        fontSize = spSize,
                                        modifier = Modifier
                                            .offset(
                                                x = with(density) { left.toDp() },
                                                y = with(density) { top.toDp() }
                                            )
                                            .size(
                                                width = with(density) { width.toDp() },
                                                height = with(density) { height.toDp() }
                                            )
                                    )
                                }
                            }

                            Spacer(
                                modifier = Modifier
                                    .matchParentSize()
                                    .drawBehind {
                                        val pageHighlights = searchHighlights.filter { it.pageIndex == index }
                                        val scaleX = size.width
                                        val scaleY = size.height
                                        for (h in pageHighlights) {
                                            val rect = androidx.compose.ui.geometry.Rect(
                                                left = h.leftNorm * scaleX,
                                                top = h.topNorm * scaleY,
                                                right = h.rightNorm * scaleX,
                                                bottom = h.bottomNorm * scaleY
                                            )
                                            drawRect(
                                                color = Color.Yellow.copy(alpha = 0.5f),
                                                topLeft = Offset(rect.left, rect.top),
                                                size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                                            )
                                        }
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } ?: run {
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
