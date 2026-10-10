package com.twilight.calleditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAddScreen(busy: Boolean, onBack: () -> Unit, onSubmit: (List<CallEntry>) -> Unit) {
    val context = LocalContext.current
    val templatePrefs = remember { context.getSharedPreferences("batch_add_template", android.content.Context.MODE_PRIVATE) }
    val defaultEnd = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    val defaultStartText = remember { defaultEnd.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }
    val defaultEndText = remember { defaultEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }
    var numbers by rememberSaveable { mutableStateOf(templatePrefs.getString("numbers", "").orEmpty()) }
    var names by rememberSaveable { mutableStateOf(templatePrefs.getString("names", "").orEmpty()) }
    var types by rememberSaveable { mutableStateOf(templatePrefs.getString("types", "").orEmpty()) }
    var durations by rememberSaveable { mutableStateOf(templatePrefs.getString("durations", "").orEmpty()) }
    var randomDurationEnabled by rememberSaveable { mutableStateOf(templatePrefs.getBoolean("randomDurationEnabled", true)) }
    var randomNumberOrder by rememberSaveable { mutableStateOf(templatePrefs.getBoolean("randomNumberOrder", false)) }
    var randomDurationMinSeconds by rememberSaveable { mutableStateOf(templatePrefs.getString("randomDurationMinSeconds", "1").orEmpty()) }
    var randomDurationMaxSeconds by rememberSaveable { mutableStateOf(templatePrefs.getString("randomDurationMaxSeconds", "300").orEmpty()) }
    var callTimes by rememberSaveable { mutableStateOf(templatePrefs.getString("callTimes", "").orEmpty()) }
    var rangeStart by rememberSaveable { mutableStateOf(templatePrefs.getString("rangeStart", defaultStartText).orEmpty()) }
    var rangeEnd by rememberSaveable { mutableStateOf(templatePrefs.getString("rangeEnd", defaultEndText).orEmpty()) }
    var intervalMinSeconds by rememberSaveable { mutableStateOf(templatePrefs.getString("intervalMinSeconds", "60").orEmpty()) }
    var intervalMaxSeconds by rememberSaveable { mutableStateOf(templatePrefs.getString("intervalMaxSeconds", "90").orEmpty()) }
    var timeJitterSeconds by rememberSaveable { mutableStateOf(templatePrefs.getString("timeJitterSeconds", "30").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<CallEntry>?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    fun requestExit() { if (!busy) { if (preview != null) preview = null else onBack() } }
    fun saveTemplate() {
        templatePrefs.edit()
            .putString("numbers", numbers).putString("names", names)
            .putString("types", types).putString("durations", durations)
            .putBoolean("randomDurationEnabled", randomDurationEnabled)
            .putBoolean("randomNumberOrder", randomNumberOrder)
            .putString("randomDurationMinSeconds", randomDurationMinSeconds)
            .putString("randomDurationMaxSeconds", randomDurationMaxSeconds)
            .putString("callTimes", callTimes).putString("rangeStart", rangeStart)
            .putString("rangeEnd", rangeEnd).putString("intervalMinSeconds", intervalMinSeconds)
            .putString("intervalMaxSeconds", intervalMaxSeconds).putString("timeJitterSeconds", timeJitterSeconds)
            .putBoolean("keepTemplate", true).apply()
    }
    fun clearTemplate() {
        templatePrefs.edit().clear().apply()
        numbers = ""; names = ""; types = ""; durations = ""; callTimes = ""
        randomDurationEnabled = true; randomDurationMinSeconds = "1"; randomDurationMaxSeconds = "300"
        randomNumberOrder = false
        rangeStart = defaultStartText; rangeEnd = defaultEndText
        intervalMinSeconds = "60"; intervalMaxSeconds = "90"; timeJitterSeconds = "30"
    }
    LaunchedEffect(numbers, names, types, durations, randomDurationEnabled, randomNumberOrder, randomDurationMinSeconds, randomDurationMaxSeconds, callTimes, rangeStart, rangeEnd, intervalMinSeconds, intervalMaxSeconds, timeJitterSeconds) {
        saveTemplate()
    }
    BackHandler { requestExit() }

    fun parse(): List<CallEntry> {
        fun rows(value: String) = value.lines().map { it.trim() }
        val rawNumberRows = rows(numbers)
        val numberIndices = rawNumberRows.indices.filter { rawNumberRows[it].isNotEmpty() && !rawNumberRows[it].startsWith("#") }
        val numberRows = numberIndices.map { rawNumberRows[it] }
        require(numberRows.isNotEmpty()) { "请至少填写一个电话号码" }
        require(numberRows.size <= 5000) { "单次最多新增 5000 条记录" }

        fun aligned(value: String, field: String): List<String> {
            val result = rows(value)
            require(result.size <= numberRows.size) { "${field}的行数不能多于电话号码" }
            return result
        }
        val nameRows = rows(names)
        val typeRows = aligned(types, "类型")
        val durationRows = aligned(durations, "通话时长")
        val timeRows = aligned(callTimes, "通话时间")
        val rowOrder = numberRows.indices.toList().let { if (randomNumberOrder) it.shuffled() else it }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val zone = ZoneId.systemDefault()
        val startMillis = try { LocalDateTime.parse(rangeStart.trim(), formatter).atZone(zone).toInstant().toEpochMilli() }
            catch (_: Exception) { throw IllegalArgumentException("开始时间格式应为 yyyy-MM-dd HH:mm") }
        val endMillis = try { LocalDateTime.parse(rangeEnd.trim(), formatter).atZone(zone).toInstant().toEpochMilli() }
            catch (_: Exception) { throw IllegalArgumentException("结束时间格式应为 yyyy-MM-dd HH:mm") }
        require(startMillis <= endMillis) { "开始时间不能晚于结束时间" }
        val intervalMin = intervalMinSeconds.toLongOrNull()
        val intervalMax = intervalMaxSeconds.toLongOrNull()
        val jitter = timeJitterSeconds.toLongOrNull()
        val randomDurationMin = randomDurationMinSeconds.toLongOrNull()
        val randomDurationMax = randomDurationMaxSeconds.toLongOrNull()
        require(intervalMin != null && intervalMin in 1..31_536_000L) { "随机间隔最小值请输入 1 至 31536000 秒" }
        require(intervalMax != null && intervalMax in intervalMin..31_536_000L) { "随机间隔最大值不能小于最小值，且不能超过 31536000 秒" }
        require(jitter != null && jitter in 0..31_536_000L) { "通话时间随机偏移请输入 0 至 31536000 秒" }
        require(randomDurationMin != null && randomDurationMin in 0..31_536_000L) { "随机通话时长最小值请输入 0 至 31536000 秒" }
        require(randomDurationMax != null && randomDurationMax in randomDurationMin..31_536_000L) { "随机通话时长最大值不能小于最小值，且不能超过 31536000 秒" }
        var previousDate: Long? = null
        return rowOrder.mapIndexed { outputIndex, sourceIndex ->
            val number = numberRows[sourceIndex]
            val originalRow = numberIndices[sourceIndex]
            try {
                val name = nameRows.getOrNull(originalRow).orEmpty()
                val type = typeRows.getOrNull(originalRow)?.takeIf { it.isNotEmpty() }?.toInt() ?: 2
                val durationText = durationRows.getOrNull(originalRow).orEmpty()
                val duration = if (durationText.isNotEmpty()) durationText.toLongOrNull()
                    ?: throw IllegalArgumentException("通话时长必须填写非负整数秒")
                else if (randomDurationEnabled) kotlin.random.Random.nextLong(randomDurationMin!!, randomDurationMax!! + 1L)
                else 0L
                val timeText = timeRows.getOrNull(originalRow).orEmpty()
                require(duration >= 0L) { "通话时长不能小于 0 秒" }
                val date = if (timeText.isBlank()) {
                    // date is the call start time. When generating older entries in descending
                    // order, reserve the CURRENT call's duration before the newer entry, not the
                    // previous (newer) call's duration.
                    val intervalSeconds = kotlin.random.Random.nextLong(intervalMin!!, intervalMax!! + 1L)
                    val baseline = previousDate?.minus((duration + intervalSeconds) * 1000L) ?: endMillis
                    val jitterMillis = if (jitter!! == 0L) 0L else kotlin.random.Random.nextLong(jitter + 1L) * 1000L
                    val candidate = baseline - jitterMillis
                    val adjusted = previousDate?.let { prev ->
                        minOf(candidate, prev - duration * 1000L)
                    } ?: candidate
                    require(adjusted in startMillis..endMillis) {
                        "随机生成的通话时间超出所选范围；请扩大时间范围或减小随机间隔/偏移"
                    }
                    adjusted
                } else {
                    timeText.toLongOrNull() ?: try {
                        LocalDateTime.parse(timeText, formatter).atZone(zone).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        throw IllegalArgumentException("第 ${outputIndex + 1} 行通话时间格式错误，请填 yyyy-MM-dd HH:mm 或时间戳毫秒")
                    }
                }
                previousDate?.let { prev ->
                    require(prev - date >= duration * 1000L) {
                        "第 ${outputIndex + 1} 条记录与上一条的时间差不能小于本条通话时长（${duration} 秒）"
                    }
                }
                val entry = CallEntry(number = number, name = name, type = type, duration = duration, date = date)
                require(entry.validate() == null) { entry.validate().orEmpty() }
                require(number.isNotBlank()) { "电话号码不能为空" }
                previousDate = date
                entry
            } catch (e: Exception) {
                throw IllegalArgumentException("第 ${outputIndex + 1} 条记录：${e.message ?: "格式错误"}", e)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空批量新增内容？") },
            text = { Text("是否要清空内容？这会清除已填写的号码、姓名、类型、时长、通话时间以及相关设置。") },
            confirmButton = { TextButton(onClick = { clearTemplate(); error = null; preview = null; showClearDialog = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { MediumTopAppBar(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp)),
            colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(if (preview == null) "批量新增" else "预览批量新增") },
            actions = { TextButton(enabled = !busy, onClick = { showClearDialog = true }) { Text("清空") } }
        ) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (preview == null) {
                Text("每个文本框按行填写，第一行号码对应其他文本框第一行，以此类推；不需要输入分隔符。", style = MaterialTheme.typography.bodyMedium)
                Text("类型：1 呼入、2 呼出、3 未接、4 语音信箱、5 已拒接、6 已拦截、7 其他设备接听。类型留空默认呼出；通话时长留空时可按随机范围生成。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = numbers, onValueChange = { numbers = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("电话号码（每行一条，必填）") }, placeholder = { Text("13800138000\n10086") }, minLines = 3, enabled = !busy)
                OutlinedTextField(value = names, onValueChange = { names = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("姓名（可选，每行对应一个号码）") }, placeholder = { Text("张三\n中国移动") }, minLines = 3, enabled = !busy)
                OutlinedTextField(value = types, onValueChange = { types = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("通话类型（每行对应一个号码，可留空）") }, placeholder = { Text("2\n1") }, minLines = 3, enabled = !busy)
                OutlinedTextField(value = durations, onValueChange = { durations = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("通话时长（秒，每行对应一个号码，可留空随机生成）") }, placeholder = { Text("45\n留空则随机生成") }, minLines = 3, enabled = !busy)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = randomDurationEnabled, onCheckedChange = { randomDurationEnabled = it; error = null }, enabled = !busy)
                    Text("启用随机通话时长（仅对留空行生效）")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = randomNumberOrder, onCheckedChange = { randomNumberOrder = it; error = null }, enabled = !busy)
                    Text("随机号码顺序（号码、姓名、类型、时长和手动通话时间作为一组一起随机排序）")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = randomDurationMinSeconds, onValueChange = { randomDurationMinSeconds = it; error = null }, modifier = Modifier.weight(1f), label = { Text("随机时长最小值（秒）") }, singleLine = true, enabled = !busy && randomDurationEnabled)
                    OutlinedTextField(value = randomDurationMaxSeconds, onValueChange = { randomDurationMaxSeconds = it; error = null }, modifier = Modifier.weight(1f), label = { Text("随机时长最大值（秒）") }, singleLine = true, enabled = !busy && randomDurationEnabled)
                }
                Text("自动生成时间范围", style = MaterialTheme.typography.titleMedium)
                Text("自动生成时间从结束时间开始向更早排列；每条记录间隔为随机间隔加上一条记录的通话时长，再额外向更早方向随机偏移，因此间隔不会小于上一条通话时长。时间必须落在设定范围内。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = rangeStart, onValueChange = { rangeStart = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("通话时间范围开始") }, placeholder = { Text("2026-10-01 09:00") }, singleLine = true, enabled = !busy)
                OutlinedTextField(value = rangeEnd, onValueChange = { rangeEnd = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("通话时间范围结束") }, placeholder = { Text("2026-10-10 18:00") }, singleLine = true, enabled = !busy)
                OutlinedTextField(value = intervalMinSeconds, onValueChange = { intervalMinSeconds = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("记录间隔随机最小值（秒）") }, placeholder = { Text("60") }, singleLine = true, enabled = !busy)
                OutlinedTextField(value = intervalMaxSeconds, onValueChange = { intervalMaxSeconds = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("记录间隔随机最大值（秒）") }, placeholder = { Text("90") }, singleLine = true, enabled = !busy)
                OutlinedTextField(value = timeJitterSeconds, onValueChange = { timeJitterSeconds = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("通话时间向更早方向随机偏移上限（秒）") }, placeholder = { Text("30") }, singleLine = true, enabled = !busy)
                OutlinedTextField(value = callTimes, onValueChange = { callTimes = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("每条记录的通话时间（可留空自动生成）") }, placeholder = { Text("2026-10-10 17:00\n留空则按上方范围和间隔自动生成") }, minLines = 3, enabled = !busy)
                Text("通话时间可填写 yyyy-MM-dd HH:mm 或时间戳毫秒。以 # 开头的号码行会被忽略；其他文本框按对应行匹配。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !busy && numbers.isNotBlank(), onClick = {
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
