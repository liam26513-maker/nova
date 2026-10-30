package it.manlio.nova.core.config

/**
 * Controlli semantici che rispecchiano gli schemi in `config/schema/`.
 * La CI valida i file contro gli schemi; qui si protegge il tablet da
 * pacchetti o override non validi prima di applicarli.
 */
object ConfigValidator {
    private val COLOR = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val ID = Regex("^[a-z][a-z0-9-]*$")

    fun validate(c: NovaConfig): List<String> =
        buildList {
            listOf(
                "meta" to c.meta.schemaVersion,
                "brand" to c.brand.schemaVersion,
                "theme" to c.theme.schemaVersion,
                "modules" to c.modules.schemaVersion,
            ).forEach { (name, v) ->
                if (v != SUPPORTED_SCHEMA_VERSION) {
                    add("$name.schemaVersion=$v non supportato (atteso $SUPPORTED_SCHEMA_VERSION)")
                }
            }
            if (c.brand.displayName.isBlank()) add("brand.displayName vuoto")
            color("brand.accentColor", c.brand.accentColor)
            with(c.theme.colors) {
                color("theme.colors.background", background)
                color("theme.colors.surface", surface)
                color("theme.colors.onSurface", onSurface)
                color("theme.colors.onSurfaceMuted", onSurfaceMuted)
                color("theme.colors.accent", accent)
            }
            if (!ID.matches(c.theme.id)) add("theme.id non valido: ${c.theme.id}")
            with(c.theme.radii) {
                if (small < 0 || medium < 0 || large < 0) add("theme.radii negativi")
                if (small > medium || medium > large) add("theme.radii devono crescere: small ≤ medium ≤ large")
            }
            if (c.theme.materialOpacity !in 0f..1f) add("theme.materialOpacity fuori da 0..1")
            if (c.theme.motion.stiffness <= 0f) add("theme.motion.stiffness deve essere > 0")
            if (c.theme.motion.dampingRatio !in 0.1f..2f) add("theme.motion.dampingRatio fuori da 0.1..2")
            c.modules.modules.keys.filterNot { ID.matches(it) }.forEach { add("modules: id non valido '$it'") }
        }

    private fun MutableList<String>.color(
        field: String,
        value: String,
    ) {
        if (!COLOR.matches(value)) add("$field non è un colore #RRGGBB o #AARRGGBB: $value")
    }
}
