# Walkthrough of Provider Modernizations

This document details the features, enhancements, and domain updates safely integrated from `toadd/` into the production provider files. All code-level optimizations (lazy-loaded localized Jackson mappers, parallel page scraping, Jsoup cached `.document` retrieval, and rotated User-Agents) have been fully preserved.

---

## Changes Implemented

### 1. **`FenrirRealmProvider.kt`**
- Migrated API endpoints to version 2: `/api/new/v2/series`.
- Implemented `loadReviews` fetching commenter data.
- Implemented recommendations in `getRelated`.
- Kept our private localized Jackson mapper.

### 2. **`HiraethTranslationProvider.kt`**
- Added DOM-based comment/review parsing.
- Added similar series parser (`getRelated`).
- Added query URL space encoding fix.

### 3. **`MtlNovelProvider.kt`**
- Fixed tag indexing layout crash (changed `lis.getOrNull(5)` to `lis.getOrNull(3)`).
- Added `getRelated` AJAX endpoints.

### 4. **`NovelFireProvider.kt`**
- Replaced the multi-page sequential HTML chapter list scraping with a single fast AJAX request: `/ajax/listChapterDataAjax`. This significantly speeds up chapter loading and returns actual titles & dates.
- Added reviews and related novels API integration.

### 5. **`NovelLightProvider.kt`**
- Added comments/reviews API integration.
- Added related fictions via swiper slides.
- Fixed search space queries.

### 6. **`PawReadProvider.kt`**
- Updated base domain from `m.pawread.com` (mobile) to `pawread.com` (desktop) and updated all page selectors.
- Added check `selectFirst("div>svg") == null` to automatically filter out locked chapters.
- Added related novels and review API hooks while preserving rotated User-Agent headers.

### 7. **`RanobesProvider.kt`**
- Added related novels DOM parsing.
- Added comments/reviews parsing from engine controller AJAX.
- Kept our randomized User-Agent rotation.

### 8. **`ReadhiveProvider.kt`**
- Simplified tags layout selector to match the site's update.
- Added related novels swiper slider parser.

### 9. **`WattpadProvider.kt`**
- Parsed similar stories in `getRelated`.
- Ignored their broken reviews implementation.
- Preserved our **parallel chapter page fetching coroutines** in `loadHtml`.

### 10. **`WuxiaBoxProvider.kt`**
- Integrated `getRelated` DOM parsing.

### 11. **`WuxiaClickProvider.kt`**
- Enabled mirror site `wuxia.click`.
- Integrated `wuxiaworld.eu` APIs for reviews and recommendations while keeping our optimized API-based chapter retrieval.

---

## Verification Tasks (User-Driven)

1. Please test compiling the application with the new extensions:
   ```powershell
   ./gradlew :app:assembleDebug
   ```
2. Once compiled, run the app to check:
   - Search queries still work and return results.
   - Novel descriptions load smoothly.
   - Chapters load and display content correctly.
