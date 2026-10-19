package io.github.kesemb2.glasshome.ui

import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.ui.apps.CardSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CardSizeTest {

    @Test
    fun fiveColumnsKeepTheOriginalCards() {
        val size = CardSize(5)
        assertEquals(160f, size.width.value, 0.001f)
        assertEquals(90f, size.height.value, 0.001f)
    }

    @Test
    fun everySizeSpansTheContentWidth() {
        for (columns in CardSize.MIN_COLUMNS..CardSize.MAX_COLUMNS) {
            val size = CardSize(columns)
            val total = size.width * columns + size.gap * (columns - 1)
            assertEquals("columns $columns", 864f, total.value, 0.01f)
            assertEquals(size.width.value * 9f / 16f, size.height.value, 0.001f)
            assertEquals(size.height + 10.dp + 18.dp + 8.dp, size.rowPitch)
        }
    }

    @Test
    fun sizesAreValues() {
        assertEquals(CardSize(6), CardSize(6))
    }
}
