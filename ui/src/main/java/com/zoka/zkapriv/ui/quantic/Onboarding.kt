package com.zoka.zkapriv.ui.quantic

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.AccentDot
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.Eyebrow
import com.zoka.zkapriv.ui.components.GlassArc
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.SectionTitle
import com.zoka.zkapriv.ui.components.StepBars
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZChip
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.components.zkCapsule
import com.zoka.zkapriv.ui.components.zkShake
import com.zoka.zkapriv.ui.theme.Zk

// ── Bienvenida ───────────────────────────────────────────────────────────────

/**
 * La primera pantalla, como el inicio de zoka.network: el arco de luz, la
 * marca grande con el rosa solo en QUANTIC, una frase y las dos entradas.
 */
@Composable
fun WelcomeScreen(onCreate: () -> Unit, onRestore: () -> Unit) {
    val c = Zk.colors
    ZkScreen(
        title = null,
        scroll = false,
        backdrop = { GlassArc(towardsEnd = true) },
        bottom = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZButton(onCreate, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large) {
                    ZButtonText("Create wallet")
                    ForwardMark()
                }
                ZButton(onRestore, Modifier.fillMaxWidth(), ZButtonVariant.Secondary, ZButtonSize.Large) {
                    ZButtonText("Restore with 24 words")
                }
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Eyebrow("Quantic Testnet v3", dot = true)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.text)) { append("ZOKA\n") }
                    withStyle(SpanStyle(color = c.accent)) { append("QUANTIC") }
                },
                style = Zk.text.brandHero.copy(fontSize = 50.sp, lineHeight = 52.sp),
            )
            Text("Private by design.", style = Zk.text.brandTagline, color = c.textSoft)
            Text(
                "A post-quantum private wallet. Your keys and every proof stay on this phone.",
                style = Zk.text.lead,
                color = c.textSoft,
                modifier = Modifier.widthIn(max = 300.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AccentDot(size = 5.dp, color = c.textMuted)
                Text("TEST COINS, NO VALUE", style = Zk.text.pill, color = c.textMuted)
            }
        }
        Spacer(Modifier.weight(0.35f))
    }
}

// ── Respaldo de la frase ─────────────────────────────────────────────────────

@Composable
fun BackupPhraseScreen(words: List<String>, onBack: () -> Unit, onContinue: () -> Unit) {
    var written by remember { mutableStateOf(false) }
    ZkScreen(
        title = null,
        onBack = onBack,
        actions = { StepBars(1, 3) },
        bottom = {
            ZButton(onContinue, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large, enabled = written) {
                ZButtonText("Continue")
                ForwardMark()
            }
        },
    ) {
        SectionTitle(
            null,
            "Write down your 24 words",
            "In this order, on paper. They are the only way to recover this wallet, here or in ZSilent.",
        )
        Notice(
            "Anyone with these words can spend your coins. Never type them into a website or share a photo of them.",
            title = "Keep them secret",
            tone = ZkTone.Err,
        )
        WordGrid(words)
        Panel(contentPadding = PaddingValues(0.dp), gap = 0.dp) {
            SwitchRow("I wrote the 24 words on paper", null, written) { written = it }
        }
    }
}

/** Las palabras en dos columnas, numeradas, en una tarjeta de vidrio. */
@Composable
fun WordGrid(words: List<String>) {
    val half = (words.size + 1) / 2
    Panel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(0 until half, half until words.size).forEach { range ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    range.forEach { i ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%02d".format(i + 1),
                                style = Zk.text.mono,
                                color = Zk.colors.textMuted,
                                modifier = Modifier.width(30.dp),
                            )
                            Text(words[i], style = Zk.text.mono.copy(fontSize = 14.sp), color = Zk.colors.text)
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
    var misses by remember { mutableIntStateOf(0) }
    val position = questions[step]
    val answer = words[position]
    val options = remember(step) {
        (words.filter { it != answer }.distinct().shuffled().take(5) + answer).shuffled()
    }
    ZkScreen(title = null, onBack = onBack, actions = { StepBars(2, 3) }) {
        SectionTitle(null, "Which is word #${position + 1}?", "Question ${step + 1} of 3. Check your paper.")
        Column(Modifier.padding(top = 8.dp).zkShake(misses), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { option ->
                        ZButton(
                            onClick = {
                                if (option == answer) {
                                    wrong = null
                                    if (step == questions.lastIndex) onVerified() else step++
                                } else {
                                    wrong = option
                                    misses++
                                }
                            },
                            modifier = Modifier.weight(1f),
                            variant = if (wrong == option) ZButtonVariant.Danger else ZButtonVariant.Secondary,
                            size = ZButtonSize.Large,
                        ) {
                            Text(option, style = Zk.text.mono.copy(fontSize = 15.sp), color = LocalContentColor.current, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (wrong != null) ErrorText("That is not word #${position + 1}. Check your paper.")
    }
}

// ── Restaurar ────────────────────────────────────────────────────────────────

/**
 * Las 24 palabras, una por casilla. Mientras se escribe, sobre el teclado van
 * hasta cuatro palabras de la lista que empiezan igual: tocar una la completa
 * y pasa a la casilla siguiente. Una casilla queda en rojo si ninguna palabra
 * empieza así. Pegar la frase entera la reparte.
 *
 * La frase vive solo en memoria (`remember`, nunca en el estado guardado) y
 * el teclado va sin autocorrector ni aprendizaje, como antes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestoreScreen(validate: (String) -> Boolean, onBack: () -> Unit, onRestored: (String) -> Unit) {
    var cells by remember { mutableStateOf(List(PhraseEntry.WORDS) { "" }) }
    var focused by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusers = remember { List(PhraseEntry.WORDS) { FocusRequester() } }
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val keyboardOpen = WindowInsets.isImeVisible
    val complete = PhraseEntry.complete(cells)

    fun type(at: Int, text: String) {
        val (next, focus) = PhraseEntry.type(cells, at, text)
        cells = next
        error = null
        if (focus != at) focusers[focus].requestFocus()
    }

    fun pick(word: String) {
        val at = focused
        cells = cells.toMutableList().also { it[at] = word }
        error = null
        if (at < PhraseEntry.WORDS - 1) focusers[at + 1].requestFocus() else focusManager.clearFocus()
    }

    ZkScreen(
        title = null,
        onBack = onBack,
        actions = { StepBars(1, 2) },
        bottom = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { ErrorText(it) }
                if (keyboardOpen && !complete) {
                    WordSuggestions(PhraseEntry.suggest(cells[focused]), focused + 1, ::pick)
                } else {
                    ZButton(
                        onClick = {
                            val phrase = PhraseEntry.phrase(cells)
                            if (validate(phrase)) onRestored(phrase) else error =
                                "These 24 words do not form a valid phrase. Check their order."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ZButtonVariant.Primary,
                        size = ZButtonSize.Large,
                        enabled = complete,
                    ) { ZButtonText("Restore wallet") }
                }
            }
        },
    ) {
        SectionTitle(null, "Type your 24 words", "Of this wallet or a ZSilent wallet.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${PhraseEntry.validCount(cells)} of ${PhraseEntry.WORDS}",
                style = Zk.text.mono,
                color = Zk.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            ZButton(
                onClick = {
                    val text = clipboard.getText()?.text.orEmpty()
                    // La frase entera va desde la primera casilla; menos, desde la que se escribe.
                    if (text.isNotBlank()) {
                        type(if (PhraseEntry.words(text).size >= PhraseEntry.WORDS) 0 else focused, text)
                    }
                },
                size = ZButtonSize.Small,
            ) {
                Icon(Icons.Outlined.ContentPaste, null, tint = LocalContentColor.current, modifier = Modifier.size(14.dp))
                ZButtonText("Paste")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until PhraseEntry.WORDS / 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (index in row * 2..row * 2 + 1) {
                        val word = cells[index]
                        WordCell(
                            number = index + 1,
                            value = word,
                            onValueChange = { type(index, it) },
                            focusRequester = focusers[index],
                            onFocused = { focused = index },
                            // Mientras se escribe, es un error solo si ninguna palabra empieza así.
                            isError = word.isNotEmpty() && !PhraseEntry.isKnown(word) &&
                                (index != focused || PhraseEntry.suggest(word, 1).isEmpty()),
                            last = index == PhraseEntry.WORDS - 1,
                            onNext = {
                                if (index < PhraseEntry.WORDS - 1) focusers[index + 1].requestFocus()
                                else focusManager.clearFocus()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        HintText("The wallet reads the chain from its first block, so the first sync takes a little longer.")
    }
}

/** Una casilla de la frase: su número y la palabra. */
@Composable
private fun WordCell(
    number: Int,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    isError: Boolean,
    last: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Zk.colors
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.small
    val border = when {
        isError -> c.danger.copy(alpha = 0.7f)
        focused -> c.accentLine
        else -> c.border
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 46.dp)
            .background(if (focused) c.elevated else c.surface2, shape)
            .border(BorderStroke(1.dp, border), shape)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        singleLine = true,
        textStyle = Zk.text.mono.copy(color = if (isError) c.danger else c.text),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = KeyboardType.Password,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
        cursorBrush = SolidColor(c.accent),
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(number.toString(), style = Zk.text.mono.copy(fontSize = 11.sp), color = c.textMuted, modifier = Modifier.width(24.dp))
                Box(Modifier.weight(1f)) { inner() }
            }
        },
    )
}

/** Las palabras que empiezan como lo escrito, sobre el teclado. */
@Composable
private fun WordSuggestions(words: List<String>, number: Int, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 38.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (words.isEmpty()) {
            HintText("Type the first letters of word $number")
        } else {
            words.forEachIndexed { i, word ->
                ZChip(word, onClick = { onPick(word) }, selected = i == 0, role = Role.Button)
            }
        }
    }
}

// ── PIN ──────────────────────────────────────────────────────────────────────

/**
 * Elegir el PIN: una vez y otra para confirmarlo. `steps` dibuja las barritas
 * del alta (el PIN es siempre el último paso); `screenTitle` va en la barra
 * cuando se cambia desde los ajustes.
 */
@Composable
fun PinSetupScreen(
    onBack: () -> Unit,
    onPinSet: (String) -> Unit,
    steps: Int? = null,
    screenTitle: String? = null,
) {
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    var misses by remember { mutableIntStateOf(0) }
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
                misses++
                first = null
                entry = ""
            }
        }
    }
    ZkScreen(
        title = screenTitle,
        onBack = onBack,
        scroll = false,
        actions = { if (steps != null) StepBars(steps, steps) },
    ) {
        PinGate(
            title = if (first == null) "Choose a 6-digit PIN" else "Type it again",
            filled = entry.length,
            onDigit = { digit ->
                mismatch = false
                if (entry.length < QuanticVault.PIN_LENGTH) entry += digit
            },
            onDelete = { entry = entry.dropLast(1) },
            subtitle = "It unlocks ZKAPriv on this phone. It is not your recovery phrase.",
            message = if (mismatch) "The PINs did not match. Choose it again." else null,
            isError = mismatch,
            shake = misses,
        )
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
    BiometricOfferContent(
        error = error,
        onUse = {
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
        onSkip = { onDone(false) },
    )
}

/** La oferta de la huella, sin el sistema: también la dibuja la vista de diseño. */
@Composable
internal fun BiometricOfferContent(error: String?, onUse: () -> Unit, onSkip: () -> Unit) {
    val c = Zk.colors
    ZkScreen(
        title = null,
        scroll = false,
        bottom = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZButton(onUse, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large) {
                    ZButtonText("Use biometrics")
                }
                ZButton(onSkip, Modifier.fillMaxWidth(), ZButtonVariant.Ghost, ZButtonSize.Large) {
                    ZButtonText("Only the PIN")
                }
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.align(Alignment.CenterHorizontally).size(104.dp).zkCapsule(), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Fingerprint, null, tint = c.accent, modifier = Modifier.size(48.dp))
        }
        VSpace(14)
        Text(
            "Unlock with your fingerprint?",
            style = MaterialTheme.typography.headlineMedium,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "You can still use the PIN, and change this in Settings.",
            style = Zk.text.lead,
            color = c.textSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = c.danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.weight(1.2f))
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
