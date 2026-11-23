package edu.bu.cs501.habitable.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Start over": puts the app back to a fresh install, so setup (apartment and
 * landlord) appears again. Clears every Room table, the photo and export files,
 * and all settings (onboarding flag, sensor choice, sign-in token).
 * The caller stops monitoring first.
 */
suspend fun AppContainer.resetEverything() = withContext(Dispatchers.IO) {
    database.clearAllTables()
    photos.deleteAllFiles()
    settings.clearAll()
}
