package com.example.prowoflowdocscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

object TextRecognitionHelper {

    suspend fun extractText(context: Context, file: File): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            if (file.extension.equals("pdf", ignoreCase = true)) {
                // Handle PDF: Extract text from all pages
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val stringBuilder = StringBuilder()

                for (i in 0 until pdfRenderer.pageCount) {
                    val page = pdfRenderer.openPage(i)
                    // Render page to bitmap
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)

                    // PDF pages might have transparent background, so fill with white
                    bitmap.eraseColor(android.graphics.Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val image = InputImage.fromBitmap(bitmap, 0)
                    val text = recognizer.process(image).await()
                    stringBuilder.append(text.text).append("\n\n")

                    // Free memory
                    bitmap.recycle()
                }

                pdfRenderer.close()
                fileDescriptor.close()
                stringBuilder.toString().trim()
            } else {
                // Handle Image
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val text = recognizer.process(image).await()
                    bitmap.recycle()
                    text.text
                } else {
                    "Error: Could not decode image file."
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error extracting text: ${e.message}"
        }
    }
}