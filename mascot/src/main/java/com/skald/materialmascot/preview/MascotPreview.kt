package com.skald.materialmascot.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.skald.materialmascot.MaterialMascot
import com.skald.materialmascot.MascotConfig
import com.skald.materialmascot.MascotExpression
import com.skald.materialmascot.MascotStyle

@Preview(showBackground = true)
@Composable
fun MaterialMascotPreview() {
    MaterialTheme {
        MaterialMascot(
            config = MascotConfig(
                style = MascotStyle.BOOK_WISP,
                expression = MascotExpression.HAPPY,
                motion = false
            )
        )
    }
}
