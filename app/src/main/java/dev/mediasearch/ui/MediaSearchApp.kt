package dev.mediasearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediasearch.*
import dev.mediasearch.core.*
import dev.mediasearch.session.SessionStatus
import kotlinx.coroutines.launch
import java.net.URLEncoder

private data class BrowserDestination(val platform: Platform, val url: String, val login: Boolean)

/** How many results of one platform are shown before the user asks for more. */
private const val INITIAL_VISIBLE_RESULTS = 3

/** How many extra results one tap on "更多" reveals (already fetched ones first). */
private const val VISIBLE_RESULTS_STEP = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSearchApp(model: SearchViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val sessions by model.sessions.statuses.collectAsStateWithLifecycle()
    val library by model.library.state.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var browser by remember { mutableStateOf<BrowserDestination?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showHome by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf("") }
    var sort by remember { mutableStateOf(ResultSort.RELEVANCE) }
    var merged by rememberSaveable { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var controlsOpen by rememberSaveable { mutableStateOf(false) }
    val selectedItems = remember(state.searchId) { mutableStateMapOf<String, Boolean>() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val pageStates = rememberSaveableStateHolder()

    // Hoisted above the browser/about branches: these composables are removed from the
    // composition while a detail page is open, so list state remembered inside them would be
    // thrown away. One state per list keeps the two scroll positions independent.
    val searchListState = rememberLazyListState()
    val accountListState = rememberLazyListState()

    // Per-platform "how many results are visible" counters, keyed on the search round.
    // SearchViewModel bumps state.searchId on every search() — including a repeat of the same
    // query — so a new search starts at three again, while retry/more and returning from a
    // detail page keep the counts. Like the list states above, this lives outside the branches.
    val visibleCounts = remember(state.searchId) { mutableStateMapOf<Platform, Int>() }

    fun login(platform: Platform) { browser = BrowserDestination(platform, platform.homeUrl, sessions[platform] != SessionStatus.VERIFIED) }
    fun open(entry: SearchItem) {
        model.local { model.library.recordVisit(entry) }
        browser = BrowserDestination(entry.platform, entry.url, false)
    }
    fun webSearch(platform: Platform) {
        val query = URLEncoder.encode(state.query.ifBlank { state.input }.trim(), "UTF-8")
        val url = when (platform) {
            Platform.XHS -> "https://www.xiaohongshu.com/search_result?keyword=$query&source=web_explore_feed"
            Platform.ZHIHU -> "https://www.zhihu.com/search?type=content&q=$query"
            Platform.BILIBILI -> "https://search.bilibili.com/all?keyword=$query"
            Platform.DOUYIN -> dev.mediasearch.douyin.DouyinPageClient.searchUrl(state.query.ifBlank { state.input }.trim())
        }
        browser = BrowserDestination(platform, url, false)
    }
    fun startSearch() {
        model.search()
        if (state.enabled.isNotEmpty() && state.input.isNotBlank()) { showHome = false; filter = "" }
        // A new search starts at the top. Opening a detail page and coming back takes neither
        // this path nor a state.searchId change, so it keeps the scroll position and the counts.
        scope.launch { searchListState.scrollToItem(0) }
    }
    CollectionTheme {
        val destination = browser
        if (destination != null) {
            PlatformBrowser(destination.platform, destination.url, destination.login, model,
                onClose = { browser = null },
                onVerified = { browser = null; model.retry(destination.platform) })
        } else if (tab == 3 && showAbout) {
            AboutScreen(onBack = { showAbout = false })
        } else if (tab == 3 && showHelp) {
            HelpScreen(model, onBack = { showHelp = false })
        } else {
            BackHandler(enabled = tab == 0 && !showHome && state.query.isNotBlank()) { showHome = true }
            Scaffold(
                snackbarHost = { notice?.let { message -> Snackbar(action = { TextButton(onClick = { model.notify(null) }) { Text("知道了") } }) { Text(message) } } },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(when (tab) { 1 -> "内容库"; 2 -> "账号"; 3 -> "设置"; else -> "OpenScope" }, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            Icon(
                                AppIcons.Explore,
                                contentDescription = "OpenScope",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp).size(28.dp)
                            )
                        },
                        actions = { TextButton(onClick = { tab = 2 }) { Text("账号") } },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        listOf(
                            "搜索" to AppIcons.Explore,
                            "内容库" to AppIcons.Bookmark,
                            "账号" to AppIcons.Person,
                            "设置" to AppIcons.Settings
                        ).forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(selected = tab == index, onClick = { tab = index },
                                icon = { Icon(icon, contentDescription = label) }, label = { Text(label) })
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> LazyColumn(
                        state = searchListState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = state.input, onValueChange = model::input,
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("搜索你感兴趣的事") },
                                shape = RoundedCornerShape(28.dp),
                                leadingIcon = { Icon(AppIcons.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = {
                                    FilledTonalButton(onClick = { keyboard?.hide(); startSearch() }, enabled = state.input.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.padding(end = 8.dp)) { Text("搜索") }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); startSearch() })
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Platform.entries.forEach { platform ->
                                    FilterChip(selected = platform in state.enabled, onClick = { model.togglePlatform(platform) },
                                        label = { Text(platform.label + if (platform == Platform.DOUYIN) " · 实验" else "") },
                                        leadingIcon = { PlatformLogo(platform, 20.dp) })
                                }
                            }
                        }
                        if (state.query.isBlank() || showHome) {
                            if (state.query.isNotBlank()) item { OutlinedButton(onClick = { showHome = false }) { Text("查看上次搜索：${state.query}") } }
                            item { TrendingSection(model) { term -> model.input(term); model.search(); if (state.enabled.isNotEmpty()) showHome = false } }
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text("你的内容来源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Platform.entries.forEach { platform ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                PlatformMark(platform)
                                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                                    Text(platform.label, style = MaterialTheme.typography.bodyLarge)
                                                    Text(sessions[platform]?.label.orEmpty(),
                                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                TextButton(onClick = { login(platform) }) { Text(accountActionLabel(sessions[platform])) }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { showHome = true }) { Text("返回首页") }
                                    TextButton(onClick = { model.search(force = true) }) { Text("刷新结果") }
                                    TextButton(onClick = { exportOpen = !exportOpen }) { Text("导出 / 复制") }
                                    TextButton(onClick = { controlsOpen = !controlsOpen }) { Text("筛选 / 排序") }
                                }
                                if (controlsOpen) {
                                OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("筛选已加载结果，空格分隔关键词") })
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ResultSort.entries.forEach { choice -> FilterChip(selected = sort == choice, onClick = { sort = choice }, label = { Text(choice.label) }) }
                                    FilterChip(selected = merged, onClick = { merged = !merged }, label = { Text("合并视图") })
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = state.selected == null, onClick = { model.select(null) }, label = { Text("全部结果") })
                                    state.results.keys.forEach { p -> FilterChip(selected = state.selected == p, onClick = { model.select(p) }, label = { Text(p.label) }) }
                                }
                                if (sort != ResultSort.RELEVANCE) Text("仅排序已加载结果；互动为平台已返回的点赞等计数，缺失值排后", style = MaterialTheme.typography.labelSmall)
                                } else if (filter.isNotBlank() || sort != ResultSort.RELEVANCE || state.selected != null || merged) {
                                    Text("${sort.label} · ${state.selected?.label ?: "全部来源"}${if (filter.isNotBlank()) " · 筛选中" else ""}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (exportOpen) {
                                    val all = ResultTools.filterAndSort(state.results.filterKeys { state.selected == null || state.selected == it }.values.flatMap { it.items }, filter, sort)
                                    val chosen = all.filter { selectedItems["${it.platform}:${it.id}"] == true }
                                    Text(if (chosen.isEmpty()) "导出筛选后的 ${all.size} 条" else "导出已选 ${chosen.size} 条")
                                    ExportActions(chosen.ifEmpty { all })
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("“${state.query}”", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2)
                                    Text(if (merged) "跨平台合并" else "按来源呈现", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (merged) {
                                val candidates = state.results.filterKeys { state.selected == null || state.selected == it }.flatMap { (platform, result) ->
                                    ResultTools.filterAndSort(result.items, filter, sort).take(visibleCounts[platform] ?: INITIAL_VISIBLE_RESULTS)
                                }
                                val groups = ResultTools.groupDuplicates(ResultTools.filterAndSort(candidates, "", sort))
                                items(groups, key = { "merged-${it.first().platform}-${it.first().id}" }) { group ->
                                    val entry = group.first()
                                    ResultCard(entry, library.entries.any { it.item.platform == entry.platform && it.item.id == entry.id && it.favorite },
                                        library.entries.any { it.item.platform == entry.platform && it.item.id == entry.id && it.later },
                                        onFavorite = { model.local { model.library.toggleFavorite(entry) } }, onLater = { model.local { model.library.toggleLater(entry) } },
                                        filter = filter, onOpen = { open(entry) })
                                    if (group.size > 1) Row(Modifier.horizontalScroll(rememberScrollState())) { group.forEach { other -> TextButton(onClick = { open(other) }) { Text(other.platform.label) } } }
                                }
                            }
                            state.results.filterKeys { state.selected == null || state.selected == it }.forEach { (platform, result) ->
                                val visible = visibleCounts[platform] ?: INITIAL_VISIBLE_RESULTS
                                val filtered = ResultTools.filterAndSort(result.items, filter, sort)
                                val shown = filtered.take(visible)
                                val hidden = filtered.size - shown.size
                                item(key = "header-${platform.name}") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PlatformMark(platform)
                                        Text(platform.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                                        TextButton(onClick = { visibleCounts[platform] = INITIAL_VISIBLE_RESULTS; model.refreshPlatform(platform) }, enabled = !result.loading) { Text("重搜") }
                                        result.elapsedMs?.let { Text(if (result.cached) "缓存 · ${it}ms" else "${it}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                                if (result.loading) item(key = "loading-${platform.name}") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                                result.message?.let { message ->
                                    item(key = "message-${platform.name}") {
                                        StatusCard(message,
                                            when (result.failure) {
                                                FailureKind.LOGIN_REQUIRED -> "在 App 内登录"
                                                FailureKind.UNSUPPORTED, FailureKind.CHALLENGE, FailureKind.RATE_LIMITED -> "查看官方网页"
                                                else -> "重试"
                                            },
                                            onAction = { when (result.failure) {
                                                FailureKind.LOGIN_REQUIRED -> login(platform)
                                                FailureKind.UNSUPPORTED, FailureKind.CHALLENGE, FailureKind.RATE_LIMITED -> webSearch(platform)
                                                else -> model.retry(platform)
                                            } },
                                            alternate = if (result.failure !in setOf(FailureKind.UNSUPPORTED, FailureKind.LOGIN_REQUIRED, FailureKind.CHALLENGE, FailureKind.RATE_LIMITED)) ({ webSearch(platform) }) else null)
                                    }
                                }
                                if (!result.loading && result.message == null && result.items.isEmpty()) item(key = "empty-${platform.name}") { Text("没有找到相关内容，试试换个关键词。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (!merged) items(shown, key = { "${platform.name}-${it.id}" }) { entry ->
                                    val saved = library.entries.firstOrNull { it.item.platform == platform && it.item.id == entry.id }
                                    if (exportOpen) Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = selectedItems["${platform}:${entry.id}"] == true, onCheckedChange = { selectedItems["${platform}:${entry.id}"] = it })
                                        Text("选择此条", style = MaterialTheme.typography.labelSmall)
                                    }
                                    ResultCard(entry, saved?.favorite == true, saved?.later == true,
                                        onFavorite = { model.local { model.library.toggleFavorite(entry) } }, onLater = { model.local { model.library.toggleLater(entry) } },
                                        filter = filter, onOpen = { open(entry) })
                                }
                                if (hidden > 0 || result.hasMore) {
                                    item(key = "more-${platform.name}") {
                                        OutlinedButton(
                                            onClick = {
                                                // Reveal the results that are already fetched first; only ask for
                                                // another page once the fetched ones are all on screen.
                                                visibleCounts[platform] = minOf(visible, filtered.size) + VISIBLE_RESULTS_STEP
                                                if (hidden == 0) model.more(platform)
                                            },
                                            enabled = !result.loading,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("更多${platform.label}结果") }
                                    }
                                }
                            }
                        }
                    }
                    1 -> pageStates.SaveableStateProvider("library") { LibraryScreen(model, modifier = Modifier.padding(padding), onOpen = ::open) }
                    2 -> LazyColumn(
                        state = accountListState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item { Text("连接你的世界", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                        item { Text("用已有平台账号登录，会话留在这台手机。官方 App 的登录状态不会自动同步。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(Platform.entries) { platform ->
                            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { PlatformMark(platform); Text(platform.label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge) }
                                    Text(sessions[platform]?.label.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (platform == Platform.XHS) {
                                        Text("使用官方网页会话搜索笔记。", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (platform == Platform.DOUYIN) Text("实验性网页搜索；会话保存不代表登录已确认。", style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = { login(platform) }) { Text(accountActionLabel(sessions[platform])) }
                                }
                            }
                        }
                    }
                    else -> SettingsScreen(onOpenAbout = { showAbout = true }, modifier = Modifier.padding(padding), onDiagnostics = { showHelp = true })
                }
            }
        }
    }
}

/**
 * One concise label for the account button, shared by the source card and the account list.
 * The wording follows the session state that is shown right above it.
 */
private fun accountActionLabel(status: SessionStatus?): String =
    if (status == SessionStatus.CAPTURED || status == SessionStatus.VERIFIED) "打开账号" else "登录"

@Composable
private fun PlatformMark(platform: Platform) {
    PlatformLogo(platform)
}

@Composable
private fun StatusCard(message: String, action: String, onAction: () -> Unit, alternate: (() -> Unit)?) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAction) { Text(action) }
                if (alternate != null) TextButton(onClick = alternate) { Text("查看网页") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(item: SearchItem, favorite: Boolean, later: Boolean, onFavorite: () -> Unit, onLater: () -> Unit, filter: String = "", onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                if (item.thumbnailUrl.isNotBlank()) ResultPreview(item)
                Text(highlight(item.title, filter, MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PlatformLogo(item.platform, 20.dp)
                Text("${item.platform.label} · ${item.author.ifBlank { "未知作者" }}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val facts = ResultMetadata.facts(item)
            if (facts.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                facts.forEach { fact ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text("${fact.label} ${fact.value}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onFavorite) { Text(if (favorite) "已收藏" else "收藏") }
                TextButton(onClick = onLater) { Text(if (later) "已加入稍后" else "稍后再看") }
            }
        }
    }
}
