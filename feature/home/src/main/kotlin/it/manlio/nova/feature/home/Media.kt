@file:Suppress("MagicNumber") // misure e simboli: sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform
import kotlinx.coroutines.flow.StateFlow

/** Cosa sta suonando. [art] è la copertina, già ridotta, se c'è. */
@Immutable
data class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap? = null,
)

/** La musica in riproduzione (sessioni multimediali di Android) e i suoi comandi; l'app li fornisce. */
interface MediaSource {
    val nowPlaying: StateFlow<NowPlaying?>

    fun previous()

    fun toggle()

    fun next()

    /** Apre l'app che sta suonando. */
    fun open()
}

private enum class MediaGlyph { PREVIOUS, PLAY, PAUSE, NEXT, NOTE }

/**
 * «Isola dinamica» (SPEC §6.7) dentro la Home: una pillola in alto con la musica in riproduzione; toccata si
 * allarga con i comandi. Compare solo mentre suona qualcosa (in pausa si riprende dal widget Musica).
 * È una vista della Home, non una finestra sopra le altre app.
 */

@Composable
internal fun DynamicIsland(
    media: MediaSource?,
    modifier: Modifier = Modifier,
) {
    val now = media?.nowPlaying?.collectAsState()?.value
    var expanded by remember { mutableStateOf(false) }
    val look = LocalHomeLook.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    AnimatedVisibility(
        visible = media != null && now?.playing == true,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = 500f)) + expandHorizontally(spring(stiffness = 400f)),
        exit = fadeOut(spring(stiffness = 500f)) + shrinkHorizontally(spring(stiffness = 400f)),
    ) {
        val song = now ?: return@AnimatedVisibility
        val view = LocalView.current
        val label = stringResource(R.string.island_label, song.title)
        Row(
            Modifier
                .widthIn(max = if (expanded) 460.dp else 300.dp)
                .background(Color.Black.copy(alpha = 0.78f), CircleShape)
                .glass(CircleShape, dark = true, flat = flat)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    view.perform(NovaHaptic.TAP)
                    expanded = !expanded
                }.padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Cover(song, 28)
            Column(Modifier.weight(1f, fill = false)) {
                BasicText(
                    song.title,
                    style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold, fontFamily = look.family),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (expanded && song.artist.isNotBlank()) {
                    BasicText(
                        song.artist,
                        style = TextStyle(Color.White.copy(alpha = 0.7f), 12.sp, fontFamily = look.family),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (expanded) Controls(media!!, song, Color.White, 34)
        }
    }
}

/** Widget Musica: copertina, titolo, artista e i tre comandi. */
@Composable
internal fun MusicWidget(media: MediaSource?) {
    val look = LocalHomeLook.current
    val now = media?.nowPlaying?.collectAsState()?.value
    if (media == null || now == null) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(stringResource(R.string.music_none), style = TextStyle(look.muted, 15.sp, fontFamily = look.family))
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(now, 56)
            Column(Modifier.weight(1f)) {
                BasicText(
                    now.title,
                    style = TextStyle(look.ink, 17.sp, FontWeight.SemiBold, fontFamily = look.family),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    now.artist,
                    style = TextStyle(look.muted, 14.sp, fontFamily = look.family),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.fillMaxSize(0.99f), horizontalArrangement = Arrangement.Center) { Controls(media, now, look.ink, 44) }
    }
}

@Composable
private fun Cover(
    song: NowPlaying,
    size: Int,
) {
    val look = LocalHomeLook.current
    Box(
        Modifier.size(size.dp).clip(SquircleShape((size * 0.24f).dp)).background(look.ink.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        val art = song.art
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.size((size * 0.5f).dp)) { drawGlyph(MediaGlyph.NOTE, look.ink) }
        }
    }
}

@Composable
private fun Controls(
    media: MediaSource,
    song: NowPlaying,
    ink: Color,
    size: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaButton(MediaGlyph.PREVIOUS, stringResource(R.string.music_previous), ink, size, media::previous)
        MediaButton(
            if (song.playing) MediaGlyph.PAUSE else MediaGlyph.PLAY,
            stringResource(if (song.playing) R.string.music_pause else R.string.music_play),
            ink,
            size,
            media::toggle,
        )
        MediaButton(MediaGlyph.NEXT, stringResource(R.string.music_next), ink, size, media::next)
    }
}

@Composable
private fun MediaButton(
    glyph: MediaGlyph,
    description: String,
    ink: Color,
    size: Int,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Box(
        Modifier
            .size(size.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size((size * 0.46f).dp)) { drawGlyph(glyph, ink) } }
}

private fun DrawScope.drawGlyph(
    glyph: MediaGlyph,
    ink: Color,
) {
    val u = size.minDimension

    fun tri(
        x0: Float,
        x1: Float,
    ) = Path().apply {
        moveTo(x0 * u, 0.1f * u)
        lineTo(x1 * u, 0.5f * u)
        lineTo(x0 * u, 0.9f * u)
        close()
    }
    when (glyph) {
        MediaGlyph.PLAY -> drawPath(tri(0.22f, 0.9f), ink)
        MediaGlyph.PAUSE -> {
            drawRect(ink, Offset(0.18f * u, 0.12f * u), androidx.compose.ui.geometry.Size(0.24f * u, 0.76f * u))
            drawRect(ink, Offset(0.58f * u, 0.12f * u), androidx.compose.ui.geometry.Size(0.24f * u, 0.76f * u))
        }
        MediaGlyph.NEXT -> {
            drawPath(tri(0.08f, 0.62f), ink)
            drawRect(ink, Offset(0.7f * u, 0.12f * u), androidx.compose.ui.geometry.Size(0.16f * u, 0.76f * u))
        }
        MediaGlyph.PREVIOUS -> {
            drawPath(
                Path().apply {
                    moveTo(0.92f * u, 0.1f * u)
                    lineTo(0.38f * u, 0.5f * u)
                    lineTo(0.92f * u, 0.9f * u)
                    close()
                },
                ink,
            )
            drawRect(ink, Offset(0.14f * u, 0.12f * u), androidx.compose.ui.geometry.Size(0.16f * u, 0.76f * u))
        }
        MediaGlyph.NOTE -> {
            drawCircle(ink, 0.18f * u, Offset(0.32f * u, 0.76f * u))
            drawRect(ink, Offset(0.44f * u, 0.12f * u), androidx.compose.ui.geometry.Size(0.1f * u, 0.64f * u))
            drawPath(
                Path().apply {
                    moveTo(0.54f * u, 0.12f * u)
                    lineTo(0.84f * u, 0.3f * u)
                    lineTo(0.54f * u, 0.4f * u)
                    close()
                },
                ink,
            )
        }
    }
}

/** La pastiglia della Studio: stato a sinistra, Pomodoro e uscita come veri pulsanti. */
@Composable
internal fun StudyPill(
    bar: StudyBar,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = STUDY_PILL_ALPHA), RoundedCornerShape(percent = 50))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(bar.text, style = TextStyle(Color.White, 15.sp))
        StudyPillButton(bar.timerLabel, bar.onTimer)
        StudyPillButton(bar.notesLabel, bar.onNotes)
        StudyPillButton(bar.exitLabel, bar.onExit)
    }
}

@Composable
private fun StudyPillButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.SemiBold))
    }
}

private const val STUDY_PILL_ALPHA = 0.75f

/** L'isola fuori dalla Home (una finestra sopra le altre app), con l'aspetto scuro di serie. */
@Composable
fun FloatingIsland(media: MediaSource) = DynamicIsland(media)
