package com.example.prowoflowdocscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SearchablePdfGenerator {

    suspend fun generate(context: Context, imageFile: File): Pair<File, List<OcrHighlight>>? = withContext(Dispatchers.IO) {
        if (!imageFile.exists()) return@withContext null

        return@withContext try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val pdfDocument = PdfDocument()

            try {
                // Prepare paint for transparent text
                val textPaint = Paint().apply {
                    color = Color.TRANSPARENT
                }

                val ocrHighlights = mutableListOf<OcrHighlight>()

                if (imageFile.extension.equals("pdf", ignoreCase = true)) {
                    ParcelFileDescriptor.open(imageFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fileDescriptor ->
                        PdfRenderer(fileDescriptor).use { pdfRenderer ->
                            val pageCount = pdfRenderer.pageCount
                            for (i in 0 until pageCount) {
                                val pdfPage = pdfRenderer.openPage(i)
                                try {
                                    val bitmap = Bitmap.createBitmap(pdfPage.width * 2, pdfPage.height * 2, Bitmap.Config.ARGB_8888)
                                    try {
                                        bitmap.eraseColor(Color.WHITE)
                                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                        val image = InputImage.fromBitmap(bitmap, 0)
                                        val text = recognizer.process(image).await()
                                        
                                        extractHighlights(text, i, bitmap.width, bitmap.height, ocrHighlights)

                                        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                                        val page = pdfDocument.startPage(pageInfo)
                                        try {
                                            val canvas = page.canvas
                                            canvas.drawBitmap(bitmap, 0f, 0f, null)
                                            drawTextBlocks(text, canvas, textPaint)
                                        } finally {
                                            pdfDocument.finishPage(page)
                                        }
                                    } finally {
                                        bitmap.recycle()
                                    }
                                } finally {
                                    pdfPage.close()
                                }
                            }
                        }
                    }
                } else {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return@withContext null
                    try {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val text = recognizer.process(image).await()

                        extractHighlights(text, 0, bitmap.width, bitmap.height, ocrHighlights)

                        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        try {
                            val canvas = page.canvas
                            canvas.drawBitmap(bitmap, 0f, 0f, null)
                            drawTextBlocks(text, canvas, textPaint)
                        } finally {
                            pdfDocument.finishPage(page)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                val tempFile = File.createTempFile("searchable_", ".pdf", context.cacheDir)
                try {
                    FileOutputStream(tempFile).use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }

                    val outputFile = if (imageFile.extension.equals("pdf", ignoreCase = true)) {
                        imageFile
                    } else {
                        var destFile = File(imageFile.parent, "${imageFile.nameWithoutExtension}.pdf")
                        var counter = 1
                        while (destFile.exists()) {
                            destFile = File(imageFile.parent, "${imageFile.nameWithoutExtension}_$counter.pdf")
                            counter++
                        }
                        destFile
                    }
                    tempFile.copyTo(outputFile, overwrite = true)
                    Pair(outputFile, ocrHighlights)
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            } finally {
                pdfDocument.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractHighlights(text: Text, pageIndex: Int, width: Int, height: Int, outList: MutableList<OcrHighlight>) {
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val boundingBox = line.boundingBox
                if (boundingBox != null) {
                    outList.add(
                        OcrHighlight(
                            pageIndex = pageIndex,
                            text = line.text,
                            leftNorm = boundingBox.left.toFloat() / width,
                            topNorm = boundingBox.top.toFloat() / height,
                            rightNorm = boundingBox.right.toFloat() / width,
                            bottomNorm = boundingBox.bottom.toFloat() / height
                        )
                    )
                }
            }
        }
    }

    private fun drawTextBlocks(text: Text, canvas: Canvas, textPaint: Paint) {
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val boundingBox = line.boundingBox
                if (boundingBox != null) {
                    textPaint.textSize = boundingBox.height().toFloat()
                    canvas.drawText(
                        line.text,
                        boundingBox.left.toFloat(),
                        boundingBox.bottom.toFloat(),
                        textPaint
                    )
                }
            }
        }
    }
}
