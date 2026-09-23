package dev.mediasearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediasearch.SearchViewModel
import dev.mediasearch.core.Platform
import dev.mediasearch.core.ResultTools
import dev.mediasearch.core.SearchItem
import dev.mediasearch.library.HistoryEntry
import dev.mediasearch.library.LibraryEntry
import dev.mediasearch.library.LocalLibraryCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibraryView(val label: String) {
    FAVORITES("收藏"),
    LATER("稍后"),
    HISTORY("历史"),
    PLATFORM("平台收藏")
}

private sealed interface RemoveTarget {
    val item: SearchItem

    data class Entry(override val item: SearchItem) : RemoveTarget
    data class History(override val item: SearchItem) : RemoveTarget
}

private const val PLATFORM_SYNC_FOLDER = "B站同步"

/** Local favorites, reading list, browsing history, backups, and read-only platform sync. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    model: SearchViewModel,
    modifier: Modifier = Modifier,
    onOpen: (SearchItem) -> Unit
) {
    val libraryState by model.library.state.collectAsStateWithLifecycle()
    val syncing by model.syncing.collectAsStateWithLifecycle()
    var selectedView by rememberSaveable { mutableIntStateOf(LibraryView.FAVORITES.ordinal) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var removeTarget by remember { mutableStateOf<RemoveTarget?>(null) }
    var showAddFolder by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // These states intentionally live outside the selected-view branch. Returning from a
    // different view therefore restores the reader's exact position in each list.
    val favoritesListState = rememberLazyListState()
    val laterListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val platformListState = rememberLazyListState()

    val view = LibraryView.entries[selectedView.coerceIn(0, LibraryView.entries.lastIndex)]
    val entries = when (view) {
        LibraryView.FAVORITES -> libraryState.entries.filter { entry ->
            entry.favorite && (selectedFolder == null || entry.folder == selectedFolder)
        }
        LibraryView.LATER -> libraryState.entries.filter { it.later }
        LibraryView.PLATFORM -> libraryState.entries.filter {
            it.favorite && it.item.platform == Platform.BILIBILI && it.folder == PLATFORM_SYNC_FOLDER
        }
        LibraryView.HISTORY -> emptyList()
    }
    val history = if (view == LibraryView.HISTORY) libraryState.history else emptyList()
    val listState: LazyListState = when (view) {
        LibraryView.FAVORITES -> favoritesListState
        LibraryView.LATER -> laterListState
        LibraryView.HISTORY -> historyListState
        LibraryView.PLATFORM -> platformListState
    }
    val exportItems = when (view) {
        LibraryView.HISTORY -> history.map(HistoryEntry::item)
        else -> entries.map(LibraryEntry::item)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model.local {
            val json = model.library.exportBackup()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入备份文件")
            }
            model.notify("备份已保存")
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model.local {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= LocalLibraryCodec.MAX_BACKUP_BYTES) {
                        val count = input.read(buffer, 0, minOf(buffer.size, LocalLibraryCodec.MAX_BACKUP_BYTES + 1 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("无法读取备份文件")
            }
            require(bytes.size <= LocalLibraryCodec.MAX_BACKUP_BYTES) { "备份文件超过 16MB" }
            val added = model.library.importBackup(bytes.toString(Charsets.UTF_8))
            model.notify("已合并导入 $added 条本机条目")
        }
    }

    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("本地库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibraryView.entries.forEachIndexed { index, option ->
                        FilterChip(
                            selected = selectedView == index,
                            onClick = { selectedView = index },
                            label = { Text(option.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        exportBackupLauncher.launch("openscope-library-${fileTimestamp()}.json")
                    }) { Text("导出备份") }
                    OutlinedButton(onClick = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/*"))
                    }) { Text("导入并合并") }
                }
            }

            if (view == LibraryView.FAVORITES) {
                FolderBar(
                    folders = libraryState.folders,
                    selected = selectedFolder,
                    onSelect = { selectedFolder = it },
                    onAdd = { showAddFolder = true }
                )
            }

            if (view == LibraryView.PLATFORM) {
                PlatformSyncPanel(
                    syncing = syncing,
                    onSync = model::syncBilibiliFavorites,
                    onCancel = model::cancelSync
                )
            }

            ExportActions(exportItems)

            Box(modifier = Modifier.fillMaxSize()) {
                if (view == LibraryView.HISTORY) {
                    HistoryList(
                        history = history,
                        listState = listState,
                        onOpen = onOpen,
                        onRemove = { removeTarget = RemoveTarget.History(it) }
                    )
                } else {
                    EntryList(
                        entries = entries,
                        listState = listState,
                        onOpen = onOpen,
                        onEdit = { editTarget = it },
                        onRemove = { removeTarget = RemoveTarget.Entry(it) },
                        onFavorite = { item -> model.local { model.library.toggleFavorite(item) } },
                        onLater = { item -> model.local { model.library.toggleLater(item) } }
                    )
                }
            }
        }
    }

    editTarget?.let { target ->
        EditEntryDialog(
            entry = target,
            onDismiss = { editTarget = null },
            onSave = { folder, note ->
                editTarget = null
                model.local {
                    model.library.edit(target.item, folder, note)
                    model.notify("条目已更新")
                }
            }
        )
    }

    if (showAddFolder) {
        AddFolderDialog(
            onDismiss = { showAddFolder = false },
            onSave = { name ->
                showAddFolder = false
                model.local {
                    model.library.addFolder(name)
                    model.notify("收藏夹已新增")
                }
            }
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("确认移除？") },
            text = { Text("将从本机${if (target is RemoveTarget.History) "历史记录" else "收藏库"}移除“${target.item.title}”。") },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    model.local {
                        if (target is RemoveTarget.History) model.library.removeHistory(target.item)
                        else model.library.remove(target.item)
                        model.notify("已移除")
                    }
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("取消") } }
        )
    }
}

/** Export actions are intentionally local: SAF and the system clipboard are the only sinks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportActions(items: List<SearchItem>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val payload = pending
        pending = null
        if (uri != null && payload != null) writeExport(context, scope, uri, payload)
    }
    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val payload = pending
        pending = null
        if (uri != null && payload != null) writeExport(context, scope, uri, payload)
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(enabled = items.isNotEmpty(), onClick = {
            pending = ResultTools.csv(items)
            csvLauncher.launch("openscope-results-${fileTimestamp()}.csv")
        }) { Text("保存 CSV") }
        OutlinedButton(enabled = items.isNotEmpty(), onClick = {
            pending = ResultTools.markdown(items)
            markdownLauncher.launch("openscope-results-${fileTimestamp()}.md")
        }) { Text("保存 Markdown") }
        OutlinedButton(enabled = items.isNotEmpty(), onClick = {
            val links = ResultTools.links(items)
            if (links.isBlank()) {
                Toast.makeText(context, "当前没有可复制的官方链接", Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OpenScope 原文链接", links))
                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
            }
        }) { Text("复制链接") }
    }
}

@Composable
private fun FolderBar(
    folders: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("全部收藏") })
        folders.forEach { folder ->
            FilterChip(selected = selected == folder, onClick = { onSelect(folder) }, label = { Text(folder) })
        }
        TextButton(onClick = onAdd) { Text("新建收藏夹") }
    }
}

@Composable
private fun PlatformSyncPanel(syncing: Boolean, onSync: () -> Unit, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("平台收藏", style = MaterialTheme.typography.titleMedium)
            Text(
                "哔哩哔哩只读同步，每次最多读取 100 条并合并到本机。其他平台官方收藏入口暂未验证，暂不支持同步。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (syncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onCancel) { Text("取消同步") }
            } else {
                Button(onClick = onSync) { Text("同步 B站收藏") }
            }
        }
    }
}

@Composable
private fun NoticeBanner(notice: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(notice, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

@Composable
private fun EntryList(
    entries: List<LibraryEntry>,
    listState: LazyListState,
    onOpen: (SearchItem) -> Unit,
    onEdit: (LibraryEntry) -> Unit,
    onRemove: (SearchItem) -> Unit,
    onFavorite: (SearchItem) -> Unit,
    onLater: (SearchItem) -> Unit
) {
    if (entries.isEmpty()) {
        EmptyLibraryState("这里还没有条目")
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(entries, key = { "${it.item.platform.name}:${it.item.id}" }) { entry ->
            LibraryEntryCard(
                entry = entry,
                onOpen = { onOpen(entry.item) },
                onEdit = { onEdit(entry) },
                onRemove = { onRemove(entry.item) },
                onFavorite = { onFavorite(entry.item) },
                onLater = { onLater(entry.item) }
            )
        }
    }
}

@Composable
private fun HistoryList(
    history: List<HistoryEntry>,
    listState: LazyListState,
    onOpen: (SearchItem) -> Unit,
    onRemove: (SearchItem) -> Unit
) {
    if (history.isEmpty()) {
        EmptyLibraryState("还没有浏览历史")
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(history, key = { "${it.item.platform.name}:${it.item.id}" }) { entry ->
            HistoryCard(
                entry = entry,
                onOpen = { onOpen(entry.item) },
                onRemove = { onRemove(entry.item) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LibraryEntryCard(
    entry: LibraryEntry,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onFavorite: () -> Unit,
    onLater: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (entry.item.summary.isNotBlank()) {
                Text(entry.item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${entry.item.platform.label} · ${entry.item.author.ifBlank { "未知作者" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.folder.isNotBlank() || entry.note.isNotBlank()) {
                Text(
                    listOf(entry.folder.takeIf { it.isNotBlank() }, entry.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onFavorite) { Text(if (entry.favorite) "已收藏" else "收藏") }
                TextButton(onClick = onLater) { Text(if (entry.later) "已稍后" else "稍后") }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onRemove) { Text("移除") }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(entry.item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.item.platform.label} · ${entry.item.author.ifBlank { "未知作者" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTimestamp(entry.visitedAt), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRemove) { Text("移除") }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntryDialog(entry: LibraryEntry, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var folder by remember(entry.item.platform, entry.item.id, entry.folder) { mutableStateOf(entry.folder) }
    var note by remember(entry.item.platform, entry.item.id, entry.note) { mutableStateOf(entry.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑条目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entry.item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(value = folder, onValueChange = { folder = it }, label = { Text("收藏夹") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, minLines = 3, maxLines = 5)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(folder, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddFolderDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建收藏夹") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("新增") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun writeExport(context: Context, scope: kotlinx.coroutines.CoroutineScope, uri: android.net.Uri, content: String) {
    scope.launch(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入导出文件")
        }.onSuccess {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "文件已保存", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun formatTimestamp(seconds: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000L))

private fun fileTimestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
