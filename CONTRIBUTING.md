# Contributing to ProDocScan 📄⚡

Thank you for your interest in contributing to **ProDocScan**! We welcome contributions from developers, designers, financial professionals, and non-technical vibe-coders alike.

## 🤖 AI-Assisted Vibe Coding
ProDocScan is explicitly configured for AI-assisted development. Before submitting a Pull Request (PR) or suggesting code:
- Please review `CLAUDE.md`, `GEMINI.md`, or `AGENTS.md` in the project root.
- Ensure all Bitmap processing loops explicitly call `bitmap.recycle()` in `try-finally` blocks to prevent memory leaks.
- Ensure 100% on-device privacy is maintained (no external network dependencies for core features).

## 🛠️ How to Submit a Pull Request
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/AmazingFeature`).
3. Build and test locally: `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`.
4. Commit your changes (`git commit -m 'feat: Add some AmazingFeature'`).
5. Push to the branch (`git push origin feature/AmazingFeature`).
6. Open a Pull Request.

## 🐛 Reporting Bugs & Feature Ideas
Please use our custom [GitHub Issue Templates](.github/ISSUE_TEMPLATE/) to report bugs or submit plain-English AI feature prompts!

<!-- release update -->
