# Recent Context Cache

> Updated automatically after each session. Agent reads this at session start before re-querying graphify.

## Last Session Summary

**Date**: 2026-06-27  
**Task**: Fix TTS progress resetting back to the first paragraph when reopening the app.  
**Complexity**: Medium

## Files Touched

- `app/src/main/java/com/lagradost/quicknovel/ReadActivityViewModel.kt`

## Graph Nodes Resolved

- `ReadActivityViewModel`
- `isInApp`
- `resumedApp`
- `changeIndex`
- `onScroll`

## Key Decisions Made

- Set the `isInApp = true` variable synchronously inside the main-thread context of `init()`, prior to scheduling any background IO operations.
- Annotated `isInApp` with `@Volatile` to ensure visibility transitions are published cleanly between the main thread and the background TTS execution loop.
- Wrapped `changeIndex()` inside a `runOnMainThread` block to serialize scroll updates and prevent concurrent thread race conflicts when updating settings keys.

## Carry-Over Notes

- Both the Telegram backup feature and TTS paragraph progress reset fixes are complete. Verify their integration via manual execution in Android Studio.
