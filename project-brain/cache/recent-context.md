# Recent Context Cache

> Updated automatically after each session. Agent reads this at session start before re-querying graphify.

## Last Session Summary

**Date**: 2026-06-27  
**Task**: Refined NeoLists feature: rebranded tab to "NeoNexus", added folder details editor dialog for local folders, and fully decoupled folder membership from standard bookmark categories.  
**Complexity**: High

## Files Touched

- `app/src/main/AndroidManifest.xml`
- `benchmark/src/main/java/com/lagradost/quicknovel/benchmark/BaselineProfileGenerator.kt`
- `app/src/main/java/com/lagradost/quicknovel/db/AppDatabase.kt`
- `app/src/main/java/com/lagradost/quicknovel/db/NeoListPinMap.kt`
- `app/src/main/java/com/lagradost/quicknovel/db/NeoListDao.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/neolists/NeoListsViewModel.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/neolists/NeoListsHubScreen.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/download/DownloadScreen.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/result/ResultViewModel.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/result/ResultDetailModernScreen.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/result/ResultDetailDefaultScreen.kt`
- `app/src/main/java/com/lagradost/quicknovel/ui/result/ResultFragment.kt`

## Graph Nodes Resolved

- `NeoListPinMap`
- `NeoListDao`
- `AppDatabase`
- `NeoListsViewModel`
- `NeoListsHubScreen`
- `DownloadScreen`
- `ResultViewModel`
- `ResultDetailModernScreen`
- `ResultDetailDefaultScreen`
- `ResultFragment`

## Key Decisions Made

- Excluded locked NeoList categories (`isLocked = true`) from the bookmark dropdown in both Compose and legacy XML implementations.
- Used `maxId + 1` category ID generation logic to bridge NeoList UUIDs to SharedPreferences category integers, preventing hash collision bugs.
- Decoupled folders from library status categories (`bookmarkType`). Selecting a folder now toggles folder membership directly in `neolist_pin_map` and the folder's `novels` JSON, keeping the novel's main status category (Reading, Completed, etc.) untouched.
- Changed the primary key of `neolist_pin_map` to a composite primary key `(novelHash, neoListId)` to allow a novel to be added to multiple folders simultaneously.

## Carry-Over Notes

- Compile and run project in Android Studio to verify that a novel can be bookmarked to Reading/Completed while also being pinned to multiple folders in the dropdown.
- Check that checking/unchecking folders toggles their state in the details screen and DB.
