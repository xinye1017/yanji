package com.example.yanji.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.YanjiPrimary
import com.example.yanji.theme.YanjiPrimarySoft
import com.example.yanji.theme.YanjiPrimaryStrong
import com.example.yanji.theme.YanjiRadius

@Composable
fun UserMessageBubble(
    content: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = YanjiRadius.MessageRadius,
                topEnd = 4.dp,
                bottomStart = YanjiRadius.MessageRadius,
                bottomEnd = YanjiRadius.MessageRadius
            ),
            color = YanjiPrimarySoft,
            modifier = Modifier
                .widthIn(max = 280.dp) // 最大宽度 280dp
                .wrapContentSize()
        ) {
            Text(
                text = content,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    color = YanjiPrimaryStrong,
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}