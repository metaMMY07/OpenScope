package dev.mediasearch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediasearch.SearchViewModel
import dev.mediasearch.core.Platform

internal fun highlight(text: String, keywords: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    keywords.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
        var offset = 0
        while (offset < text.length) {
            val index = text.indexOf(word, offset, ignoreCase = true)
            if (index < 0) break
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), index, index + word.length)
            offset = index + word.length
        }
    }
}

@Composable
fun TrendingSection(model: SearchViewModel, onSearch: (String) -> Unit) {
    val trends by model.trends.collectAsStateWithLifecycle()
    var platform by remember { mutableStateOf(Platform.BILIBILI) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(platform) { model.fetchTrending(platform) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("热搜", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { model.fetchTrending(platform, refresh = true) }) { Text("刷新") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Platform.entries.forEach { p -> FilterChip(selected = p == platform, onClick = { platform = p },
                label = { Text(p.label) }, leadingIcon = { PlatformLogo(p, 20.dp) }) }
        }
        val result = trends[platform]
        if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (result.words.isEmpty()) Text(result.message ?: "暂无热搜", style = MaterialTheme.typography.bodySmall)
        else {
            result.words.take(if (expanded) 20 else 3).forEachIndexed { index, word -> TextButton(onClick = { onSearch(word) }) { Text("${index + 1}  $word") } }
            if (result.words.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起热搜" else "更多热搜") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(model: SearchViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val clipboard = LocalClipboardManager.current
    val report = remember { model.diagnostics() }
    Scaffold(topBar = { TopAppBar(title = { Text("帮助与诊断") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("开始使用", style = MaterialTheme.typography.titleLarge) }
            item { Text("1. 在账号页用已有账号登录官方页面。\n2. 勾选搜索来源，输入关键词。抖音为可选实验来源。\n3. 每个来源先显示 3 条，点击更多继续。\n4. 收藏与稍后再看彼此独立，在内容库里编辑和备份。") }
            item { Text("搜索与平台限制", style = MaterialTheme.typography.titleLarge) }
            item { Text("筛选和排序只处理已加载内容，不额外发请求。单平台重搜取下一页未出现内容并替换该平台；更多向后追加。平台字段缺失时不猜测数值。\n\n遇到限流会暂停该来源 60 / 120 / 240 / 300 秒。验证码请在官方页面完成。抖音搜索和真实账号验证仍为实验能力。") }
            item { Text("诊断预览", style = MaterialTheme.typography.titleLarge) }
            item { Text("仅包含版本、系统 API、账号状态、结果数量及失败类型；不含 Cookie、搜索词、账号昵称或内容。不会自动上传。", style = MaterialTheme.typography.bodySmall) }
            item { Text(report, style = MaterialTheme.typography.bodySmall) }
            item { OutlinedButton(onClick = { clipboard.setText(AnnotatedString(report)); model.notify("诊断信息已复制") }) { Text("复制诊断信息") } }
        }
    }
}
