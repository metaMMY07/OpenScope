package dev.mediasearch.library

import dev.mediasearch.core.Platform
import dev.mediasearch.core.SearchItem
import org.json.JSONArray
import org.json.JSONObject

/** A small, dependency-free JSON codec for portable local-library backups. */
object LocalLibraryCodec {
    const val VERSION = 1
    const val MAX_NOTE_LENGTH = 1000
    const val MAX_FOLDER_LENGTH = 60
    const val MAX_ITEMS = 500
    const val MAX_HISTORY = 1000
    const val MAX_BACKUP_BYTES = 16 * 1024 * 1024

    internal object MergePolicy {
        /** Existing user edits win, including a deliberately cleared note/default folder. */
        fun preserveText(existing: String?, incoming: String): String =
            existing ?: incoming

        /** An older backup must never move a stored visit backwards in time. */
        fun newestTimestamp(existing: Long?, incoming: Long): Long =
            if (existing == null) incoming else maxOf(existing, incoming)
    }

    data class EntryRecord(
        val item: SearchItem,
        val favorite: Boolean,
        val later: Boolean,
        val folder: String,
        val note: String,
        val savedAt: Long
    )

    data class HistoryRecord(val item: SearchItem, val visitedAt: Long)

    data class Backup(
        val entries: List<EntryRecord>,
        val history: List<HistoryRecord>,
        val folders: List<String>
    )

    fun encode(
        entries: List<EntryRecord>,
        history: List<HistoryRecord>,
        folders: List<String>
    ): String {
        val root = JSONObject()
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis() / 1000L)
        root.put("folders", JSONArray().also { array -> folders.forEach(array::put) })
        root.put("items", JSONArray().also { array -> entries.forEach { array.put(encodeEntry(it)) } })
        root.put("history", JSONArray().also { array -> history.forEach { array.put(encodeHistory(it)) } })
        return root.toString().also { json ->
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
                "备份文件超过 16MB"
            }
        }
    }

    fun decode(json: String): Backup {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "备份文件超过 16MB"
        }
        val root = try {
            JSONObject(json)
        } catch (error: Exception) {
            throw IllegalArgumentException("备份文件不是有效 JSON", error)
        }

        val version = root.requiredInt("version")
        require(version == VERSION) { "不支持的备份版本: $version" }
        val folders = decodeFolders(root.optJSONArray("folders"))
        val entries = decodeEntries(root.requiredArray("items"))
        val history = decodeHistory(root.optJSONArray("history"))
        return Backup(entries = entries, history = history, folders = folders)
    }

    private fun encodeEntry(record: EntryRecord): JSONObject = JSONObject()
        .put("item", encodeItem(record.item))
        .put("favorite", record.favorite)
        .put("later", record.later)
        .put("folder", record.folder)
        .put("note", record.note)
        .put("savedAt", record.savedAt)

    private fun encodeHistory(record: HistoryRecord): JSONObject = JSONObject()
        .put("item", encodeItem(record.item))
        .put("visitedAt", record.visitedAt)

    private fun encodeItem(item: SearchItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("platform", item.platform.name)
        .put("title", item.title)
        .put("author", item.author)
        .put("summary", item.summary)
        .put("url", item.url)
        .put("thumbnailUrl", item.thumbnailUrl)
        .put("metric", item.metric)
        .putOptionalLong("publishedAt", itemMetric(item, "publishedAt"))
        .putOptionalLong("engagement", itemMetric(item, "engagement"))
        .putOptionalLong("views", itemMetric(item, "views"))

    private fun decodeFolders(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val raw = array.get(index)
            require(raw is String) { "收藏夹名称必须是文字" }
            val name = raw.trim()
            require(name.isNotEmpty() && name.length <= MAX_FOLDER_LENGTH) {
                "收藏夹名称无效"
            }
            if (result.none { it.equals(name, ignoreCase = true) }) result += name
        }
        return result
    }

    private fun decodeEntries(array: JSONArray): List<EntryRecord> {
        require(array.length() <= MAX_ITEMS) { "备份中的条目超过 $MAX_ITEMS 项" }
        val result = ArrayList<EntryRecord>(array.length())
        val keys = HashSet<String>()
        for (index in 0 until array.length()) {
            val objectValue = array.get(index)
            require(objectValue is JSONObject) { "备份条目格式无效" }
            val item = decodeItem(objectValue.requiredObject("item"))
            val key = key(item)
            require(keys.add(key)) { "备份中有重复条目" }
            val folder = objectValue.requiredString("folder").trim()
            val note = objectValue.requiredString("note")
            require(folder.length <= MAX_FOLDER_LENGTH) { "收藏夹名称过长" }
            require(note.length <= MAX_NOTE_LENGTH) { "备注不能超过 $MAX_NOTE_LENGTH 个字符" }
            result += EntryRecord(
                item = item,
                favorite = objectValue.requiredBoolean("favorite"),
                later = objectValue.requiredBoolean("later"),
                folder = folder,
                note = note,
                savedAt = objectValue.requiredLong("savedAt")
            )
        }
        return result
    }

    private fun decodeHistory(array: JSONArray?): List<HistoryRecord> {
        if (array == null) return emptyList()
        require(array.length() <= MAX_HISTORY) { "备份中的历史记录超过 $MAX_HISTORY 条" }
        val result = ArrayList<HistoryRecord>(array.length())
        val keys = HashSet<String>()
        for (index in 0 until array.length()) {
            val objectValue = array.get(index)
            require(objectValue is JSONObject) { "历史记录格式无效" }
            val item = decodeItem(objectValue.requiredObject("item"))
            require(keys.add(key(item))) { "备份中有重复历史记录" }
            result += HistoryRecord(item, objectValue.requiredLong("visitedAt"))
        }
        return result
    }

    private fun decodeItem(value: JSONObject): SearchItem {
        val id = value.requiredString("id")
        val platformName = value.requiredString("platform")
        val platform = try {
            Platform.valueOf(platformName)
        } catch (error: Exception) {
            throw IllegalArgumentException("未知平台: $platformName", error)
        }
        return buildSearchItem(
            id = id,
            platform = platform,
            title = value.requiredString("title"),
            author = value.requiredString("author"),
            summary = value.requiredString("summary"),
            url = value.requiredString("url"),
            thumbnailUrl = value.requiredString("thumbnailUrl"),
            metric = value.requiredString("metric"),
            publishedAt = value.optionalLong("publishedAt"),
            engagement = value.optionalLong("engagement"),
            views = value.optionalLong("views")
        )
    }

    internal fun buildSearchItem(
        id: String, platform: Platform, title: String, author: String, summary: String, url: String,
        thumbnailUrl: String, metric: String, publishedAt: Long?, engagement: Long?, views: Long?
    ): SearchItem = SearchItem(id, platform, title, author, summary, url, thumbnailUrl, metric, publishedAt, engagement, views)

    internal fun itemMetric(item: SearchItem, name: String): Long? = when (name) {
        "publishedAt" -> item.publishedAt
        "engagement" -> item.engagement
        "views" -> item.views
        else -> null
    }

    private fun key(item: SearchItem): String = "${item.platform.name}\u0000${item.id}"

    private fun JSONObject.requiredObject(name: String): JSONObject {
        val value = get(name)
        require(value is JSONObject) { "$name 必须是对象" }
        return value
    }

    private fun JSONObject.requiredArray(name: String): JSONArray {
        val value = get(name)
        require(value is JSONArray) { "$name 必须是数组" }
        return value
    }

    private fun JSONObject.requiredString(name: String): String {
        val value = get(name)
        require(value is String) { "$name 必须是文字" }
        return value
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean {
        val value = get(name)
        require(value is Boolean) { "$name 必须是布尔值" }
        return value
    }

    private fun JSONObject.requiredLong(name: String): Long {
        val value = get(name)
        require(value is Number && value !is Float && value !is Double) { "$name 必须是整数" }
        return value.toLong()
    }

    private fun JSONObject.requiredInt(name: String): Int {
        val value = get(name)
        require(value is Number && value !is Float && value !is Double) { "$name 必须是整数" }
        require(value.toLong() in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$name 超出范围" }
        return value.toInt()
    }

    private fun JSONObject.optionalLong(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        val value = get(name)
        require(value is Number && value !is Float && value !is Double) { "$name 必须是整数" }
        return value.toLong()
    }

    private fun JSONObject.putOptionalLong(name: String, value: Long?): JSONObject =
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
}
