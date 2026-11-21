package com.zoka.zkapriv

import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zoka.zkapriv.ui.avatar.AvatarMosaic
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.floor

/** Only the debug fixture: never opens a real wallet or reads keys. */
class AvatarInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun preview(screen: String, block: (ActivityScenario<MainActivity>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("screen", "wallet-preview").putExtra("scenario", screen).putExtra("theme", "dark"))
            .use { compose.waitForIdle(); block(it) }
    }

    @Test fun draws81FlatSquaresAt40And64And80Dp() = preview("avatar-sizes") {
        val expected = AvatarMosaic.generate("public-account:alice")!!
        listOf(40, 64, 80).forEach { dp ->
            val bitmap = compose.onNodeWithTag("avatar-$dp").assertIsDisplayed().captureToImage()
            val pixels = bitmap.toPixelMap()
            val side = bitmap.width.toFloat()
            assertEquals(bitmap.width, bitmap.height)
            val padding = floor(side * .045f).coerceAtLeast(1f)
            val gap = floor(side * .014f).coerceAtLeast(1f)
            val pitch = (side - 2 * padding + gap) / 9
            expected.forEachIndexed { index, paletteIndex ->
                val x = (padding + (index % 9 + .5f) * pitch - gap / 2).toInt()
                val y = (padding + (index / 9 + .5f) * pitch - gap / 2).toInt()
                assertEquals("$dp dp square $index", AvatarMosaic.palette[paletteIndex].toInt(), pixels[x, y].toArgb())
            }
            assertEquals(AvatarMosaic.BACKGROUND.toInt(), pixels[0, 0].toArgb())
        }
    }

    @Test fun recreationKeepsExactlyTheSameImage() = preview("avatar-sizes") { scenario ->
        val before = compose.onNodeWithTag("avatar-64").captureToImage().toPixelMap()
        scenario.recreate()
        compose.waitForIdle()
        val after = compose.onNodeWithTag("avatar-64").captureToImage().toPixelMap()
        for (y in 0 until before.height) for (x in 0 until before.width) {
            assertEquals(before[x, y], after[x, y])
        }
    }

    @Test fun emptyIdentifierShowsExplicitPlaceholder() = preview("avatar-sizes") {
        compose.onNodeWithTag("avatar-empty").assertContentDescriptionEquals("Account avatar unavailable")
    }

    @Test fun dashboardFitsThePhoneWithoutClippingNetworkValues() = preview("home") {
        listOf("1,284,567", "3.48 GH/s", "100%", "29 bits").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        listOf("Wallet", "Receive", "Send", "Activity", "Settings").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
    }

    @Test fun hidingBalanceAlsoHidesTheChartAndChange() = preview("home") {
        compose.onNodeWithContentDescription("Hide balance").performClick()
        compose.onNodeWithContentDescription("Recent balance chart").assertDoesNotExist()
        compose.onNodeWithText("Balance hidden").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show balance").performClick()
        compose.onNodeWithContentDescription("Recent balance chart").assertIsDisplayed()
    }

    @Test fun selectingAnotherAccountChangesItsAvatarAndGoingBackRestoresIt() = preview("home") {
        fun pattern() = compose.onNodeWithTag("home-avatar", useUnmergedTree = true)
            .captureToImage().toPixelMap().let { pixels ->
                List(pixels.height) { y -> List(pixels.width) { x -> pixels[x, y].toArgb() } }
            }
        val main = pattern()
        compose.onNodeWithContentDescription("Change wallet").performClick()
        compose.onNodeWithText("Savings").performClick()
        compose.onNodeWithText("Savings").assertIsDisplayed()
        assertNotEquals(main, pattern())
        compose.onNodeWithContentDescription("Change wallet").performClick()
        compose.onNodeWithText("Main Wallet").performClick()
        assertEquals(main, pattern())
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
