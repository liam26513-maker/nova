package it.manlio.nova.feature.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Un pacchetto di icone installato. */
data class IconPackInfo(
    val packageName: String,
    val label: String,
)

/**
 * Pacchetti di icone di terzi nel formato diffuso (`appfilter.xml`, quello usato da Niagara, Nova,
 * Lawnchair…): si leggono dal pacchetto installato, nessuna icona viene copiata in NOVA.
 */
class IconPacks(private val context: Context) {
    private var loaded: Pair<String, IconPack>? = null

    /** Pacchetti installati che si dichiarano come pacchetti di icone. */
    fun installed(): List<IconPackInfo> {
        val pm = context.packageManager
        return THEME_ACTIONS
            .flatMap { action -> pm.queryIntentActivities(Intent(action), PackageManager.ResolveInfoFlags.of(0)) }
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { IconPackInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** Icona del pacchetto per [componentKey] (`pacchetto/attività`), o null se il pacchetto non la ha. */
    @Synchronized
    fun icon(
        packageName: String,
        componentKey: String,
        densityDpi: Int,
    ): Drawable? {
        val pack =
            loaded?.takeIf { it.first == packageName }?.second
                ?: load(packageName)?.also { loaded = packageName to it }
                ?: return null
        return pack.drawable(componentKey, densityDpi)
    }

    private fun load(packageName: String): IconPack? =
        runCatching {
            val res = context.packageManager.getResourcesForApplication(packageName)
            IconPack(packageName, res, IconPackParser.parse(appFilter(res, packageName) ?: return@runCatching null))
        }.getOrNull()

    @SuppressLint("DiscouragedApi") // i pacchetti di icone si leggono per nome: è il loro formato
    private fun appFilter(
        res: Resources,
        packageName: String,
    ): XmlPullParser? {
        val id = res.getIdentifier("appfilter", "xml", packageName)
        if (id != 0) return res.getXml(id)
        val stream = runCatching { res.assets.open("appfilter.xml") }.getOrNull() ?: return null
        return XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, null) }
    }

    private class IconPack(
        val packageName: String,
        val res: Resources,
        val map: Map<String, String>,
    ) {
        @SuppressLint("DiscouragedApi")
        fun drawable(
            componentKey: String,
            densityDpi: Int,
        ): Drawable? {
            val name = map[componentKey] ?: return null
            val id = res.getIdentifier(name, "drawable", packageName)
            return if (id == 0) null else runCatching { res.getDrawableForDensity(id, densityDpi, null) }.getOrNull()
        }
    }

    private companion object {
        val THEME_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
    }
}

/** Lettura di `appfilter.xml`: `<item component="ComponentInfo{pacchetto/attività}" drawable="nome"/>`. */
internal object IconPackParser {
    private val COMPONENT = Regex("""ComponentInfo\{([^/}]+)/([^}]+)}""")

    fun parse(parser: XmlPullParser): Map<String, String> {
        val map = HashMap<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val key = parser.getAttributeValue(null, "component")?.let(::componentKey)
                val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() }
                if (key != null && drawable != null) map.putIfAbsent(key, drawable)
            }
        }
        return map
    }

    /** "ComponentInfo{com.app/com.app.Main}" → "com.app/com.app.Main"; anche con attività relativa ".Main". */
    fun componentKey(component: String): String? {
        val (pkg, cls) = COMPONENT.find(component)?.destructured ?: return null
        val fullClass = if (cls.startsWith(".")) pkg + cls else cls
        return "$pkg/$fullClass"
    }
}

/** Accesso al lettore di appfilter.xml per i test dell'app (il lettore resta interno al modulo). */
object IconPackParserAccess {
    fun parse(parser: XmlPullParser): Map<String, String> = IconPackParser.parse(parser)
}
