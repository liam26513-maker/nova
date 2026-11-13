// SPDX-License-Identifier: GPL-3.0-only
// NOVA: editor visivo della disposizione dei tasti (richiesta del 13 novembre 2026).
package helium314.keyboard.settings.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity

/** La disposizione come righe di tasti: ogni voce è una riga del formato semplice (il tasto, poi i caratteri a pressione lunga). */
object KeyArrangement {
    /** null se la disposizione non è nel formato semplice (per esempio JSON): quella non si modifica qui. */
    fun parse(content: String): List<List<String>>? {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("[") || trimmed.startsWith("{")) return null
        return LayoutUtils.getSimpleRowStrings(trimmed).map { row -> row.lines().map { it.trim() }.filter { it.isNotEmpty() } }
    }

    fun serialize(rows: List<List<String>>): String = rows.joinToString("\n\n") { it.joinToString("\n") }

    /** Scambia due tasti (riga, posizione), con i loro caratteri a pressione lunga. */
    fun swap(rows: List<List<String>>, a: Pair<Int, Int>, b: Pair<Int, Int>): List<List<String>> {
        val out = rows.map { it.toMutableList() }
        val first = out[a.first][a.second]
        out[a.first][a.second] = out[b.first][b.second]
        out[b.first][b.second] = first
        return out
    }

    /** Cosa c'è scritto sul tasto. */
    fun label(line: String): String = line.substringBefore(' ')

    const val NAME = "NOVA"
}

@Composable
fun KeyArrangementScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    var subtype by remember { mutableStateOf(SubtypeSettings.getSelectedSubtype(ctx.prefs()).toSettingsSubtype()) }
    val start = remember { KeyArrangement.parse(currentContent(subtype, ctx)) }
    var rows by remember { mutableStateOf(start) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun apply(newSubtype: SettingsSubtype) {
        SubtypeUtilsAdditional.changeAdditionalSubtype(subtype, newSubtype, ctx)
        subtype = newSubtype
        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    SearchSettingsScreen(onClickBack = onClickBack, title = stringResource(R.string.nova_key_arrangement), settings = emptyList()) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val current = rows
                if (current == null) {
                    Text(stringResource(R.string.nova_key_arrangement_unsupported))
                    return@Column
                }
                Text(stringResource(R.string.nova_key_arrangement_hint))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                            row.forEachIndexed { k, line ->
                                val here = r to k
                                val isSelected = selected == here
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(10.dp))
                                        .clickable {
                                            val from = selected
                                            selected = when {
                                                from == null -> here
                                                from == here -> null
                                                else -> {
                                                    rows = KeyArrangement.swap(current, from, here)
                                                    null
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        KeyArrangement.label(line),
                                        fontSize = 20.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        val name = LayoutUtilsCustom.getLayoutName(KeyArrangement.NAME, LayoutType.MAIN, subtype.locale)
                        LayoutUtilsCustom.getLayoutFile(name, LayoutType.MAIN, ctx).writeText(KeyArrangement.serialize(current))
                        LayoutUtilsCustom.onLayoutFileChanged()
                        apply(subtype.withLayout(LayoutType.MAIN, name))
                        Toast.makeText(ctx, R.string.nova_key_arrangement_saved, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.save)) }
                    OutlinedButton(onClick = {
                        apply(subtype.withoutLayout(LayoutType.MAIN))
                        rows = KeyArrangement.parse(currentContent(subtype, ctx))
                        selected = null
                    }) { Text(stringResource(R.string.nova_key_arrangement_reset)) }
                }
                Box(Modifier.height(8.dp))
            }
        }
    }
}

/** Il testo della disposizione in uso per [subtype]: quella personalizzata se c'è, altrimenti quella della tastiera. */
private fun currentContent(subtype: SettingsSubtype, ctx: android.content.Context): String {
    val name = subtype.mainLayoutName() ?: SubtypeLocaleUtils.QWERTY
    return runCatching {
        if (LayoutUtilsCustom.isCustomLayout(name)) LayoutUtilsCustom.getLayoutFile(name, LayoutType.MAIN, ctx).readText()
        else LayoutUtils.getContentWithPlus(name, subtype.locale, ctx)
    }.getOrDefault("")
}
