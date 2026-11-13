@file:Suppress("MagicNumber") // colori e misure della scheda: sono il progetto grafico

package it.manlio.nova

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * La risposta rapida dalla tendina di NOVA (SPEC §6.2): sopra le altre app la tastiera non può comparire sopra la tendina, quindi
 * il testo si scrive in questa piccola finestra. Il messaggio va solo all'app della notifica, come dalla tendina di Android.
 */
class ReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(KEY) ?: return finish()
        val to = intent.getStringExtra(TO).orEmpty()
        // Il messaggio a cui rispondi, letto ora dalla notifica (richiesta del 13 novembre 2026).
        val lines = NotificationHub.conversation(key)
        setContent {
            ReplyScreen(to, lines, onSend = { text ->
                if (!NotificationHub.reply(key, text)) Toast.makeText(this, R.string.reply_failed, Toast.LENGTH_LONG).show()
                finish()
            }, onCancel = ::finish)
        }
    }

    companion object {
        const val KEY = "notifica"
        const val TO = "a"
    }
}

@Composable
private fun ReplyScreen(
    to: String,
    lines: List<ChatLine>,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DIM))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .background(CARD, RoundedCornerShape(24.dp))
                // Il tocco sulla scheda non deve chiuderla.
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(
                stringResource(R.string.reply_title, to.ifBlank { "…" }),
                style = TextStyle(Color.White, 18.sp, FontWeight.SemiBold),
                maxLines = 1,
            )
            if (lines.isNotEmpty()) Conversation(lines)
            BasicTextField(
                text,
                { text = it.take(MAX_REPLY) },
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(Color.White.copy(alpha = FIELD_ALPHA), RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .focusRequester(focus),
                textStyle = TextStyle(Color.White, 17.sp),
                cursorBrush = SolidColor(Color.White),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) BasicText(stringResource(R.string.reply_hint), style = TextStyle(HINT, 17.sp))
                        inner()
                    }
                },
            )
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ReplyButton(stringResource(R.string.reply_cancel), HINT, onCancel)
                ReplyButton(stringResource(R.string.reply_send), ACCENT) { if (text.isNotBlank()) onSend(text.trim()) }
            }
        }
    }
}

/** I messaggi sopra il campo: i suoi a sinistra, i tuoi a destra, scorrevoli se sono tanti. */
@Composable
private fun Conversation(lines: List<ChatLine>) {
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lines.forEach { line ->
            val mine = line.sender == null
            Column(
                Modifier
                    .align(if (mine) Alignment.End else Alignment.Start)
                    .widthIn(max = 460.dp)
                    .background(if (mine) MINE else THEIRS, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (!mine) BasicText(line.sender.orEmpty(), style = TextStyle(ACCENT, 12.sp, FontWeight.SemiBold), maxLines = 1)
                BasicText(line.text, style = TextStyle(Color.White, 15.sp))
            }
        }
    }
}

@Composable
private fun ReplyButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    BasicText(
        label,
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        style = TextStyle(color, 17.sp, FontWeight.Medium),
    )
}

private const val MAX_REPLY = 1000
private const val DIM = 0.35f
private const val FIELD_ALPHA = 0.08f
private val CARD = Color(0xF21D2127)
private val HINT = Color(0xFF9AA0A6)
private val ACCENT = Color(0xFF8AB4F8)
private val THEIRS = Color(0x1FFFFFFF)
private val MINE = Color(0x5C8AB4F8)
