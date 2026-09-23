package dev.mediasearch.core

import java.text.Normalizer
import java.util.Locale

/** The order in which result collections can be presented to the user. */
enum class ResultSort(val label: String) {
    RELEVANCE("综合"),
    LATEST("最新"),
    ENGAGEMENT("互动最多")
}

/** Small, deterministic operations shared by the result list and export actions. */
object ResultTools {
    fun filterAndSort(
        items: List<SearchItem>,
        keywords: String,
        sort: ResultSort
    ): List<SearchItem> {
        val tokens = keywords
            .normalizeForSearch()
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)

        val filtered = if (tokens.isEmpty()) {
            items
        } else {
            items.filter { item ->
                val haystack = listOf(item.title, item.summary, item.author)
                    .joinToString(" ")
                    .normalizeForSearch()
                tokens.all(haystack::contains)
            }
        }

        if (sort == ResultSort.RELEVANCE) return filtered

        // Carry the source position explicitly. This makes the tie behaviour stable even
        // if the implementation of a collection's sort changes in a future Kotlin version.
        return filtered.withIndex()
            .sortedWith { left, right ->
                val comparison = when (sort) {
                    ResultSort.LATEST -> compareNullableDescending(
                        left.value.publishedAt,
                        right.value.publishedAt
                    )
                    ResultSort.ENGAGEMENT -> compareNullableDescending(
                        left.value.engagement,
                        right.value.engagement
                    )
                    ResultSort.RELEVANCE -> 0
                }
                if (comparison != 0) comparison else left.index.compareTo(right.index)
            }
            .map { it.value }
    }

    /**
     * Return stable groups of results. Every input item is represented exactly once after
     * same-platform IDs have been collapsed. Cross-platform grouping deliberately uses a
     * strict key: punctuation and spacing may differ, but the meaningful title characters
     * and the non-empty author must be identical.
     */
    fun groupDuplicates(items: List<SearchItem>): List<List<SearchItem>> {
        val unique = ArrayList<SearchItem>(items.size)
        val seenIds = HashSet<String>()
        for (item in items) {
            val id = item.id.trim()
            val identity = if (id.isNotEmpty()) "${item.platform}\u0000$id" else null
            if (identity == null || seenIds.add(identity)) unique += item
        }

        val groups = ArrayList<MutableList<SearchItem>>(unique.size)
        for (item in unique) {
            val key = duplicateKey(item)
            if (key == null) {
                groups += arrayListOf(item)
                continue
            }

            // A group may contain at most one result from a platform. This also means that
            // two independent IDs on one platform never become a fuzzy bridge to another.
            val existing = groups.firstOrNull { group ->
                group.firstOrNull()?.let { first ->
                    duplicateKey(first) == key && group.none { it.platform == item.platform }
                } == true
            }
            if (existing == null) groups += arrayListOf(item) else existing += item
        }
        return groups.map { it.toList() }
    }

    /** Export a UTF-8 CSV with every cell quoted and spreadsheet formulas neutralised. */
    fun csv(items: List<SearchItem>): String {
        val header = listOf("平台", "ID", "标题", "作者", "摘要", "发布时间", "互动数", "浏览数", "原文链接")
        val rows = ArrayList<List<String>>(items.size + 1)
        rows += header
        rows += items.map { item ->
            listOf(
                item.platform.label,
                item.id,
                item.title,
                item.author,
                item.summary,
                item.publishedAt?.toString().orEmpty(),
                item.engagement?.toString().orEmpty(),
                item.views?.toString().orEmpty(),
                safeOfficialUrl(item).orEmpty()
            )
        }
        return "\uFEFF" + rows.joinToString(separator = "\r\n", postfix = "\r\n") { row ->
            row.joinToString(",") { csvCell(it) }
        }
    }

    /** Export readable Markdown while escaping all user-controlled text. */
    fun markdown(items: List<SearchItem>): String {
        if (items.isEmpty()) return "# 搜索结果\n"
        return buildString {
            appendLine("# 搜索结果")
            items.forEachIndexed { index, item ->
                if (index > 0) appendLine("\n---")
                appendLine()
                append("## ").appendLine(markdownText(item.title))
                append("平台：").append(item.platform.label)
                    .append(" · 作者：").appendLine(markdownText(item.author.ifBlank { "未知" }))
                if (item.summary.isNotBlank()) appendLine(markdownText(item.summary))
                if (item.publishedAt != null || item.engagement != null || item.views != null) {
                    append("发布时间：").append(item.publishedAt?.toString() ?: "未知")
                    append(" · 互动数：").append(item.engagement?.toString() ?: "未知")
                    append(" · 浏览数：").appendLine(item.views?.toString() ?: "未知")
                }
                val url = safeOfficialUrl(item)
                if (url == null) {
                    appendLine("原文链接不可用")
                } else {
                    // Angle brackets keep URL punctuation out of the link label. URI parsing
                    // has already rejected credentials, non-HTTPS schemes, and lookalike hosts.
                    append("[打开原文](<")
                        .append(url.replace("<", "%3C").replace(">", "%3E"))
                        .appendLine(">)")
                }
            }
        }
    }

    /** Return distinct, complete links which belong to the official platform domain. */
    fun links(items: List<SearchItem>): String = items
        .asSequence()
        .mapNotNull(::safeOfficialUrl)
        .distinct()
        .joinToString("\n")

    private fun duplicateKey(item: SearchItem): String? {
        val title = item.title.normalizeTitle()
        val author = item.author.normalizeAuthor()
        if (title.codePointCount(0, title.length) < MIN_DUPLICATE_TITLE_CHARS || author.isEmpty()) {
            return null
        }
        return "$title\u0000$author"
    }

    private fun safeOfficialUrl(item: SearchItem): String? {
        val url = item.url.trim()
        return url.takeIf { it.isNotEmpty() && BrowserProfile.allowed(item.platform, it) }
    }

    private fun csvCell(value: String): String {
        // Excel and other spreadsheet programs interpret these prefixes as formulas even
        // when the CSV field is quoted. Keep leading whitespace in the check by design.
        val safe = if (FORMULA_PREFIX.containsMatchIn(value)) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private fun markdownText(value: String): String = value
        .replace(Regex("[\\r\\n]+"), " ")
        .replace(MARKDOWN_SPECIALS) { "\\${it.value}" }

    private fun String.normalizeForSearch(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)

    private fun String.normalizeTitle(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .codePoints()
        .filter { Character.isLetterOrDigit(it) }
        .toArray()
        .let { codePoints -> buildString(codePoints.size) { codePoints.forEach { appendCodePoint(it) } } }

    private fun String.normalizeAuthor(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

    private fun compareNullableDescending(left: Long?, right: Long?): Int = when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        else -> right.compareTo(left)
    }

    private const val MIN_DUPLICATE_TITLE_CHARS = 12
    private val FORMULA_PREFIX = Regex("^[\\s\\u0000-\\u001F]*[=+\\-@]")
    private val MARKDOWN_SPECIALS = Regex("[\\\\`*_{}\\[\\]()<>#!|&~^]")
}
