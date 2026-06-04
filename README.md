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

## What's New

- **Glassmorphism UI** — Edge-to-edge Jetpack Compose interface with translucent glass cards, spring-physics transitions, and smooth micro-animations.
- **Custom Categories & Live Updates** — Create and reorder personal library folders. Background sync notifies you the moment new chapters drop.
- **3 Novel Detail Screen Layouts** — Switch between three premium landing page designs with dynamic cover-matched color palettes.
- **AMOLED / Light / Material You Themes** — True pitch-black AMOLED mode, a clean light theme, and full Monet dynamic coloring on Android 12+.
- **Custom Backgrounds & Fonts** — Set any wallpaper behind your library shelf with blur/dim controls and many Trendy fonts in the app with scaling option.
- **Decentralized Plugin System** — Providers are modular, sandboxed plugins downloaded separately. Update, repair, or expand sources without touching the app. Join my [telegram](https://t.me/+i9MSwgeoXzU0NTE1) to get the latest provider apk.
- **Reading Stats & For You Feed** — Detailed reading history metrics and a locally computed recommendation dashboard.
- **Pinterest Bento Grid** — Masonry layout with GPU-accelerated 3D tilt effects on cover art.
- **Vibe & Aura Settings** — Global character rename dictionary, visual contrast overlays, and advanced tuning toggles.
-  and many more features , try them in app.

---

## Screenshots


| <img src="./screenshots/01_library.png" width="180"/> | <img src="./screenshots/02_source_explorer.png" width="180"/> | <img src="./screenshots/03_novel_detail.png" width="180"/> | <img src="./screenshots/04_reader.png" width="180"/> | <img src="./screenshots/05_settings.png" width="180"/> |
|:---:|:---:|:---:|:---:|:---:|
| **Library** | **Source Explorer** | **Novel Detail** | **Reader** | **Settings** |

| <img src="./screenshots/06_vibe_aura.png" width="180"/> | <img src="./screenshots/07_search.png" width="180"/> | <img src="./screenshots/08_updates.png" width="180"/> | <img src="./screenshots/09_history.png" width="180"/> | <img src="./screenshots/10_providers.png" width="180"/> |
|:---:|:---:|:---:|:---:|:---:|
| **Vibe & Aura** | **Search** | **Updates** | **History** | **Providers** |

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
