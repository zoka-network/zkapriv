package com.zoka.zkapriv

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/** Solo abre la vista de diseño: ninguna prueba accede al almacén ni envía pagos. */
class WalletRedesignInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun preview(screen: String, test: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("screen", "wallet-preview")
            .putExtra("scenario", screen)
            .putExtra("theme", "dark")
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitForIdle()
            test()
        }
    }

    @Test fun resetRequiresAcknowledgement() = preview("reset") {
        compose.onNodeWithText("REMOVE FROM THIS PHONE").assertIsNotEnabled()
        compose.onNodeWithText("I have the 24 words of every wallet").performClick()
        compose.onNodeWithText("REMOVE FROM THIS PHONE").assertIsEnabled().performClick()
        compose.onNodeWithText("CREATE WALLET").assertIsDisplayed()
    }

    @Test fun resetStaysDisabledDuringPayment() = preview("reset-sending") {
        compose.onNodeWithText("I have the 24 words of every wallet").performClick()
        compose.onNodeWithText("REMOVE FROM THIS PHONE").assertIsNotEnabled()
    }

    @Test fun forgotPinOpensReset() = preview("lock") {
        compose.onNodeWithText("Forgot your PIN?").assertIsDisplayed().performClick()
        compose.onNodeWithText("Start again on this phone").assertIsDisplayed()
        compose.onNodeWithText("REMOVE FROM THIS PHONE").assertIsNotEnabled()
    }

    @Test fun eighthAddressReusesTheReceiveCard() = preview("receive-eight") {
        compose.onNodeWithText("Main").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("#7").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("ADDRESS #7").assertIsDisplayed()
        compose.onNodeWithText("COPY").assertIsDisplayed()
        compose.onNodeWithText("SHARE").assertIsDisplayed()
    }

    @Test fun restoreAcceptsAPublicSamplePhraseAcrossTheCells() = preview("restore") {
        // Frase de prueba pública, nunca una billetera del usuario ni el portapapeles.
        val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(phrase)
        compose.onNodeWithText("24 of 24").assertExists()
        compose.onNodeWithText("RESTORE WALLET").assertIsEnabled().performClick()
        compose.onNodeWithText("Choose a 6-digit PIN").assertIsDisplayed()
    }
}
