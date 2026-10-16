package app.sawatt.core.history

import kotlinx.serialization.json.Json

/** Shared JSON configuration for everything persisted by the app. */
val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}
