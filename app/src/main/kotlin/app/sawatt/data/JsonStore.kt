package app.sawatt.data

import android.util.Log
import app.sawatt.core.history.StorageJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.File

/** Small JSON-file store under the app's private files directory. Writes are atomic (temp + rename). */
class JsonStore(private val root: File) {

    suspend fun <T> read(path: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        val file = File(root, path)
        if (!file.exists()) return@withContext null
        try {
            StorageJson.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Unreadable $path, ignoring", e)
            null
        }
    }

    suspend fun <T> write(path: String, serializer: KSerializer<T>, value: T) = withContext(Dispatchers.IO) {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(StorageJson.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        File(root, path).delete()
    }

    private companion object {
        const val TAG = "JsonStore"
    }
}
