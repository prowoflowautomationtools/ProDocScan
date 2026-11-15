package com.example.prowoflowdocscan.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.FileInputStream
import com.example.prowoflowdocscan.util.OcrHighlight
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.Types

enum class SaveFormat { PDF, IMAGE, BOTH }
enum class PageSize { ORIGINAL, A4, US_LETTER }

data class Document(val id: String, val name: String, val path: String, val dateModified: Long)

class DocumentRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("searchable_docs", Context.MODE_PRIVATE)

    fun markAsSearchable(id: String) {
        prefs.edit().putBoolean(id, true).apply()
    }

    fun isSearchable(id: String): Boolean {
        return prefs.getBoolean(id, false)
    }

    suspend fun importPdf(context: Context, uri: Uri): Document? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var filename: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    filename = cursor.getString(index)
                }
            }
        }

        val finalFilename = if (filename == null) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            "imported_$timestamp.pdf"
        } else if (!filename!!.lowercase().endsWith(".pdf")) {
            "$filename.pdf"
        } else {
            filename!!
        }

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return@withContext null
        var destinationFile = File(directory, finalFilename)
        
        var counter = 1
        val baseName = finalFilename.substringBeforeLast(".")
        while (destinationFile.exists()) {
            destinationFile = File(directory, "${baseName}_$counter.pdf")
            counter++
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext Document(
                id = destinationFile.name,
                name = destinationFile.name,
                path = destinationFile.absolutePath,
                dateModified = destinationFile.lastModified()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    fun saveOcrHighlights(documentId: String, highlights: List<OcrHighlight>) {
        try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(List::class.java, OcrHighlight::class.java)
            val adapter = moshi.adapter<List<OcrHighlight>>(type)
            val json = adapter.toJson(highlights)
            val file = File(context.filesDir, "${documentId}_ocr_bounds.json")
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getOcrHighlights(documentId: String): List<OcrHighlight> {
        return try {
            val file = File(context.filesDir, "${documentId}_ocr_bounds.json")
            if (file.exists()) {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val type = Types.newParameterizedType(List::class.java, OcrHighlight::class.java)
                val adapter = moshi.adapter<List<OcrHighlight>>(type)
                val json = file.readText()
                adapter.fromJson(json) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getDocuments(): List<Document> {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (directory == null || !directory.exists()) {
            return emptyList()
        }
        val files = directory.listFiles() ?: return emptyList()
        return files.filter { it.isFile && (it.extension.lowercase() == "pdf" || it.extension.lowercase() == "jpg" || it.extension.lowercase() == "jpeg" || it.extension.lowercase() == "png") }
            .map { file ->
                Document(
                    id = file.name,
                    name = file.name,
                    path = file.absolutePath,
                    dateModified = file.lastModified()
                )
            }.sortedByDescending { it.dateModified }
    }

    fun saveDocument(result: GmsDocumentScanningResult, format: SaveFormat, customName: String, pageSize: PageSize) {
        if (format == SaveFormat.IMAGE || format == SaveFormat.BOTH) {
            result.pages?.let { pages ->
                var pageIndex = 1
                for (page in pages) {
                    val name = if (pages.size > 1) "${customName}_$pageIndex" else customName
                    saveFile(page.imageUri, "jpg", name, pageSize)
                    pageIndex++
                }
            }
        }
        if (format == SaveFormat.PDF || format == SaveFormat.BOTH) {
            if (pageSize == PageSize.ORIGINAL) {
                result.pdf?.let { pdf ->
                    saveFile(pdf.uri, "pdf", customName, pageSize)
                }
            } else {
                result.pages?.let { pages ->
                    generateCustomPdf(pages.map { it.imageUri }, customName, pageSize)
                }
            }
        }
    }

    private fun generateCustomPdf(imageUris: List<Uri>, customName: String, pageSize: PageSize): Document? {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        val fileName = "$customName.pdf"
        var destinationFile = File(directory, fileName)
        
        var counter = 1
        while (destinationFile.exists()) {
            destinationFile = File(directory, "${customName}_$counter.pdf")
            counter++
        }

        val (pageW, pageH) = when (pageSize) {
            PageSize.A4 -> 595 to 842
            PageSize.US_LETTER -> 612 to 792
            else -> 595 to 842
        }

        try {
            val pdfDocument = PdfDocument()
            for ((index, uri) in imageUris.withIndex()) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        
                        val scale = minOf(pageInfo.pageWidth.toFloat() / bitmap.width, pageInfo.pageHeight.toFloat() / bitmap.height)
                        val scaledWidth = bitmap.width * scale
                        val scaledHeight = bitmap.height * scale
                        val dx = (pageInfo.pageWidth - scaledWidth) / 2f
                        val dy = (pageInfo.pageHeight - scaledHeight) / 2f

                        val matrix = android.graphics.Matrix()
                        matrix.postScale(scale, scale)
                        matrix.postTranslate(dx, dy)
                        
                        val paint = Paint().apply { isFilterBitmap = true }
                        page.canvas.drawBitmap(bitmap, matrix, paint)
                        
                        pdfDocument.finishPage(page)
                        bitmap.recycle()
                    }
                }
            }
            
            FileOutputStream(destinationFile).use { output ->
                pdfDocument.writeTo(output)
            }
            pdfDocument.close()
            
            return Document(
                id = destinationFile.name,
                name = destinationFile.name,
                path = destinationFile.absolutePath,
                dateModified = destinationFile.lastModified()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun saveFile(uri: Uri, extension: String, customName: String, pageSize: PageSize): Document? {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        val fileName = "$customName.$extension"
        var destinationFile = File(directory, fileName)
        
        // Handle name collision
        var counter = 1
        while (destinationFile.exists()) {
            destinationFile = File(directory, "${customName}_$counter.$extension")
            counter++
        }

        try {
            if (pageSize == PageSize.ORIGINAL) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // Resize logic for images
                val (targetWidth, targetHeight) = when (pageSize) {
                    PageSize.A4 -> 595f to 842f
                    PageSize.US_LETTER -> 612f to 792f
                    else -> 595f to 842f // Fallback
                }
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val originalBitmap = BitmapFactory.decodeStream(input)
                    if (originalBitmap != null) {
                        val scale = minOf(targetWidth / originalBitmap.width, targetHeight / originalBitmap.height)
                        val newWidth = (originalBitmap.width * scale).toInt()
                        val newHeight = (originalBitmap.height * scale).toInt()
                        
                        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                        FileOutputStream(destinationFile).use { output ->
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                        }
                        if (scaledBitmap != originalBitmap) {
                            scaledBitmap.recycle()
                        }
                        originalBitmap.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        return Document(
            id = destinationFile.name,
            name = destinationFile.name,
            path = destinationFile.absolutePath,
            dateModified = destinationFile.lastModified()
        )
    }

    fun deleteDocument(document: Document): Boolean {
        val file = File(document.path)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    fun deleteDocuments(files: List<File>): Boolean {
        var allDeleted = true
        for (file in files) {
            if (file.exists()) {
                if (!file.delete()) {
                    allDeleted = false
                }
            }
        }
        return allDeleted
    }

    fun renameDocument(document: Document, newName: String): Document? {
        val file = File(document.path)
        if (!file.exists()) return null
        
        var finalName = newName
        if (!finalName.lowercase().endsWith(".pdf") && 
            !finalName.lowercase().endsWith(".jpg") && 
            !finalName.lowercase().endsWith(".jpeg") && 
            !finalName.lowercase().endsWith(".png")) {
            // Append extension
            finalName += ".${file.extension}"
        }

        val newFile = File(file.parentFile, finalName)
        if (newFile.exists()) return null // Or handle name collision

        return if (file.renameTo(newFile)) {
            Document(
                id = newFile.name,
                name = newFile.name,
                path = newFile.absolutePath,
                dateModified = newFile.lastModified()
            )
        } else {
            null
        }
    }

    fun replaceWithSearchablePdf(originalDocument: Document, tempPdfFile: File): Document? {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        val baseName = originalDocument.name.substringBeforeLast(".")
        var pdfFileName = "$baseName.pdf"
        var destinationFile = File(directory, pdfFileName)
        var counter = 1
        
        while (destinationFile.exists()) {
            pdfFileName = "${baseName}_$counter.pdf"
            destinationFile = File(directory, pdfFileName)
            counter++
        }

        try {
            tempPdfFile.copyTo(destinationFile, overwrite = true)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        return Document(
            id = destinationFile.name,
            name = destinationFile.name,
            path = destinationFile.absolutePath,
            dateModified = destinationFile.lastModified()
        )
    }

    fun updateDocument(originalDocument: Document, tempFile: File): Document? {
        val originalFile = File(originalDocument.path)
        try {
            tempFile.copyTo(originalFile, overwrite = true)
            return Document(
                id = originalFile.name,
                name = originalFile.name,
                path = originalFile.absolutePath,
                dateModified = originalFile.lastModified()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun updateDocumentImage(document: Document, bitmap: android.graphics.Bitmap): Document? {
        val file = File(document.path)
        if (file.extension.equals("pdf", ignoreCase = true)) {
            return null // Modifying PDF directly is not supported in this scope
        }
        try {
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            return Document(
                id = file.name,
                name = file.name,
                path = file.absolutePath,
                dateModified = file.lastModified()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun getPdfThumbnail(file: File): android.graphics.Bitmap? {
        if (!file.exists() || !file.name.endsWith(".pdf", ignoreCase = true)) return null
        
        val thumbnailFile = File(context.cacheDir, "thumb_${file.name}.jpg")
        if (thumbnailFile.exists()) {
            return android.graphics.BitmapFactory.decodeFile(thumbnailFile.absolutePath)
        }

        return try {
            val fileDescriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
            
            if (pdfRenderer.pageCount > 0) {
                val page = pdfRenderer.openPage(0)
                val width = 400
                val height = (width.toFloat() / page.width * page.height).toInt()
                
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                page.close()
                pdfRenderer.close()
                fileDescriptor.close()
                
                // Cache it
                val out = FileOutputStream(thumbnailFile)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                out.close()
                
                bitmap
            } else {
                pdfRenderer.close()
                fileDescriptor.close()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
