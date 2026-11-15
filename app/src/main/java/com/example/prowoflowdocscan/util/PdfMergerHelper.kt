package com.example.prowoflowdocscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfMergerHelper {

    suspend fun mergeImagesToPdf(context: Context, imageFiles: List<File>, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        if (imageFiles.isEmpty()) return@withContext false

        val pdfDocument = PdfDocument()

        return@withContext try {
            val pageW = 595
            val pageH = 842 // A4

            for ((index, file) in imageFiles.withIndex()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
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

            FileOutputStream(outputFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pdfDocument.close()
        }
    }
}