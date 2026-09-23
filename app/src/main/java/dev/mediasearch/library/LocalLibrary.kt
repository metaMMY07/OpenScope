package dev.mediasearch.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import dev.mediasearch.core.BrowserProfile
import dev.mediasearch.core.Platform
import dev.mediasearch.core.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LibraryEntry(
    val item: SearchItem,
    val favorite: Boolean,
    val later: Boolean,
    val folder: String,
    val note: String,
    val savedAt: Long
)

data class HistoryEntry(val item: SearchItem, val visitedAt: Long)

data class LibraryState(
    val entries: List<LibraryEntry> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val folders: List<String> = emptyList()
)

/**
 * SQLite-backed, process-independent local library.
 *
 * Every public operation that touches SQLite runs on Dispatchers.IO and is serialized so
 * that a state refresh can never observe half of a write transaction.
 */
class LocalLibrary(
    context: Context,
    databaseName: String = DEFAULT_DATABASE_NAME
) : AutoCloseable {
    companion object {
        const val DEFAULT_DATABASE_NAME = "local_library.db"
        /** Favorites and later entries share the same 500-row local-library budget. */
        const val MAX_ENTRIES = 500
        const val MAX_FAVORITES = 500
        const val MAX_HISTORY = 1000
        const val MAX_NOTE_LENGTH = LocalLibraryCodec.MAX_NOTE_LENGTH
        const val MAX_FOLDER_LENGTH = LocalLibraryCodec.MAX_FOLDER_LENGTH
        private const val DATABASE_VERSION = 2
        private const val TABLE_ENTRIES = "library_entries"
        private const val TABLE_HISTORY = "library_history"
        private const val TABLE_FOLDERS = "library_folders"
        private val RESERVED_FOLDER_NAMES = setOf("全部", "全部收藏", "默认收藏夹", "稍后再看")
        private val ENTRY_COLUMNS = arrayOf(
            "platform", "item_id", "title", "author", "summary", "url", "thumbnail_url", "metric",
            "published_at", "engagement", "views", "metric_counts", "favorite", "later", "folder", "note", "saved_at"
        )
        private val HISTORY_COLUMNS = arrayOf(
            "platform", "item_id", "title", "author", "summary", "url", "thumbnail_url", "metric",
            "published_at", "engagement", "views", "metric_counts", "visited_at"
        )
    }

    private val helper = LibraryDbHelper(context.applicationContext, databaseName)
    private val mutex = Mutex()
    private var closed = false
    private val mutableState = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = mutableState.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Library closed" }
            refreshLocked(helper.readableDatabase)
        }
    }

    suspend fun toggleFavorite(item: SearchItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                val current = findEntry(database, item)
                val favorite = !(current?.favorite ?: false)
                if (favorite && current?.favorite != true) ensureFavoriteCapacity(database, additional = 1)
                val later = current?.later ?: false
                if (!favorite && !later) {
                    deleteEntry(database, item)
                } else {
                    upsertEntry(
                        database = database,
                        item = item,
                        favorite = favorite,
                        later = later,
                        folder = current?.folder.orEmpty(),
                        note = current?.note.orEmpty(),
                        savedAt = current?.savedAt ?: nowSeconds()
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            refreshLocked(database)
        }
    }

    suspend fun toggleLater(item: SearchItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                val current = findEntry(database, item)
                val later = !(current?.later ?: false)
                val favorite = current?.favorite ?: false
                if (!favorite && !later) {
                    deleteEntry(database, item)
                } else {
                    upsertEntry(
                        database = database,
                        item = item,
                        favorite = favorite,
                        later = later,
                        folder = current?.folder.orEmpty(),
                        note = current?.note.orEmpty(),
                        savedAt = current?.savedAt ?: nowSeconds()
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            refreshLocked(database)
        }
    }

    suspend fun edit(item: SearchItem, folder: String, note: String) = withContext(Dispatchers.IO) {
        val cleanFolder = folder.trim()
        require(cleanFolder.length <= MAX_FOLDER_LENGTH) { "收藏夹名称不能超过 $MAX_FOLDER_LENGTH 个字符" }
        require(note.length <= MAX_NOTE_LENGTH) { "备注不能超过 $MAX_NOTE_LENGTH 个字符" }
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                val current = findEntry(database, item)
                // Keep edits made before the first toggle; such a row is intentionally hidden
                // until it receives one of the two independent library flags.
                upsertEntry(
                    database = database,
                    item = item,
                    favorite = current?.favorite ?: false,
                    later = current?.later ?: false,
                    folder = cleanFolder,
                    note = note,
                    savedAt = current?.savedAt ?: nowSeconds()
                )
                if (cleanFolder.isNotEmpty()) insertFolder(database, cleanFolder)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            refreshLocked(database)
        }
    }

    /**
     * Merges a read-only platform collection into the local favorites. Existing metadata wins:
     * a sync must not erase a user's note, folder, or later flag.
     */
    suspend fun saveFavorites(items: List<SearchItem>, folder: String): Int =
        withContext(Dispatchers.IO) {
            val cleanFolder = folder.trim()
            require(cleanFolder.length <= MAX_FOLDER_LENGTH) {
                "收藏夹名称不能超过 $MAX_FOLDER_LENGTH 个字符"
            }
            require(items.size <= MAX_FAVORITES) { "单次同步不能超过 $MAX_FAVORITES 条" }
            val distinct = LinkedHashMap<String, SearchItem>()
            items.forEach { item ->
                distinct.putIfAbsent(itemKey(item), item)
            }
            mutex.withLock {
            check(!closed) { "Library closed" }
                val database = helper.writableDatabase
                database.beginTransaction()
                var added = 0
                try {
                    if (cleanFolder.isNotEmpty()) insertFolder(database, cleanFolder)
                    distinct.values.forEach { item ->
                        val current = findEntry(database, item)
                        if (current == null) {
                            added++
                            upsertEntry(
                                database = database,
                                item = item,
                                favorite = true,
                                later = false,
                                folder = cleanFolder,
                                note = "",
                                savedAt = nowSeconds()
                            )
                        } else {
                            upsertEntry(
                                database = database,
                                item = item,
                                favorite = true,
                                later = current.later,
                                folder = current.folder.ifEmpty { cleanFolder },
                                note = current.note,
                                savedAt = current.savedAt
                            )
                        }
                    }
                    ensureFavoriteCapacity(database)
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
                refreshLocked(database)
                added
            }
        }

    suspend fun addFolder(name: String) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "收藏夹名称不能为空" }
        require(cleanName.length <= MAX_FOLDER_LENGTH) {
            "收藏夹名称不能超过 $MAX_FOLDER_LENGTH 个字符"
        }
        require(cleanName !in RESERVED_FOLDER_NAMES) { "系统收藏夹名称不可用" }
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                insertFolder(database, cleanName)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            refreshLocked(database)
        }
    }

    suspend fun remove(item: SearchItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                deleteEntry(database, item)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            refreshLocked(database)
        }
    }

    suspend fun recordVisit(item: SearchItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                upsertHistory(database, item, nowSeconds())
                trimHistory(database)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            refreshLocked(database)
        }
    }

    suspend fun removeHistory(item: SearchItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                database.delete(
                    TABLE_HISTORY,
                    "platform = ? AND item_id = ?",
                    arrayOf(item.platform.name, item.id)
                )
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            refreshLocked(database)
        }
    }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.readableDatabase
            LocalLibraryCodec.encode(
                entries = queryEntries(database).map {
                    LocalLibraryCodec.EntryRecord(
                        item = it.item,
                        favorite = it.favorite,
                        later = it.later,
                        folder = it.folder,
                        note = it.note,
                        savedAt = it.savedAt
                    )
                },
                history = queryHistory(database).map {
                    LocalLibraryCodec.HistoryRecord(it.item, it.visitedAt)
                },
                folders = queryFolders(database)
            )
        }
    }

    /**
     * Validates the whole payload before opening a write transaction. Existing rows are merged,
     * never cleared; the returned number is the number of new library entries.
     */
    suspend fun importBackup(json: String): Int = withContext(Dispatchers.IO) {
        val backup = LocalLibraryCodec.decode(json)
        validateBackupUrls(backup)
        mutex.withLock {
            check(!closed) { "Library closed" }
            val database = helper.writableDatabase
            database.beginTransaction()
            var added = 0
            try {
                backup.folders.forEach { insertFolder(database, it) }
                backup.entries.forEach { record ->
                    val current = findEntry(database, record.item)
                    val mergedFavorite = current?.favorite == true || record.favorite
                    val mergedLater = current?.later == true || record.later
                    if (current == null) added++
                    upsertEntry(
                        database = database,
                        item = record.item,
                        favorite = mergedFavorite,
                        later = mergedLater,
                        folder = LocalLibraryCodec.MergePolicy.preserveText(current?.folder, record.folder),
                        note = LocalLibraryCodec.MergePolicy.preserveText(current?.note, record.note),
                        savedAt = minTimestamp(current?.savedAt, record.savedAt)
                    )
                    if (record.folder.isNotEmpty()) insertFolder(database, record.folder)
                }
                ensureFavoriteCapacity(database)
                backup.history.forEach { record ->
                    upsertHistory(database, record.item, record.visitedAt)
                }
                trimHistory(database)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            refreshLocked(database)
            added
        }
    }

    override fun close() {
        kotlinx.coroutines.runBlocking { mutex.withLock { closed = true; helper.close() } }
    }

    private fun validateBackupUrls(backup: LocalLibraryCodec.Backup) {
        backup.entries.forEach { validateImportedItem(it.item) }
        backup.history.forEach { validateImportedItem(it.item) }
        backup.entries.forEach { record ->
            require(record.folder.length <= MAX_FOLDER_LENGTH) { "收藏夹名称过长" }
            require(record.note.length <= MAX_NOTE_LENGTH) { "备注不能超过 $MAX_NOTE_LENGTH 个字符" }
        }
    }

    private fun validateImportedItem(item: SearchItem) {
        require(item.id.isNotEmpty()) { "条目 ID 不能为空" }
        require(BrowserProfile.allowed(item.platform, item.url)) {
            "条目 URL 不是该平台允许的 HTTPS 官方地址"
        }
    }

    private fun itemKey(item: SearchItem): String = "${item.platform.name}\u0000${item.id}"

    private fun refreshLocked(database: SQLiteDatabase) {
        mutableState.value = LibraryState(
            entries = queryEntries(database),
            history = queryHistory(database),
            folders = queryFolders(database)
        )
    }

    private fun queryEntries(database: SQLiteDatabase): List<LibraryEntry> {
        val result = ArrayList<LibraryEntry>()
        database.query(
            TABLE_ENTRIES,
            ENTRY_COLUMNS,
            "favorite = 1 OR later = 1",
            null,
            null,
            null,
            "saved_at DESC, platform ASC, item_id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += LibraryEntry(
                    item = itemFromCursor(cursor),
                    favorite = cursor.getInt(cursor.getColumnIndexOrThrow("favorite")) != 0,
                    later = cursor.getInt(cursor.getColumnIndexOrThrow("later")) != 0,
                    folder = cursor.getString(cursor.getColumnIndexOrThrow("folder")),
                    note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                    savedAt = cursor.getLong(cursor.getColumnIndexOrThrow("saved_at"))
                )
            }
        }
        return result
    }

    private fun queryHistory(database: SQLiteDatabase): List<HistoryEntry> {
        val result = ArrayList<HistoryEntry>()
        database.query(
            TABLE_HISTORY,
            HISTORY_COLUMNS,
            null,
            null,
            null,
            null,
            "visited_at DESC, platform ASC, item_id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += HistoryEntry(
                    item = itemFromCursor(cursor),
                    visitedAt = cursor.getLong(cursor.getColumnIndexOrThrow("visited_at"))
                )
            }
        }
        return result
    }

    private fun queryFolders(database: SQLiteDatabase): List<String> {
        val result = ArrayList<String>()
        database.query(
            TABLE_FOLDERS,
            arrayOf("name"),
            null,
            null,
            null,
            null,
            "created_at ASC, name COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) result += cursor.getString(0)
        }
        return result
    }

    private fun itemFromCursor(cursor: android.database.Cursor): SearchItem =
        LocalLibraryCodec.buildSearchItem(
            id = cursor.getString(cursor.getColumnIndexOrThrow("item_id")),
            platform = Platform.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("platform"))),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
            summary = cursor.getString(cursor.getColumnIndexOrThrow("summary")),
            url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
            thumbnailUrl = cursor.getString(cursor.getColumnIndexOrThrow("thumbnail_url")),
            metric = cursor.getString(cursor.getColumnIndexOrThrow("metric")),
            publishedAt = cursor.nullableLong("published_at"),
            engagement = cursor.nullableLong("engagement"),
            views = cursor.nullableLong("views"),
            metricCounts = LocalLibraryCodec.decodeMetricCounts(JSONObject(cursor.getString(cursor.getColumnIndexOrThrow("metric_counts"))))
        )

    private fun findEntry(database: SQLiteDatabase, item: SearchItem): ExistingEntry? {
        database.query(
            TABLE_ENTRIES,
            arrayOf("favorite", "later", "folder", "note", "saved_at"),
            "platform = ? AND item_id = ?",
            arrayOf(item.platform.name, item.id),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ExistingEntry(
                favorite = cursor.getInt(0) != 0,
                later = cursor.getInt(1) != 0,
                folder = cursor.getString(2),
                note = cursor.getString(3),
                savedAt = cursor.getLong(4)
            )
        }
    }

    private fun upsertEntry(
        database: SQLiteDatabase,
        item: SearchItem,
        favorite: Boolean,
        later: Boolean,
        folder: String,
        note: String,
        savedAt: Long
    ) {
        validateImportedItem(item)
        val exists = findEntry(database, item) != null
        if (!exists) database.rawQuery("SELECT COUNT(*) FROM $TABLE_ENTRIES", null).use {
            it.moveToFirst(); require(it.getInt(0) < LocalLibraryCodec.MAX_ITEMS) { "收藏与稍后最多共 500 条" }
        }
        val values = itemValues(item).apply {
            put("favorite", if (favorite) 1 else 0)
            put("later", if (later) 1 else 0)
            put("folder", folder)
            put("note", note)
            put("saved_at", savedAt)
        }
        database.insertWithOnConflict(TABLE_ENTRIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun upsertHistory(database: SQLiteDatabase, item: SearchItem, visitedAt: Long) {
        val previous = database.rawQuery("SELECT visited_at FROM $TABLE_HISTORY WHERE platform = ? AND item_id = ?", arrayOf(item.platform.name, item.id)).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
        val values = itemValues(item).apply { put("visited_at", LocalLibraryCodec.MergePolicy.newestTimestamp(previous, visitedAt)) }
        database.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun itemValues(item: SearchItem): ContentValues = ContentValues().apply {
        put("platform", item.platform.name)
        put("item_id", item.id)
        put("title", item.title)
        put("author", item.author)
        put("summary", item.summary)
        put("url", item.url)
        put("thumbnail_url", item.thumbnailUrl)
        put("metric", item.metric)
        putOptional("published_at", LocalLibraryCodec.itemMetric(item, "publishedAt"))
        putOptional("engagement", LocalLibraryCodec.itemMetric(item, "engagement"))
        putOptional("views", LocalLibraryCodec.itemMetric(item, "views"))
        put("metric_counts", JSONObject(item.metricCounts).toString())
    }

    private fun deleteEntry(database: SQLiteDatabase, item: SearchItem) {
        database.delete(
            TABLE_ENTRIES,
            "platform = ? AND item_id = ?",
            arrayOf(item.platform.name, item.id)
        )
    }

    private fun insertFolder(database: SQLiteDatabase, name: String) {
        database.insertWithOnConflict(
            TABLE_FOLDERS,
            null,
            ContentValues().apply {
                put("name", name)
                put("created_at", nowSeconds())
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private fun ensureFavoriteCapacity(database: SQLiteDatabase, additional: Int = 0) {
        val cursor = database.rawQuery("SELECT COUNT(*) FROM $TABLE_ENTRIES WHERE favorite = 1", null)
        cursor.use {
            it.moveToFirst()
            require(it.getLong(0) + additional <= MAX_FAVORITES) {
                "收藏数量不能超过 $MAX_FAVORITES 条"
            }
        }
    }

    private fun trimHistory(database: SQLiteDatabase) {
        database.execSQL(
            "DELETE FROM $TABLE_HISTORY WHERE rowid NOT IN " +
                "(SELECT rowid FROM $TABLE_HISTORY ORDER BY visited_at DESC, rowid DESC LIMIT $MAX_HISTORY)"
        )
    }

    private data class ExistingEntry(
        val favorite: Boolean,
        val later: Boolean,
        val folder: String,
        val note: String,
        val savedAt: Long
    )

    private class LibraryDbHelper(context: Context, name: String) : SQLiteOpenHelper(
        context,
        name,
        null,
        DATABASE_VERSION
    ) {
        override fun onConfigure(database: SQLiteDatabase) {
            super.onConfigure(database)
            database.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE_ENTRIES (
                    platform TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    author TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    url TEXT NOT NULL,
                    thumbnail_url TEXT NOT NULL,
                    metric TEXT NOT NULL,
                    published_at INTEGER,
                    engagement INTEGER,
                    views INTEGER,
                    metric_counts TEXT NOT NULL DEFAULT '{}',
                    favorite INTEGER NOT NULL DEFAULT 0,
                    later INTEGER NOT NULL DEFAULT 0,
                    folder TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT '',
                    saved_at INTEGER NOT NULL,
                    PRIMARY KEY (platform, item_id)
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE $TABLE_HISTORY (
                    platform TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    author TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    url TEXT NOT NULL,
                    thumbnail_url TEXT NOT NULL,
                    metric TEXT NOT NULL,
                    published_at INTEGER,
                    engagement INTEGER,
                    views INTEGER,
                    metric_counts TEXT NOT NULL DEFAULT '{}',
                    visited_at INTEGER NOT NULL,
                    PRIMARY KEY (platform, item_id)
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE $TABLE_FOLDERS (
                    name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                database.execSQL("ALTER TABLE $TABLE_ENTRIES ADD COLUMN metric_counts TEXT NOT NULL DEFAULT '{}'")
                database.execSQL("ALTER TABLE $TABLE_HISTORY ADD COLUMN metric_counts TEXT NOT NULL DEFAULT '{}'")
            }
            onCreateIfMissing(database)
        }

        private fun onCreateIfMissing(database: SQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_FOLDERS (name TEXT NOT NULL COLLATE NOCASE UNIQUE, created_at INTEGER NOT NULL)"
            )
        }
    }

    private fun android.database.Cursor.nullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun ContentValues.putOptional(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun minTimestamp(first: Long?, second: Long): Long =
        when {
            first == null || first <= 0L -> second
            second <= 0L -> first
            else -> minOf(first, second)
        }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000L

}
