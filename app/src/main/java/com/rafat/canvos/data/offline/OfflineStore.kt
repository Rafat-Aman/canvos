package com.rafat.canvos.data.offline

import android.content.Context
import com.rafat.canvos.data.api.CanvasFile
import com.rafat.canvos.data.api.CanvasJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

data class OfflineSettings(
    /** Keep courses available offline and update them in the background. */
    val enabled: Boolean = true,
    /** Background updates only on unmetered networks (Wi-Fi). */
    val wifiOnly: Boolean = true,
    val downloadFiles: Boolean = true,
    /** Larger files are skipped; 0 = no limit. */
    val maxFileMb: Int = 100,
    /** Dashboard courses the user turned off. */
    val excluded: Set<Long> = emptySet(),
    /** Non-dashboard (e.g. past) courses the user turned on. */
    val extra: Set<Long> = emptySet(),
    val lastSuccess: Long = 0L,
    val lastError: String? = null,
) {
    fun isSelected(courseId: Long, onDashboard: Boolean) =
        if (onDashboard) courseId !in excluded else courseId in extra
}

class OfflinePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<OfflineSettings> = _state.asStateFlow()

    private fun ids(key: String) = prefs.getStringSet(key, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    private fun read() = OfflineSettings(
        enabled = prefs.getBoolean("enabled", true),
        wifiOnly = prefs.getBoolean("wifi_only", true),
        downloadFiles = prefs.getBoolean("download_files", true),
        maxFileMb = prefs.getInt("max_file_mb", 100),
        excluded = ids("excluded"),
        extra = ids("extra"),
        lastSuccess = prefs.getLong("last_success", 0L),
        lastError = prefs.getString("last_error", null),
    )

    @Synchronized
    fun update(f: (OfflineSettings) -> OfflineSettings) {
        val n = f(_state.value)
        prefs.edit()
            .putBoolean("enabled", n.enabled)
            .putBoolean("wifi_only", n.wifiOnly)
            .putBoolean("download_files", n.downloadFiles)
            .putInt("max_file_mb", n.maxFileMb)
            .putStringSet("excluded", n.excluded.map { it.toString() }.toSet())
            .putStringSet("extra", n.extra.map { it.toString() }.toSet())
            .putLong("last_success", n.lastSuccess)
            .putString("last_error", n.lastError)
            .apply()
        _state.value = n
    }
}

/** A course file saved on the phone. [path] is relative to [OfflineFiles.root]. */
@Serializable
data class LocalFile(
    val file: CanvasFile,
    val courseId: Long,
    val courseName: String,
    val path: String,
    val version: String,
)

/** Downloaded course files and the manifest that maps Canvas file ids to them. */
class OfflineFiles(val root: File, private val manifest: File) {
    private val lock = Any()
    private var loaded: MutableMap<Long, LocalFile>? = null
    private var dirty = false
    private val _changes = MutableStateFlow(0)

    /** Bumps whenever files are added or removed, so screens can refresh their badges. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun map(): MutableMap<Long, LocalFile> = loaded ?: run {
        val list = runCatching {
            CanvasJson.decodeFromString(ListSerializer(LocalFile.serializer()), manifest.readText())
        }.getOrDefault(emptyList())
        list.associateBy { it.file.id }.toMutableMap().also { loaded = it }
    }

    fun all(): List<LocalFile> = synchronized(lock) { map().values.toList() }

    fun entry(id: Long): LocalFile? = synchronized(lock) { map()[id] }

    fun fileOf(e: LocalFile) = File(root, e.path)

    /** The saved copy of file [id], whatever its version. */
    fun local(id: Long): File? = entry(id)?.let(::fileOf)?.takeIf { it.isFile }

    /** The saved copy of [f] if it is the same version as on Canvas. */
    fun current(f: CanvasFile): File? =
        entry(f.id)?.takeIf { it.version == versionOf(f) }?.let(::fileOf)?.takeIf { it.isFile }

    fun put(e: LocalFile) {
        synchronized(lock) {
            map()[e.file.id] = e
            dirty = true
        }
        _changes.update { it + 1 }
    }

    fun remove(id: Long) {
        val e = synchronized(lock) { map().remove(id)?.also { dirty = true } } ?: return
        val f = fileOf(e)
        f.delete()
        pruneEmptyDirs(f.parentFile)
        _changes.update { it + 1 }
    }

    /** Move a saved file, e.g. after the course or folder was renamed. */
    fun move(e: LocalFile, newPath: String, updated: LocalFile): Boolean {
        val from = fileOf(e)
        val to = File(root, newPath)
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) return false
        pruneEmptyDirs(from.parentFile)
        put(updated.copy(path = newPath))
        return true
    }

    /** Another saved file already lives at [path]. */
    fun pathTaken(path: String, exceptId: Long): Boolean =
        synchronized(lock) { map().values.any { it.path == path && it.file.id != exceptId } }

    fun save() {
        val list = synchronized(lock) {
            if (!dirty) return
            dirty = false
            map().values.toList()
        }
        atomicWrite(manifest, CanvasJson.encodeToString(ListSerializer(LocalFile.serializer()), list).toByteArray())
    }

    fun totalBytes(): Long = all().sumOf { fileOf(it).length() }

    fun clear() {
        synchronized(lock) {
            loaded = mutableMapOf()
            dirty = false
        }
        root.deleteRecursively()
        manifest.delete()
        _changes.update { it + 1 }
    }

    private fun pruneEmptyDirs(start: File?) {
        var d = start
        while (d != null && d != root && d.path.startsWith(root.path) && d.list()?.isEmpty() == true) {
            d.delete()
            d = d.parentFile
        }
    }

    companion object {
        fun versionOf(f: CanvasFile) = "${f.size}|${f.updatedAt}"
    }
}

/**
 * What the offline sync last saved and in which version, so unchanged pages,
 * assignments and topics aren't downloaded again on every run.
 */
class SyncIndex(private val file: File) {
    @Serializable
    data class Entry(val v: String, val t: Long)

    private var loaded: MutableMap<String, Entry>? = null
    private var dirty = false
    private val ser = MapSerializer(String.serializer(), Entry.serializer())

    private fun map(): MutableMap<String, Entry> = loaded ?: run {
        runCatching { CanvasJson.decodeFromString(ser, file.readText()) }.getOrDefault(emptyMap())
            .toMutableMap().also { loaded = it }
    }

    @Synchronized
    fun isFresh(key: String, version: String, maxAgeMs: Long, now: Long): Boolean {
        val e = map()[key] ?: return false
        return e.v == version && now - e.t < maxAgeMs
    }

    @Synchronized
    fun mark(key: String, version: String, now: Long) {
        map()[key] = Entry(version, now)
        dirty = true
    }

    @Synchronized
    fun keys(prefix: String): List<String> = map().keys.filter { it.startsWith(prefix) }

    @Synchronized
    fun save(now: Long = System.currentTimeMillis()) {
        if (!dirty) return
        // Forget entries for things that disappeared long ago.
        map().entries.removeAll { now - it.value.t > 60L * 86_400_000 }
        dirty = false
        atomicWrite(file, CanvasJson.encodeToString(ser, map()).toByteArray())
    }

    @Synchronized
    fun clear() {
        loaded = mutableMapOf()
        dirty = false
        file.delete()
    }
}

/** Everything kept for offline use, under the app's own storage. */
class OfflineStore(context: Context) {
    private val dir = File(context.filesDir, "offline")
    private val ownerFile = File(dir, "owner")
    val responses = ResponseCache(File(dir, "api"))
    val resources = ResourceCache(File(dir, "res"))

    /** App-specific storage: removed with the app, visible over USB under Android/data. */
    private val external = context.getExternalFilesDir(null)
    val files = OfflineFiles(File(external ?: context.filesDir, "Canvas"), File(dir, "files.json"))
    val index = SyncIndex(File(dir, "index.json"))
    val prefs = OfflinePrefs(context)

    /** Where the files are, as the user would find them in a file manager. */
    val filesLocation: String =
        if (external != null) "Android/data/${context.packageName}/files/Canvas" else "the app's private storage"

    /** Saved content belongs to one account; signing in as someone else starts fresh. */
    @Synchronized
    fun claim(owner: String) {
        val current = runCatching { ownerFile.readText() }.getOrNull()
        if (current != null && current != owner) wipe()
        atomicWrite(ownerFile, owner.toByteArray())
    }

    /** Delete all saved content. [keepOwner] when the same account stays signed in. */
    @Synchronized
    fun wipe(keepOwner: Boolean = false) {
        responses.clear()
        resources.clear()
        files.clear()
        index.clear()
        if (!keepOwner) ownerFile.delete()
        prefs.update { it.copy(lastSuccess = 0L, lastError = null) }
    }

    fun bytesUsed(): Long = files.totalBytes() + responses.size() + resources.size()
}
