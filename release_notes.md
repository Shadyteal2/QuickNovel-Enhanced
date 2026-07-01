# NeoQN v2.2.0 (Stable)

> Get the latest **provider APK**, betas, and early access — join the **[Telegram channel](https://t.me/+i9MSwgeoXzU0NTE1)**.

---

This is a major stable release containing a full Compose UI migration, advanced reader customizations, robust backup/sync solutions, translation integrations, and a huge wave of performance optimizations.

---

### New Features

📅 **Reader & Customization**
- **Advanced Reader Customization:** Added custom theme creation & sharing, a distraction-free "Cleaner Mode" for power users, and integrated character alias options.
- **Paginated Reader & Auto-Scroll:** Enjoy a fluid, native paginated page-turning mode or use the new auto-scroll feature.
- **Reading Timer:** Track your reading sessions with a native timer located inside the reader settings.
- **Improved Typography & Comfort:** Preloaded custom fonts to eliminate font flickering on chapter open, added text auto-visibility adjustments on bright custom backgrounds, and introduced glass cards in theme selection.
- **Bionic Reading:** Repaired and improved the bionic reading styling to improve text comprehension and reading speed.

📁 **Library, History & Navigation**
- **NeoShelf:** Introduced NeoShelf—a dedicated space for organizing your library categories.
- **NeoNexus (Shared Lists):** Create, export, and import curated book lists as `.neolist` files (up to 200 books per folder) with automatic version checks and conflict resolution. [Check the NeoNexus Guide](https://t.me/neoqnnovelreader/3356).
- **Multi-Select Actions:** Select multiple bookmarked novels to move or delete them in batches.
- **Bulk Migration:** Migrate your library books to different provider sources in bulk.
- **Favorite & Priority Pinning:** Pin favorite novels to the top of your categories, or pin specific downloads to prioritize them in the queue.
- **Enhanced History & Search:** Access your global search history quickly, and view your recent reading progress via a new history carousel.
- **Custom Posters:** Generate and share beautiful custom poster cards of your favorite novel covers.

☁️ **Sync, Backup & Network**
- **Local Wi-Fi Sync:** Directly sync your novel library with other devices on your local Wi-Fi network.
- **Google Drive Sync:** Seamlessly back up and restore your settings and library using Google Drive.
- **Granular Backups:** Choose exactly which folders, categories, or settings to sync or backup.
- **Telegram Cloud Backups:** Back up your downloaded books directly to a Telegram bot, with options to compile downloads into single backup archives.
- **DNS over HTTPS (DoH):** Enable secure DNS routing to bypass ISP blocks and protect connection privacy.
- **Custom Rate Limits & Proxies:** Configure parallel download concurrency, custom rate limits, and custom proxy connections.
- **PDF to EPUB Converter:** Convert local PDF documents into standard, readable EPUB files.
- **Select & Download Chapters:** Long-press any chapter in the index to download specific chapters directly.
- **Storage/Cache Management:** Added proper storage cache management for users to remove accordingly to help keep the app's footprint small.

🌐 **Translation & Extension Integrations**
- **Advanced Translation Engines:** Integrated Google Online Translation, Yandex Translation, and custom AI API translation engines (bring-your-own-key) directly into the reader.
- **WebToEpub Integration:** Integrated webtoepub novel downloader extension in the global search bar of app , try putting some novels link supported by the extension.

✨ **UI & UX Polish**
- **Material 3 Carousels:** Upgraded "For You" and "Global Search" tabs with smooth, parallax-scrolling M3 carousels and an optimized recommendation engine and options.
- **Vibe & Aura Upgrades:** Added physics-based spring animations, cover-based dynamic color harmonization, and nested, cleaner settings sub-menus.
- **Pull-to-Refresh:** Simply swipe down to instantly refresh lists on the Main Page and Library screens.
- **Premium Haptics:** Satisfying tactile vibration feedback when bookmarking a novel or toggling settings.
- **Modernized Indicators:** Replaced legacy progress indicators with clean Material 3 designs.

---

### Performance & Under-the-Hood

- **Reduced App Size:** Stripped out unused RxJava dependencies, shaving off over 300 KB from the final installation file.
- **Faster Startup Times:** Unified duplicate Jackson JSON parsers into a single shared instance to minimize reflection overhead.
- **Buttery-Smooth Scrolling:** Optimized database queries and item layouts to eliminate lag when scrolling through massive libraries.
- **Instant Chapter Sorting:** Re-engineered chapter sorting algorithms to sort thousands of chapters instantly without freezing the UI.
- **HTTP/2 Network Optimization:** Connections are now kept open (single long-lasting socket) instead of re-establishing for every request, speeding up chapter loads and searches.
- **Smart Battery Saver:** Automatically pauses heavy background image, text-rendering, and network tasks when you aren't active in the reader.
- **Privacy-Safe Log Exporter:** Replaced the basic logcat reader with a secure "Share App Logs" and "Crash app logs" utility.
- **Optimized Provider Imports:** Removed unnecessary signature verification to speed up provider APK imports.

---

### Bug Fixes

- **TTS Progress Recovery:** Fixed an issue where reopening the app or resuming the reader reset the Text-to-Speech position to the first paragraph.
- **Background Downloads:** Fixed downloads stopping when the screen is turned off or when running in the background.
- **EPUB Deletion:** Fixed a bug that prevented imported EPUB files from being properly deleted from storage.
- **UI Layout Corrections:** Resolved text clipping on long novel titles, keyboard overlapping proxy detail inputs, and the provider import FAB leaking into the global search screen.
- **Settings Sync:** Fixed settings and UI toggles not instantly updating upon changing a preference.
- **Dialog Transparency:** Resolved overlapping text and transparency issues in reader settings dialogs.

---

### Getting providers

Providers ship separately from the app. Grab the latest provider APK from the **[Telegram channel](https://t.me/neoqnnovelreader)**, and tap "+" fab in providers tab to import it.

---

[Telegram](https://t.me/neoqnnovelreader) · [Discord](https://discord.gg/njMumTKvVw) · [All releases](https://github.com/Shadyteal2/QuickNovel-Enhanced/releases)
