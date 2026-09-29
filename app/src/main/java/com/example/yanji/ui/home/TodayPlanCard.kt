package com.example.yanji.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.data.StudyTask
import com.example.yanji.data.Subject
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.yanjiSubjectColorOf
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiProgressBar
import com.example.yanji.ui.icons.RemixIcons

/**
 * 计划可选的时长档位。
 *
 * 取值与 [com.example.yanji.data.FocusModes] 的四个倒计时档位对齐：
 * 用户在「今日计划」里写下的 45 分钟，和在专注准备页选的「45分钟深度」是同一个长度。
 */
private val PlannedDurationOptions = listOf(25, 45, 60, 90)

/**
 * 完成圈直径。分割线要用它内缩到文字列起点，因此不能只写在 [TaskCheckCircle] 里。
 */
private val TaskCircleSize = 22.dp

/**
 * 勾选圈的触控尺寸。
 *
 * 取 40dp 而非 48dp：Material 自己的 Checkbox 正是「20dp 视觉 + 48dp 触控」，
 * 这里沿用同一范式；选 40 是因为**溢出量必须小于行的上下内边距（10dp）**，
 * 否则相邻两行的命中区会纵向重叠，勾上一行却把下面一行也带走。
 * 40 - 22 = 18，溢出 9dp < 10dp，两行互不侵占。
 * 40dp 也远高于 WCAG 2.5.5 (AAA) 的 24dp 下限与 2.5.8 (AA) 的要求。
 */
private val TaskTouchTargetSize = 40.dp

/**
 * 触控框比视觉圆圈每侧多出来的一半（(40-22)/2 = 9dp）。
 *
 * 行的左内边距要减去它，圆圈的**视觉**左缘才会与卡片文字左缘对齐；
 * 触摸区则正好铺到内边距边界，不越出卡片。
 *
 * 9dp 的溢出量还必须小于行高里 8+8dp 的上下内边距之和，勾选圈才不会在纵向
 * 侵入相邻两行的命中区（否则会勾上一行却把下一行也划掉）。
 */
private val TaskTouchTargetOverhang = (TaskTouchTargetSize - TaskCircleSize) / 2

/**
 * 首页「今日计划」卡片。
 *
 * ## 为什么搬到首页
 *
 * 今日计划是**意图**（今天打算做什么），首页的「今日专注学习」是**结果**（实际做了多久）——
 * 同一件事的两面。此前意图侧留在专注准备页，结果侧在首页，开屏时看到的是结果，
 * 「今天要做什么」要切 tab 才找得到。首页一天要回答的第一个问题是「今天做什么」，
 * 而不是「我现在选哪门课」。
 *
 * ## 为什么是分组列表而不是一叠任务卡
 *
 * 早期实现给每条任务单独一张 [YanjiCard]。首页已有倒计时、打卡、今日专注、模考四张卡，
 * 再叠 N 张任务卡会退化成「卡片墙」：每条任务各带描边与内边距，行与行之间没有视觉分组，
 * 用户得靠边框去找「一件事」的边界。
 *
 * 现在与同页「模考看板」保持同一种 iOS Grouped Inset 结构：一张容器 + 发丝分行，
 * 行间靠 [YanjiColors.rowDivider] 而非边框区分。层级交给字号字重与学科色承担，容器本身保持安静。
 *
 * ## 三处刻意的取舍
 *
 * 1. **删除从行内图标移到长按菜单。** 原设计每行右侧挂垃圾桶，与「开始」并排，
 *    两个形状相近的控件挤在 48dp 内，误触成本高；删除又是低频破坏性操作，
 *    不该占据每天都要扫视的高频路径。长按菜单复用本项目已有的单指针替代路径
 *    （见 `NoteSwipeableRow`），不新造交互范式。
 * 2. **整行可点即开始专注。** 原本「开始」是文字按钮，与勾选、删除在同一行里争抢点击目标；
 *    改成整行点击（完成态除外）后，一行只剩两个交互单元：勾选完成、整行开始。
 * 3. **删掉「完成 2 / 5」数字。** 它与进度条、与状态文案表达同一件事，同屏三份计数纯属冗余。
 *    计数交给进度条，状态交给一行可执行的文字。
 */
@Composable
internal fun TodayPlanCard(
    tasks: List<StudyTask>,
    subjects: List<Subject>,
    onAdd: (Subject, String, Int) -> Unit,
    onToggle: (StudyTask) -> Unit,
    onDelete: (String) -> Unit,
    onStart: (StudyTask) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    YanjiCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_today_plan_card"),
        variant = YanjiCardVariant.Grouped
    ) {
        Column(modifier = Modifier.padding(vertical = YanjiSpacing.CardPaddingCompact)) {
            TodayPlanHeader(onAdd = { showAddDialog = true })

            if (tasks.isEmpty()) {
                EmptyPlanHint()
            } else {
                val completed = tasks.count { it.isCompleted }
                PlanProgressSummary(completed = completed, total = tasks.size)
                Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))
                YanjiProgressBar(
                    progress = completed.toFloat() / tasks.size,
                    // 全部完成时转绿，与首页「达成 100%」用同一个 success 语义色。
                    color = if (completed == tasks.size) {
                        YanjiColors.success
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    height = YanjiSpacing.TightGap
                )

                tasks.forEachIndexed { index, task ->
                    if (index > 0) {
                        // 分割线内缩到**文字列**的起点，与标题左缘严格对齐。
                        // 若只对齐卡片内边距，线会从勾选圈下方横穿过去，
                        // 视觉上像在给上一行的标题划删除线。
                        // 起点按行内实际排版推：左内边距(含溢出回补) + 触控框 + 间隙。
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = (YanjiSpacing.CardPaddingCompact - TaskTouchTargetOverhang) +
                                    TaskTouchTargetSize +
                                    YanjiSpacing.ItemGap
                            ),
                            thickness = 0.6.dp,
                            color = YanjiColors.rowDivider
                        )
                    }
                    StudyTaskRow(
                        task = task,
                        onToggle = { onToggle(task) },
                        onStart = { onStart(task) },
                        onDelete = { onDelete(task.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        StudyTaskDialog(
            subjects = subjects,
            onDismiss = { showAddDialog = false },
            onConfirm = { subject, title, minutes ->
                onAdd(subject, title, minutes)
                showAddDialog = false
            }
        )
    }
}

/**
 * 卡片头：图标 + 标题 + 添加按钮。
 *
 * 靶心图标（[RemixIcons.TargetLine]）表达「今天要打到哪」，与相邻「每日打卡」的
 * 火焰图标形成同一套「图标即卡片主题」的读法 —— 用户扫过首页时，图标比文字更快
 * 认出这张卡是关于计划的。沿用打卡卡的 32dp 色调圆底 + 18dp 图标写法，
 * 两张相邻卡因此出自同一套排版规则。
 *
 * 取 Line 而非 Fill：靶心的三圈同心圆在 18dp 下，Fill 会糊成一个实心点。
 *
 * 标题用 `titleMedium`（16sp）但字重取 [FontWeight.Bold]，与「每日打卡」一致 ——
 * 两条卡片标题同字号同字重，扫首页时是同一层级的两件事，而不是一主一次。
 * 副标题状态文案由 [PlanProgressSummary] 承担。
 */
@Composable
private fun TodayPlanHeader(onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YanjiSpacing.CardPaddingCompact, vertical = YanjiSpacing.ItemGapSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = RemixIcons.TargetLine,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "今日计划",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        AddPlanButton(onClick = onAdd)
    }
}

/** 添加按钮：调性容器 + 主色图标，比裸 TextButton 更容易在扫视中被找到。 */
@Composable
private fun AddPlanButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(YanjiRadius.Small),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = RemixIcons.AddLine,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "添加",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** 空态：不是禁用态控件，而是把这张卡变成一个明确的行动入口。 */
@Composable
private fun EmptyPlanHint() {
    Text(
        text = "写下今天要完成的事，之后可以从这里直接开始专注。",
        modifier = Modifier.padding(
            horizontal = YanjiSpacing.CardPaddingCompact,
            vertical = YanjiSpacing.ItemGapSmall
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 进度区：一句状态 + 一条与全站共用的胶囊进度条。
 *
 * 措辞按「还剩几件」组织，因为待办的用户问题是「现在该做哪一件」，
 * 不是「我已经完成多少」。全部完成时改用 success 语义色，与首页进度条达成 100% 时一致。
 */
@Composable
private fun PlanProgressSummary(
    completed: Int,
    total: Int
) {
    val remaining = total - completed
    Text(
        text = when {
            remaining == 0 -> "今天的事都做完了"
            completed == 0 -> "还有 $remaining 件待完成"
            else -> "还剩 $remaining 件，已完成 $completed 件"
        },
        modifier = Modifier.padding(horizontal = YanjiSpacing.CardPaddingCompact),
        style = MaterialTheme.typography.bodySmall,
        color = if (remaining == 0) YanjiColors.success else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 单条计划行。
 *
 * 从左到右：完成圈 → 标题 / 学科·时长 → 开始图标。
 * 标题用 weight 自适应并在过长时省略，保证右侧的开始图标永远不被挤走。
 *
 * 整行可点 = 开始专注，**包括已完成的那一行**。
 * 早先只在未完成时可点，导致已完成行变成一块「按了没反应」的死区：
 * 读屏仍会念出「可双击激活」，视觉上又有开始图标，点了却什么都不发生。
 * 完成后想再练一遍同样合理，因此统一为可点，完成状态由「标题变淡 + 圆圈填色」表达。
 *
 * `onClickLabel` 把「开始专注」挂到整行的可点击语义上，而不是挂在那个 Play 图标上 ——
 * 图标本身不是可聚焦的点击目标，给它 contentDescription 只会多出一个念得到、按不动的节点。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudyTaskRow(
    task: StudyTask,
    onToggle: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val subjectColor = yanjiSubjectColorOf(task.subjectName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClick = onStart,
                onClickLabel = "开始专注",
                onLongClick = { menuOpen = true },
                onLongClickLabel = "更多操作"
            )
            // 行的左内边距扣掉触控框比视觉圆圈多出来的一半，
            // 使 22dp 圆圈本身的左缘仍与卡片文字左缘对齐；
            // 触摸区则刚好铺满内边距，不越出卡片。
            .padding(
                start = YanjiSpacing.CardPaddingCompact - TaskTouchTargetOverhang,
                end = YanjiSpacing.CardPaddingCompact,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 勾选圈：视觉 22dp 圆画在 40dp 的方盒正中 —— 盒子本身即命中区
        // （沿用 Material「小图标 + 大命中区」的范式），圆圈因此不需要任何
        // 溢出或负偏移的技巧，版面与命中区严格一致。
        TaskCheckCircle(
            checked = task.isCompleted,
            subjectColor = subjectColor,
            title = task.title,
            onToggle = onToggle,
            modifier = Modifier.size(TaskTouchTargetSize)
        )
        Spacer(modifier = Modifier.width(YanjiSpacing.ItemGap))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (task.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(YanjiSpacing.TightGap))
            Text(
                text = "${task.subjectName} · ${task.plannedMinutes} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = YanjiColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 「开始」用图标而非文字按钮：它与勾选圈、标题并排时，文字会挤压标题，
        // 而 Play 图标在 56dp 高的行里已足够表意。已完成的行降为静默图标，
        // 表示「已完成，再练一遍也可以」，而不是一个消失了的能力。
        Icon(
            imageVector = RemixIcons.PlayFill,
            contentDescription = null,
            tint = if (task.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(18.dp)
        )

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = { Icon(RemixIcons.DeleteBinLine, contentDescription = null) },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}

/**
 * 完成圈。
 *
 * 不用 M3 `Checkbox`：它默认是 20dp 方形，在 56dp 高的行里视觉重量过大，
 * 且「已勾选」态是主色方块，会和学科色点抢注意力。
 *
 * 这里用 22dp 圆形圈：未完成时描学科色、已完成时填学科色 ——
 * 于是「这行属于哪门课」与「做没做完」由同一个形状一次讲完，
 * 不必再单独放一个学科色点。描边走 `outline` 槽位（专职 ≥3:1 的控件边界，WCAG 1.4.11）。
 *
 * 交互用 [Modifier.toggleable] 而不是 `clickable`：配上 `Role.Checkbox` 与
 * `ToggleableState` 之后，读屏才会念出「已勾选 / 未勾选」。只给 `clickable` 挂一个
 * Checkbox 角色，勾选状态是念不出来的 —— 视障用户将无法知道这件计划到底做完没有。
 *
 * 视觉 22dp，触控 48dp：勾选圈是这条行里**唯一**的独立点击目标，
 * 22dp 的可点面积达不到 WCAG 2.5.8 的最小目标尺寸。因此外层套一个 48dp 的透明方框承载
 * toggleable，圆圈本身居中绘制。行高本来就 ≥56dp，多出来的透明区域不会挤压文字，
 * 也不会和整行的点击区产生「按到哪」的分歧：内层先消费事件。
 */
@Composable
private fun TaskCheckCircle(
    checked: Boolean,
    subjectColor: Color,
    title: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 勾选圈不画水波纹：同一行里整行已经可点，再叠一层涟漪会让人分不清点击层级。
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .toggleable(
                value = checked,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null
            )
            .semantics { contentDescription = "「$title」" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(TaskCircleSize)
                .clip(CircleShape)
                .background(if (checked) subjectColor else MaterialTheme.colorScheme.surface)
                .then(
                    if (checked) {
                        Modifier
                    } else {
                        Modifier.border(1.5.dp, subjectColor, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = RemixIcons.CheckLine,
                    contentDescription = null,
                    // 勾号压在学科色填充上，需要的是「反色」而非固定主色，
                    // 否则深色学科上会与背景糊在一起。
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * 添加今日计划。
 *
 * 三段式：目标 → 科目 → 时长。科目用 FlowRow 药丸而不是下拉 ——
 * 可选科目在十几条量级，下拉要多一次点击且看不到全貌，药丸一屏铺开更好扫。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyTaskDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onConfirm: (Subject, String, Int) -> Unit
) {
    val selectableSubjects = remember(subjects) {
        subjects.filter { subject ->
            subject.enabled && (
                subject.parentId != null ||
                    subjects.none { it.enabled && it.parentId == subject.id }
                )
        }.sortedBy { it.sortOrder }
    }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedSubjectId by rememberSaveable(selectableSubjects) {
        mutableStateOf(selectableSubjects.firstOrNull()?.id.orEmpty())
    }
    var selectedMinutes by rememberSaveable { mutableIntStateOf(45) }
    val selectedSubject = selectableSubjects.firstOrNull { it.id == selectedSubjectId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加今日计划",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 60) title = it },
                    label = { Text("准备完成什么？") },
                    placeholder = { Text("例如：二次型 30 道题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.InputRadius)
                )

                Text(
                    text = "科目",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectableSubjects.forEach { subject ->
                        val selected = subject.id == selectedSubjectId
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(YanjiRadius.Small))
                                .clickable { selectedSubjectId = subject.id },
                            shape = RoundedCornerShape(YanjiRadius.Small),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = subject.name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "预计时长",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlannedDurationOptions.forEach { minutes ->
                        val selected = selectedMinutes == minutes
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(YanjiRadius.Small))
                                .clickable { selectedMinutes = minutes },
                            shape = RoundedCornerShape(YanjiRadius.Small),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = "${minutes}m",
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedSubject?.let { onConfirm(it, title.trim(), selectedMinutes) } },
                enabled = title.isNotBlank() && selectedSubject != null
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(YanjiRadius.DialogRadius),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
