# ProDocScan — Google Gemini AI Guidelines

Context manual for Google Gemini, Gemini Code Assist, and Google Cloud Vertex AI agents.

## 🚀 Key Frameworks & APIs
- **Language:** Kotlin 1.9+
- **UI:** Jetpack Compose (Material 3 Adaptive, Navigation 3)
- **Machine Learning:** Google ML Kit Document Scanner (`SCANNER_MODE_FULL`) & ML Kit Text Recognition (`text-recognition-devanagari`).
- **Asynchronous:** Kotlin Coroutines & `StateFlow`.

## 📦 Build Command
- `./gradlew assembleDebug`

## 💡 Gemini Specific Rules
- Respect the on-device ML Kit architecture. Do not attempt to modify Google's native scanner UI.
- Use Compose state hoisting. Ensure `DocumentViewModel` handles state mutation rather than composable functions.
- Ensure all PDF generation and text selection overlays maintain exact math alignment when zoom `scale` changes.
