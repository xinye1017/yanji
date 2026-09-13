package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.*

/**
 * 研迹主要行动按钮（Primary Button）。
 * 蓝底白字、圆角 12dp。
 */
@Composable
fun YanjiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = YanjiPrimary,
            contentColor = YanjiOnPrimary,
            disabledContainerColor = YanjiPrimarySoft,
            disabledContentColor = YanjiTextTertiary
        ),
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 研迹次要操作按钮（Secondary Button）。
 * 浅蓝底或白底描边，文字为深蓝/品牌色。
 */
@Composable
fun YanjiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = YanjiPrimarySoft,
            contentColor = YanjiPrimaryStrong,
            disabledContainerColor = YanjiSurfaceSoft,
            disabledContentColor = YanjiTextTertiary
        ),
        border = BorderStroke(1.dp, YanjiBorder),
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
