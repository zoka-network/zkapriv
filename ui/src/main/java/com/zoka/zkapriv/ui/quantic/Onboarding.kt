package com.zoka.zkapriv.ui.quantic

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.Eyebrow
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.QuanticWordmark
import com.zoka.zkapriv.ui.components.SectionTitle
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZInput
import com.zoka.zkapriv.ui.components.ZToggle
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk

// ── Bienvenida ───────────────────────────────────────────────────────────────

@Composable
fun WelcomeScreen(onCreate: () -> Unit, onRestore: () -> Unit) {
    ZkScreen(
        title = null,
        bottom = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZButton(onCreate, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large) {
                    ZButtonText("Create wallet")
                }
                ZButton(onRestore, Modifier.fillMaxWidth(), ZButtonVariant.Accent, ZButtonSize.Large) {
                    ZButtonText("Restore with 24 words")
                }
            }
        },
    ) {
        VSpace(56)
        Eyebrow("ZKAPriv · Testnet v3")
        QuanticWordmark(Zk.text.brandHero)
        Text("Private by design.", style = Zk.text.brandTagline, color = Zk.colors.textMuted)
        VSpace(8)
        Text(
            "A post-quantum private wallet for Zoka Quantic. Your keys and every proof stay on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = Zk.colors.textMuted,
        )
        VSpace(8)
        Notice(
            "This is the testnet: its coins are for testing and have no value.",
            title = "Test coins, no value",
            tone = ZkTone.Warn,
        )
    }
}

// ── Respaldo de la frase ─────────────────────────────────────────────────────

@Composable
fun BackupPhraseScreen(words: List<String>, onBack: () -> Unit, onContinue: () -> Unit) {
    var written by remember { mutableStateOf(false) }
    ZkScreen(
        title = "Recovery phrase",
        onBack = onBack,
        bottom = {
            ZButton(onContinue, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large, enabled = written) {
                ZButtonText("I wrote them down")
            }
        },
    ) {
        SectionTitle(
            "Step 1 of 3",
            "Write down these 24 words",
            "In this order, on paper. They are the only way to recover this wallet, here or in ZSilent.",
        )
        Notice(
            "Anyone with these words can spend your coins. Never type them into a website or share a photo of them.",
            title = "Keep them secret",
            tone = ZkTone.Err,
        )
        WordGrid(words)
        Row(
            Modifier.fillMaxWidth().clickable { written = !written }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "I wrote the 24 words on paper",
                style = MaterialTheme.typography.bodyLarge,
                color = Zk.colors.text,
                modifier = Modifier.weight(1f),
            )
            ZToggle(written, { written = it }, description = "I wrote the words down")
        }
    }
}

@Composable
fun WordGrid(words: List<String>) {
    val half = (words.size + 1) / 2
    Panel(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0 until half, half until words.size).forEach { range ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    range.forEach { i ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%02d".format(i + 1),
                                style = Zk.text.mono,
                                color = Zk.colors.textMuted,
                                modifier = Modifier.width(30.dp),
                            )
                            Text(words[i], style = Zk.text.mono, color = Zk.colors.text)
                        }
                    }
                }
            }
        }
    }
}

// ── Verificación ─────────────────────────────────────────────────────────────

/** Tres palabras al azar, cada una entre seis opciones. */
@Composable
fun VerifyPhraseScreen(words: List<String>, onBack: () -> Unit, onVerified: () -> Unit) {
    val questions = remember(words) { words.indices.shuffled().take(3).sorted() }
    var step by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf<String?>(null) }
    val position = questions[step]
    val options = remember(step) {
        (words.filterIndexed { i, _ -> i != position }.distinct().shuffled().take(5) + words[position]).shuffled()
    }
    ZkScreen(title = "Check your phrase", onBack = onBack) {
        SectionTitle("Step 2 of 3", "Which is word #${position + 1}?", "Question ${step + 1} of 3.")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        val isWrong = wrong == option
                        ZButton(
                            onClick = {
                                if (option == words[position]) {
                                    wrong = null
                                    if (step == questions.lastIndex) onVerified() else step++
                                } else {
                                    wrong = option
                                }
                            },
                            modifier = Modifier.weight(1f),
                            variant = if (isWrong) ZButtonVariant.Danger else ZButtonVariant.Default,
                        ) { ZButtonText(option) }
                    }
                }
            }
        }
        if (wrong != null) ErrorText("That is not word #${position + 1}. Check your paper.")
    }
}

// ── Restaurar ────────────────────────────────────────────────────────────────

@Composable
fun RestoreScreen(validate: (String) -> Boolean, onBack: () -> Unit, onRestored: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val count = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    ZkScreen(
        title = "Restore",
        onBack = onBack,
        bottom = {
            ZButton(
                onClick = {
                    if (validate(text)) onRestored(text) else error =
                        "These are not 24 valid words. Check spelling and order."
                },
                modifier = Modifier.fillMaxWidth(),
                variant = ZButtonVariant.Primary,
                size = ZButtonSize.Large,
                enabled = count == 24,
            ) { ZButtonText("Restore wallet") }
        },
    ) {
        SectionTitle(
            "Step 1 of 2",
            "Type your 24 words",
            "The recovery phrase of this wallet or of a ZSilent wallet, separated by spaces.",
        )
        ZInput(
            value = text,
            onValueChange = {
                text = it.lowercase()
                error = null
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            placeholder = "word1 word2 word3 …",
            singleLine = false,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Password,
            ),
            isError = error != null,
        )
        HintText("$count of 24 words")
        if (error != null) ErrorText(error!!)
        Notice(
            "The wallet reads the chain from its first block, so the first sync takes a little longer.",
            tone = ZkTone.Info,
        )
    }
}

// ── PIN ──────────────────────────────────────────────────────────────────────

@Composable
fun PinSetupScreen(onBack: () -> Unit, onPinSet: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    LaunchedEffect(entry) {
        if (entry.length < QuanticVault.PIN_LENGTH) return@LaunchedEffect
        val previous = first
        when {
            previous == null -> {
                first = entry
                entry = ""
            }
            previous == entry -> onPinSet(entry)
            else -> {
                mismatch = true
                first = null
                entry = ""
            }
        }
    }
    ZkScreen(title = "PIN", onBack = onBack, scroll = false) {
        SectionTitle(
            "Step 3 of 3",
            if (first == null) "Choose a 6-digit PIN" else "Type it again",
            "It unlocks ZKAPriv on this phone. It is not your recovery phrase.",
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PinDots(entry.length, error = mismatch)
            VSpace(12)
            if (mismatch) ErrorText("The PINs did not match. Choose it again.")
            VSpace(20)
            PinPad(
                onDigit = { digit ->
                    mismatch = false
                    if (entry.length < QuanticVault.PIN_LENGTH) entry += digit
                },
                onDelete = { entry = entry.dropLast(1) },
            )
        }
        VSpace(12)
    }
}

/** Si el teléfono tiene huella o rostro fuertes, ofrece usarlos. */
@Composable
fun BiometricOfferScreen(onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val available = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    LaunchedEffect(available) { if (!available) onDone(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ZkScreen(
        title = null,
        bottom = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZButton(
                    onClick = {
                        val activity = context as? FragmentActivity
                        if (activity == null) {
                            onDone(false)
                        } else {
                            authenticate(
                                activity,
                                "Use biometrics for ZKAPriv",
                                "Confirm it is you",
                                onSuccess = { onDone(true) },
                                onError = { error = it },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ZButtonVariant.Primary,
                    size = ZButtonSize.Large,
                ) { ZButtonText("Use biometrics") }
                ZButton({ onDone(false) }, Modifier.fillMaxWidth(), ZButtonVariant.Ghost, ZButtonSize.Large) {
                    ZButtonText("Only the PIN")
                }
            }
        },
    ) {
        VSpace(48)
        SectionTitle(
            "Unlock",
            "Unlock with your fingerprint?",
            "You can still use the PIN, and change this in Settings.",
        )
        if (error != null) ErrorText(error!!)
    }
}

/** BiometricPrompt con biometría fuerte; cancelar no es un error para mostrar. */
fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit = {},
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                    code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    code != BiometricPrompt.ERROR_CANCELED
                ) onError(message.toString())
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build(),
    )
}
