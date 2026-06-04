# NeoQN v2.1.5

> Get the latest **provider APK**, betas, and early access — join the **[Telegram channel](https://t.me/+i9MSwgeoXzU0NTE1)**.

---

This is a major release. Covers a full Compose UI migration, a rewritten download engine, deep backend changes, and a large wave of quality-of-life additions across the app.

---

### New Features

**UI & Design**
- Full Jetpack Compose migration across all major app screens
- Completely revamped Novel Detail screens — new layouts plus a redesigned original
- Flexible dynamic grid viewports across Library tabs
- Smooth shimmer skeleton loading states across all screens
- Tactile high-precision haptic ruler sliders in Settings
- Color-graded download status badges — Green (Downloaded), Yellow (Downloading), Red (Stopped)
- Streamlined theme selection: AMOLED, Light (Flashbang), and Monet only — Dark and Gray removed
- Library entry animations and a minimal sort FAB
- Global search bar redesigned with a glassmorphic style

**Customization (Vibe & Aura)**
- Accent gradient — blend two accent colors across the UI
- Eco Mode — reduces rendering load for smoother performance on lower-end devices
- Custom Font & Scale Manager — dynamic font loading with high-performance rendering
- Additional customization options throughout the Vibe & Aura panel

**Providers & Plugins**
- Replaced the sync provider FAB with a direct **Import APK** button in the Providers tab
- Hot reload plugin functionality — providers reload in the backend without requiring a restart

**Download Engine**
- Increased batch download quantity for faster overall download speeds
- Failsafe download queue with improved retry logic and network restoration handling
- Intelligent concurrency controls and backoff algorithms for resource management

**Backend & Data**
- Reactive Room SQLite database as the library single source of truth, backed by Kotlin Flows
- GPU-accelerated Coil 3 image loading with smart local cover caching
- Enhanced CloudflareKiller — better false-positive prevention and background task handling
- Improved backup system with full custom category preservation
- Safe post-restore app rebuild to prevent preference desync
- Optimized ML-Kit Translation engine — no repeated model downloads, better state restoration

---

### Fixes

- Fixed repeated/duplicate options appearing in Settings
- Restored the missing Languages option in Settings
- Fixed non-functional chapter index toolbar and sort FAB
- Fixed a navigation list layout style issue
- Fixed a crash in the global search bar
- Fixed novel download failures and improved overall download reliability
- Fixed reading stats tracking mechanics
- Resolved a significant memory leak in the reader

---

### Getting providers

Providers ship separately from the app. Grab the latest provider APK from the **[Telegram channel](https://t.me/+i9MSwgeoXzU0NTE1)**, or sideload any `.apk` directly using the new **Import APK** button in the Providers tab.

---

[Telegram](https://t.me/+i9MSwgeoXzU0NTE1) · [Discord](https://discord.gg/njMumTKvVw) · [All releases](https://github.com/Shadyteal2/QuickNovel-Enhanced/releases)
