# ProDocScan — Claude AI Guidelines

This document provides explicit project context, architecture blueprints, and coding rules for Anthropic's Claude (and Claude Code / Claude Desktop Agents).

## 🛠️ Build & Test Commands
- **Assemble Debug APK:** `./gradlew assembleDebug`
- **Run Unit Tests:** `./gradlew testDebugUnitTest`
- **Clean Build:** `./gradlew clean assembleDebug`

## 🏗️ Architecture Overview
ProDocScan is built using pure **Kotlin** and **Jetpack Compose (Material 3 Adaptive)**. It follows clean MVVM architecture:
- `app/src/main/java/com/example/prowoflowdocscan/MainActivity.kt`: Entry point, sets up `DocumentViewModel` and Jetpack Navigation 3 backstack.
- `ui/screens/GalleryScreen.kt`: Main "Scans" gallery displaying document cards, Large TopAppBar, Search bar, Sort toggle, and thumb-accessible dual-action FAB (Scan / Import PDF).
- `ui/screens/DocumentDetailScreen.kt`: Immersion viewer featuring pan/zoom, frosted glass overlays, in-app text search with auto-panning arrows, and native text selection (`SelectionContainer`).
- `ui/screens/SettingsScreen.kt`: Manages `quickSaveEnabled` and `defaultSaveFormat` preferences via `SharedPreferences`.
- `viewmodel/DocumentViewModel.kt`: Central state manager (`StateFlow`) holding documents, search highlights, and OCR bounds.
- `data/DocumentRepository.kt`: Local-only file management (`Environment.DIRECTORY_DOCUMENTS`). Handles copying, renaming, deleting, and caching PDF thumbnails.
- `util/SearchablePdfGenerator.kt`: Converts images/PDFs into Searchable PDFs using ML Kit text bounds and transparent PDF text layers. Overwrites existing PDFs in-place.
- `util/PdfMergerHelper.kt`: Merges multiple images into a single A4/Letter PDF.
- `util/TextRecognitionHelper.kt`: Executes on-device OCR using `com.google.mlkit:text-recognition-devanagari`.
- `util/ImageAdjustmentHelper.kt`: Real-time brightness/contrast manipulation using `ColorMatrix`.

## 🔒 Critical Memory & Safety Rules (DO NOT BREAK)
1. **Bitmap Recycling:** ALWAYS call `bitmap.recycle()` in a `try-finally` block immediately after drawing or processing an image. NEVER leave un-recycled Bitmaps in memory loops.
2. **PdfRenderer Lifecycle:** ALWAYS close `PdfRenderer.Page` and `PdfRenderer` in `finally` or `.use { }` blocks before opening new pages.
3. **Stream Closures:** Wrap all `FileOutputStream` and `FileInputStream` in `.use { }`. Always delete temporary `.tmp` files if processing fails.
4. **On-Device Privacy:** All processing MUST remain 100% on-device. DO NOT add network dependencies or external cloud API calls.
5. **ML Kit Scanner Constraint:** The `GmsDocumentScanner` camera UI is a closed Google Play Services Activity using `SCANNER_MODE_FULL`. DO NOT attempt to inject custom views into the camera feed.

## 🎨 UI Style Rules (Apple HCI)
- **Colors:** Azure Blue (`#3B82F6`) primary accent, Charcoal Dark Grey (`#27272A`) backgrounds, pure white text.
- **Typography:** iOS-style Large Titles (`headlineLarge`) for headers.
- **Shapes:** 24dp rounded squircle corners for cards. Minimum 48dp touch targets for interactive icons.
- **Overlays:** Translucent frosted glass effect using `Modifier.background(...copy(alpha=0.7f)).blur(16.dp)`.
