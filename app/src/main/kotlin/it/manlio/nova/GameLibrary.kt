package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.system.SystemCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sotto questi FPS medi conviene scendere di risoluzione (SPEC §8.2, profili intelligenti). */
private const val SMOOTH_FPS = 55f

private const val FULL_SCALE = 100
private const val FPS_FILE = "giochi-fps"

/** Il passo di risoluzione da proporre dopo una misura; null = va bene così, o è già al minimo. */
internal fun suggestedScale(
    fps: Float?,
    scale: Int?,
): Int? = if (fps != null && fps < SMOOTH_FPS) SystemCommand.nextGameScale(scale) else null

/** Gli FPS dell'ultima misura di un gioco, finché la sua risoluzione non cambia. */
internal class GameFps(context: Context) {
    private val saved = context.getSharedPreferences(FPS_FILE, Context.MODE_PRIVATE)

    fun of(game: String): Float? = saved.getFloat(game, -1f).takeIf { it >= 0f }

    fun record(
        game: String,
        fps: Float,
    ) = saved.edit().putFloat(game, fps).apply()

    fun forget(game: String) = saved.edit().remove(game).apply()
}

/** I giochi installati, per nome: quelli che Android conosce come giochi e che si possono aprire. */
private fun installedGames(pm: PackageManager): List<Pair<String, String>> =
    pm
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        .map { it.activityInfo.applicationInfo }
        .filter { it.category == ApplicationInfo.CATEGORY_GAME }
        .distinctBy { it.packageName }
        .map { it.packageName to pm.getApplicationLabel(it).toString() }
        .sortedBy { it.second.lowercase() }

/** Impostazioni → Modalità Gaming: i giochi con risoluzione e ultima misura, più le scorciatoie al gioco in cloud (SPEC §8.2). */
@Composable
internal fun GameLibrarySection(
    backdrop: ImageBitmap,
    tools: GameTools,
) {
    val context = LocalContext.current
    val games by produceState(emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) { runCatching { installedGames(context.packageManager) }.getOrDefault(emptyList()) }
    }
    SettingsSection(backdrop, header = stringResource(R.string.games_title), footer = stringResource(R.string.games_footer)) {
        if (games.isEmpty()) SettingsRow(stringResource(R.string.games_none))
        games.forEachIndexed { index, (game, name) -> GameRow(tools, game, name, showDivider = index > 0) }
    }
    SettingsSection(backdrop, header = stringResource(R.string.games_cloud)) {
        SettingsRow(stringResource(R.string.games_xbox), onClick = { openSite(context, XBOX_CLOUD) })
        SettingsRow(stringResource(R.string.games_geforce), showDivider = true, onClick = { openSite(context, GEFORCE_NOW) })
    }
}

@Composable
private fun GameRow(
    tools: GameTools,
    game: String,
    name: String,
    showDivider: Boolean,
) {
    val context = LocalContext.current
    var changes by remember { mutableIntStateOf(0) }
    val scale = remember(game, changes) { tools.scale(game) }
    val fps = remember(game, changes) { GameFps(context).of(game) }
    val tip = suggestedScale(fps, scale)
    val summary =
        when {
            fps == null -> null
            tip == null -> stringResource(R.string.games_fps, fps.toInt())
            else -> stringResource(R.string.games_fps_tip, fps.toInt(), tip)
        }
    SettingsRow(
        name,
        summary = summary,
        showDivider = showDivider,
        onClick = {
            context.packageManager.getLaunchIntentForPackage(game)?.let { runCatching { context.startActivity(it) } }
        },
        trailing = {
            val label = stringResource(R.string.games_change_scale, name)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 56.dp)
                    .semantics { contentDescription = label }
                    .clickable(role = Role.Button) { tools.stepScale(game) { changes++ } },
                contentAlignment = Alignment.Center,
            ) {
                val text = stringResource(R.string.game_scale_percent, scale ?: FULL_SCALE)
                BasicText(text, Modifier.padding(horizontal = 8.dp), style = TextStyle(Nova.tokens.colors.accent, 16.sp))
            }
        },
    )
}

private fun openSite(
    context: Context,
    url: String,
) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private const val XBOX_CLOUD = "https://www.xbox.com/play"
private const val GEFORCE_NOW = "https://play.geforcenow.com"
