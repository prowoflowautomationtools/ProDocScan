package com.example.prowoflowdocscan.util

import kotlinx.serialization.Serializable

@Serializable
data class OcrHighlight(
    val pageIndex: Int,
    val text: String,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float
)
