# NeoQN — QuickNovel Enhanced

> Ad-free, open-source novel downloader and EPUB reader for Android.

<div align="center">

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7f52ff?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-M3-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Android_5.0+-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![License](https://img.shields.io/badge/GPL_v3-red?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)

<p align="center">
  <a href="https://github.com/Shadyteal2/QuickNovel-Enhanced/releases">
    <img src="https://img.shields.io/badge/Download_Latest_APK-orange?style=for-the-badge&logo=github&logoColor=white" alt="Download Release"/>
  </a>
  <a href="https://t.me/+i9MSwgeoXzU0NTE1">
    <img src="https://img.shields.io/badge/Telegram-0088cc?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram"/>
  </a>
  <a href="https://discord.gg/njMumTKvVw">
    <img src="https://img.shields.io/badge/Discord-7289da?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"/>
  </a>
</p>

</div>

---

NeoQN is a complete overhaul of the original QuickNovel app — rebuilt from the ground up with a modern UI layer, high-performance internals, and deep customization options.

---

## ✨ Features

### 🌟 Power Features — What Makes NeoQN Unique

- **Vibe & Aura Aesthetic Engine** — A one-of-a-kind visual personalisation suite: **Cover Aura Glow** (dynamic color blooms behind novel covers), **Reader Ink Flow** animations, **Card Border Styles**, **Noise Texture overlays**, and contrast adjustment sliders — all tunable in one place.
- **Floating Glassmorphism UI** — Every screen is built in **Jetpack Compose (Material 3)** with translucent glass cards, spring-physics micro-animations, and edge-to-edge layouts. The UI breathes and feels alive.
- **Cinematic Reader Backgrounds** — Set any image as your reader backdrop and transform it with real-time **artistic effects** — Noir, Sepia, Threshold, Edge Detection, Halftone Dots, and more — plus **Blur**, **Grain**, and **Dim** intensity controls.
- **NeoNexus Community Folder** — A built-in community hub for sharing sources, presets, and configuration bundles directly inside the app.
- **NeoShelf — Smart Shelving** — Intelligent shelf organisation that groups, surfaces, and recommends novels based on your personal reading habits.
- **Baseline Profiled Startup** — Pre-compiled ART Baseline Profiles cut **cold-start time by up to 30%** and eliminate jank on first launch — no warm-up period.
- **Decentralized Plugin System** — Providers are sandboxed, independently updatable plugins. Get the latest sources (currently **43 confirmed working providers**) from the [Telegram channel](https://t.me/+i9MSwgeoXzU0NTE1) without ever updating the app.

---

### 🎨 Personalisation & Themes

- **App-Wide Trendy Fonts** — Choose from a curated collection of modern typefaces applied **globally across every screen**, with a scaling slider for perfect comfort.
- **Custom Backgrounds with Artistic Effects** — Apply Noir, Sepia, Threshold, Edge Detection, Dot patterns, and more to any background image, with independent **Blur**, **Grain**, and **Dim** controls.
- **AMOLED / Light / Material You Themes** — True pitch-black OLED mode for battery savings, a crisp light theme, and full **Monet dynamic coloring** that pulls palette from your wallpaper on Android 12+.
- **3 Novel Detail Screen Layouts** — Switch between three premium landing-page designs, each with a dynamic cover-matched color palette.

---

### 📚 Library & Organisation

- **List, Grid & Pinterest Bento Grid** — Three library layouts including a GPU-accelerated **masonry bento grid** with 3D tilt parallax on cover art.
- **Custom Library Categories** — Create, rename, and reorder personal library folders (Reading, Completed, On-Hold, Dropped, or fully custom named).
- **Bulk Actions** — Multi-select novels to **migrate, move, delete, or re-categorise** in a single operation.
- **Novel Updates Section** — A dedicated updates feed showing new chapters across your entire library, sorted and filtered by recency.
- **For You Feed** — A locally computed recommendation dashboard surfacing new novels based on your taste profile — fully private, no account needed.

---

### 📖 Reader & Reading Experience

- **Rich Reader Customisation** — Fine-grained controls: **Text Size, Side Margin, Top Margin, Paragraph Spacing**, custom Reader Fonts, and a **paginated viewer** mode for book-like page turns.
- **Bionic Reading** — Bolds the dominant letters of every word to trigger the brain's natural pattern-recognition and boost reading speed.
- **AutoScroll** — Hands-free continuous scrolling with adjustable speed for a comfortable, no-touch reading session.
- **Reading Progress Bar** — A persistent visual indicator showing exactly how far through a chapter or book you are at a glance.
- **Reading Timer** — Track time spent reading per session and cumulatively per novel — great for streaks and goals.
- **Inbuilt Dictionary** — Tap any word in the reader to get an instant definition without leaving the app or switching context.
- **Reader Themes — Create & Share** — Build custom color themes (background, text, link, highlight colors) and export or share them with the community.
- **Reader Cleaner** — Automatically strips chapter clutter, inline ads, and formatting noise pulled from raw web content.
- **Replace Words / Aliases** — Define global word substitutions or character name aliases that apply silently across all chapters as you read.
- **Notes & Aliases Manager** — Attach personal notes to any passage and manage all annotations and aliases in a single organised panel.
- **Notes on Novel Detail Screen** — Add freeform notes directly on a novel's detail page — thoughts, arc summaries, or reminders for when you return.

---

### 📊 Reading Stats & Insights

- **Detailed Reading History Metrics** — Session duration, words read, chapters finished, daily streaks, and per-novel breakdowns — all computed and stored locally on-device.
- **Smart Recommendations** — On-device engine surfaces novels matching your reading pattern without sending data anywhere.

---

### 🌐 Translation

- **Three Translation Modes** — Choose between **ML Kit on-device** (offline & private), **Google Translate online** (cloud accuracy), or **AI Translation** (context-aware, paragraph-level quality) — all switchable per session inside the reader.

---

### 🔄 Sync & Backup

- **Google Drive Sync** — Back up your entire library, reading progress, and settings to Google Drive and restore on any device instantly.
- **Telegram Sync** — Push backups to a private Telegram bot or channel for instant cross-device access.
- **Local Wi-Fi Sync** — Sync library and reading progress between devices on the same network — no internet or cloud required.
- **Cache Management** — View and selectively clear cached chapters, cover art, and provider data to keep the app's storage footprint under control.

---

### 🛡️ Network & Downloads

- **Automatic Cloudflare Solving** — Built-in JS/Turnstile challenge solver handles protected provider sites automatically so downloads never silently fail.
- **DNS over HTTPS (DoH)** — Routes all provider requests through encrypted DNS resolvers for improved privacy and reliability.
- **Custom Download Configurations** — Per-novel settings: concurrent thread count, retry logic, delay between requests, and precise chapter range selection.
- **Custom Proxy Support** — Route provider traffic through any HTTP/SOCKS5 proxy or VPN endpoint with a single tap.

---

### 🗂️ Import & Export

- **PDF → EPUB Converter** — Import any PDF document and convert it to a clean, reflowable EPUB file for comfortable in-app reading.
- **Custom Poster Sharing** — Generate and share beautifully designed novel poster cards directly from the detail screen.
- **Multi-Migration & Bulk Tools** — Batch migrate library entries between sources, bulk-delete, or export the full library as a structured backup file.

---

## Screenshots


| <img src="screenshots/library 1-4.webp" width="180"/> | <img src="screenshots/librayr light-5.webp" width="180"/> | <img src="screenshots/custom bg-3.webp" width="180"/> | <img src="screenshots/bento pinterest-1.webp" width="180"/> | <img src="screenshots/providers 2-10.webp" width="180"/> |
|:---:|:---:|:---:|:---:|:---:|
| **Library** | **Light Theme** | **Custom BG** | **Bento Grid** | **Providers** |

| <img src="screenshots/provider library-9.webp" width="180"/> | <img src="screenshots/recommendation choice 3-13.webp" width="180"/> | <img src="screenshots/recommendation 4-12.webp" width="180"/> | <img src="screenshots/Novel detail screen-7.webp" width="180"/> | <img src="screenshots/novel detail scren 2-8.webp" width="180"/> |
|:---:|:---:|:---:|:---:|:---:|
| **Provider Library** | **Recommendation** | **For You** | **Novel detail screen** | **Novel detail screen** |

| <img src="screenshots/reading stats -11.webp" width="180"/> | <img src="screenshots/settings -16.webp" width="180"/> | <img src="screenshots/cool features-2.webp" width="180"/> | <img src="screenshots/set 1-14.webp" width="180"/> | <img src="screenshots/set 2-15.webp" width="180"/> |
|:---:|:---:|:---:|:---:|:---:|
| **Reading Stats** | **Settings** | **Cool Features** | **Appearance 1** | **Appearance 2** |

---

## Performance

- **Baseline Profiles** — Pre-compiled startup and scroll hot paths, reducing cold start latency by up to 30%.
- **Off-thread Library Sorting** — Handles 10,000+ items on a background thread with 500ms UI rate-limiting to stay jank-free.
- **GPU-Accelerated Image Loading** — Coil 3 with hardware bitmaps and prefetching for zero-stutter scrolling.
- **Multi-ABI Builds** — Platform-specific APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`.

---

## Links

- **Original Project:** [LagradOst/QuickNovel](https://github.com/LagradOst/QuickNovel)
- **Telegram:** [Join the community , latest Beta Release and Providers APK](https://t.me/+i9MSwgeoXzU0NTE1)
- **Discord:** [Join the server](https://discord.gg/njMumTKvVw)
- **Releases:** [Get the latest APK](https://github.com/Shadyteal2/QuickNovel-Enhanced/releases)

---

## Extensible Plugin Ecosystem

NeoQN does not bundle, host, or pre-configure any copyrighted novel sources. It provides a sandboxed **External Plugin System** allowing users to load their own self-hosted scrapers, personal web archives, or custom third-party extensions.
(Will provide more detail on how to do it later) 👌
---

## Legal Disclaimer & Notice

NeoQN is a generic, open-source e-book client, browser utility, and offline EPUB reader.

- **No Content Hosting** — NeoQN does not host, stream, pre-package, or manage any digital files, media, or books. All content is sourced dynamically from external locations provided by the end-user.
- **User Responsibility** — The user is solely responsible for ensuring any extensions or sources they install comply with local copyright laws and applicable terms of service.
- **Copyright Inquiries** — Any takedown requests regarding content accessed through user-defined extensions should be directed to the third-party hosts where that content resides. NeoQN has no affiliation with or control over user-loaded sources.

Use NeoQN at your own discretion and risk.

> [!IMPORTANT]
> If you have concerns about this repository or any user-generated plugin, please reach out directly to the owner on **[Telegram](https://t.me/+i9MSwgeoXzU0NTE1)** or **[Discord](https://discord.gg/njMumTKvVw)** before filing any formal notice. Issues are resolved quickly and amicably.
