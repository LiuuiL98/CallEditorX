package com.twilight.calleditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAddScreen(busy: Boolean, onBack: () -> Unit, onSubmit: (List<CallEntry>) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<CallEntry>?>(null) }
    BackHandler { if (!busy) { if (preview != null) preview = null else onBack() } }

    fun parse(): List<CallEntry> {
        val lines = input.lines().mapIndexedNotNull { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) null else (index + 1) to line
        }
        require(lines.isNotEmpty()) { "请至少输入一条记录" }
        require(lines.size <= 5000) { "单次最多新增 5000 条记录" }
        val now = System.currentTimeMillis()
        return lines.mapIndexed { index, (lineNo, line) ->
            try {
                val parts = line.split("|").map { it.trim() }
                require(parts.size <= 5) { "字段过多，请使用竖线 | 分隔" }
                val number = parts.getOrNull(0).orEmpty()
                val name = parts.getOrNull(1).orEmpty()
                val type = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toInt() ?: 2
                val duration = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toLong() ?: 0L
                val date = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }?.toLong() ?: (now - index * 60_000L)
                CallEntry(number = number, name = name, type = type, duration = duration, date = date).also {
                    require(it.validate() == null) { it.validate().orEmpty() }
                    require(number.isNotBlank()) { "电话号码不能为空" }
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("第 $lineNo 行：${e.message ?: "格式错误"}", e)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MediumTopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(if (preview == null) "批量新增" else "预览批量新增") },
            navigationIcon = { TextButton(enabled = !busy, onClick = { if (preview != null) preview = null else onBack() }) { Text("返回") } }
        ) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (preview == null) {
                Text("每行一条记录，使用竖线 | 分隔字段。只需填写号码，其余字段可省略。", style = MaterialTheme.typography.bodyMedium)
                Text("格式：号码 | 姓名 | 类型 | 时长秒 | 时间戳毫秒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("类型：1 呼入、2 呼出、3 未接、4 语音信箱、5 已拒接、6 已拦截、7 其他设备接听。默认类型为呼出，时长默认为 0 秒。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = input, onValueChange = { input = it; error = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    label = { Text("批量记录") },
                    placeholder = { Text("13800138000 | 张三 | 2 | 45\n10086 | 中国移动 | 1 | 0") },
                    minLines = 8, enabled = !busy
                )
                Text("未填写时间戳时，记录从当前时间开始，每行间隔一分钟。以 # 开头的行会被忽略。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !busy && input.isNotBlank(), onClick = {
                    try { preview = parse(); error = null } catch (e: Exception) { error = e.message ?: "无法解析输入" }
                }, modifier = Modifier.fillMaxWidth()) { Text("校验并预览") }
            } else {
                val items = preview!!
                Text("准备新增 ${items.size} 条记录", style = MaterialTheme.typography.headlineSmall)
                Text("日期范围：${dateLabel(items.minOf { it.date }, "yyyy-MM-dd HH:mm")} 至 ${dateLabel(items.maxOf { it.date }, "yyyy-MM-dd HH:mm")}")
                Text("请确认号码、姓名、类型与时间正确。写入时会跳过与现有系统记录完全相同的项目，不会清空已有记录。", style = MaterialTheme.typography.bodyMedium)
                items.take(20).forEachIndexed { i, item ->
                    ListItem(
                        headlineContent = { Text(item.name.ifBlank { item.number }) },
                        supportingContent = { Text("${item.number} · ${typeLabel(item.type)} · ${item.duration} 秒") },
                        trailingContent = { Text(dateLabel(item.date, "MM-dd HH:mm"), style = MaterialTheme.typography.labelSmall) }
                    )
                    if (i < minOf(items.size, 20) - 1) HorizontalDivider()
                }
                if (items.size > 20) Text("另有 ${items.size - 20} 条未显示。", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy, onClick = { onSubmit(items) }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "正在写入…" else "确认新增 ${items.size} 条") }
                OutlinedButton(enabled = !busy, onClick = { preview = null }, modifier = Modifier.fillMaxWidth()) { Text("返回修改") }
            }
        }
    }
}
