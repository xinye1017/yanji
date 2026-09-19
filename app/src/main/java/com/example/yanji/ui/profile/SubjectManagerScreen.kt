package com.example.yanji.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.Subject
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.YanjiDetailTopBar

object SubjectManagerTags {
    const val ADD_CATEGORY = "subject_manager_add_category"
    fun category(id: String) = "subject_category_$id"
    fun addChild(id: String) = "subject_add_child_$id"
    fun editRow(id: String) = "subject_edit_$id"
    fun deleteRow(id: String) = "subject_delete_$id"
    const val NAME_FIELD = "subject_name_field"
    const val CONFIRM = "subject_confirm"
}

/**
 * 学科管理：类别与其下辖子学科的增删改。
 *
 * 独立路由页（非底部弹窗），与「我的」页其它子页面保持一致的全屏导航体验。
 */
@Composable
fun SubjectManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = yanjiViewModel { container ->
        ProfileViewModel(container.repository)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SubjectManagerContent(
        subjects = state.subjects,
        onAddCategory = viewModel::addSubjectCategory,
        onAddSubSubject = viewModel::addSubSubject,
        onRename = viewModel::renameSubject,
        onDelete = viewModel::deleteSubject,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun SubjectManagerContent(
    subjects: List<Subject>,
    onAddCategory: (String) -> Unit,
    onAddSubSubject: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember(subjects) {
        subjects.filter { it.isCategory }.sortedBy { it.sortOrder }
    }
    val childrenOf = remember(subjects) {
        subjects.filter { it.parentId != null }.groupBy { it.parentId!! }
    }

    // 编辑弹窗状态：新增类别 / 新增子学科 / 重命名 共用一套输入框。
    var editing by remember { mutableStateOf<SubjectEditTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<Subject?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YanjiColors.groupedBackground)
    ) {
        YanjiDetailTopBar(
            title = "学科管理",
            subtitle = "类别下可继续添加子学科，专注时可选到子学科",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = YanjiSpacing.TightGap,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryRow(
                    category = category,
                    children = childrenOf[category.id].orEmpty().sortedBy { it.sortOrder },
                    onAddChild = { editing = SubjectEditTarget.AddChild(category.id, category.name) },
                    onRename = { editing = SubjectEditTarget.Rename(it) },
                    onDelete = { pendingDelete = it }
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(YanjiRadius.ButtonRadius))
                        .clickable { editing = SubjectEditTarget.AddCategory }
                        .padding(vertical = 14.dp)
                        .testTag(SubjectManagerTags.ADD_CATEGORY),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "新增学科类别",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    editing?.let { target ->
        SubjectNameDialog(
            title = when (target) {
                is SubjectEditTarget.AddCategory -> "新增学科类别"
                is SubjectEditTarget.AddChild -> "在「${target.parentName}」下新增子学科"
                is SubjectEditTarget.Rename -> "重命名学科"
            },
            initialValue = (target as? SubjectEditTarget.Rename)?.subject?.name.orEmpty(),
            onDismiss = { editing = null },
            onConfirm = { name ->
                when (target) {
                    is SubjectEditTarget.AddCategory -> onAddCategory(name)
                    is SubjectEditTarget.AddChild -> onAddSubSubject(target.parentId, name)
                    is SubjectEditTarget.Rename -> onRename(target.subject.id, name)
                }
                editing = null
            }
        )
    }

    pendingDelete?.let { subject ->
        val hasChildren = subject.isCategory && childrenOf[subject.id].orEmpty().isNotEmpty()
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${subject.name}」？") },
            text = {
                Text(
                    if (hasChildren) {
                        "该类别下的子学科会一并删除。已有的学习记录不会被删除，但统计里会归入「其他」。"
                    } else {
                        "已有的学习记录不会被删除，但统计里会归入「其他」。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(subject.id)
                        pendingDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CategoryRow(
    category: Subject,
    children: List<Subject>,
    onAddChild: () -> Unit,
    onRename: (Subject) -> Unit,
    onDelete: (Subject) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YanjiRadius.StandardCardRadius))
            .background(YanjiColors.fill)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(parseHexColor(category.colorHex))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = YanjiColors.primaryLabel,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onAddChild,
                modifier = Modifier.testTag(SubjectManagerTags.addChild(category.id))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "新增子学科",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { onRename(category) },
                modifier = Modifier.testTag(SubjectManagerTags.editRow(category.id))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "重命名",
                    tint = YanjiColors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = { onDelete(category) },
                modifier = Modifier.testTag(SubjectManagerTags.deleteRow(category.id))
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = YanjiColors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (children.isEmpty()) {
            Text(
                text = "暂无子学科",
                style = MaterialTheme.typography.labelMedium,
                color = YanjiColors.textTertiary,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 4.dp)
            )
        } else {
            children.forEach { child ->
                HorizontalDivider(
                    color = YanjiColors.separator,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = YanjiColors.secondaryLabel,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRename(child) },
                        modifier = Modifier.testTag(SubjectManagerTags.editRow(child.id))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "重命名",
                            tint = YanjiColors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDelete(child) },
                        modifier = Modifier.testTag(SubjectManagerTags.deleteRow(child.id))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "删除",
                            tint = YanjiColors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectNameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val trimmed = text.trim()
    val isValid = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("请输入名称") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SubjectManagerTags.NAME_FIELD)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = isValid,
                modifier = Modifier.testTag(SubjectManagerTags.CONFIRM)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private sealed interface SubjectEditTarget {
    data object AddCategory : SubjectEditTarget
    data class AddChild(val parentId: String, val parentName: String) : SubjectEditTarget
    data class Rename(val subject: Subject) : SubjectEditTarget
}

/** 把 `#RRGGBB` 解析成 Compose Color；非法值回落到主题主色。 */
private fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return Color(0xFF667085)
    return runCatching {
        Color(cleaned.toLong(16) or 0xFF000000L)
    }.getOrDefault(Color(0xFF667085))
}
