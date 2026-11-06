package it.manlio.nova.core.config

import kotlinx.serialization.Serializable

/** Versione dello schema di configurazione supportata da questa build. */
const val SUPPORTED_SCHEMA_VERSION = 1

/** Metadati di un pacchetto di configurazione (SPEC §3.5). */
@Serializable
data class ConfigMeta(
    val schemaVersion: Int,
    val version: String,
    val minAppVersion: Int = 0,
)

@Serializable
data class BrandConfig(
    val schemaVersion: Int,
    val displayName: String,
    val accentColor: String,
)

@Serializable
data class ThemeColors(
    val background: String,
    val surface: String,
    val onSurface: String,
    val onSurfaceMuted: String,
    val accent: String,
)

@Serializable
data class ThemeRadii(
    val small: Float,
    val medium: Float,
    val large: Float,
)

@Serializable
data class ThemeMotion(
    val stiffness: Float,
    val dampingRatio: Float,
)

@Serializable
data class ThemeConfig(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val dark: Boolean,
    val colors: ThemeColors,
    val radii: ThemeRadii,
    val materialOpacity: Float,
    val motion: ThemeMotion,
)

@Serializable
data class ModulesConfig(
    val schemaVersion: Int,
    val modules: Map<String, Boolean>,
)

/** Una modalità: nomi delle azioni, in ordine, e durata massima. I nomi li capisce `core/modes`; quelli sconosciuti si ignorano. */
@Serializable
data class ModeConfig(
    val actions: List<String>,
    val maxMinutes: Int,
)

@Serializable
data class ModesConfig(
    val schemaVersion: Int,
    val modes: Map<String, ModeConfig>,
)

/** Un'app che si può spegnere (SPEC §7.2): [level] è `safe` o `consider`. */
@Serializable
data class DebloatApp(
    val name: String,
    val level: String,
    val note: String,
)

@Serializable
data class DebloatConfig(
    val schemaVersion: Int,
    val apps: Map<String, DebloatApp>,
)

/** Configurazione completa risultante dalla fusione degli strati. */
@Serializable
data class NovaConfig(
    val meta: ConfigMeta,
    val brand: BrandConfig,
    val theme: ThemeConfig,
    val modules: ModulesConfig,
    val modes: ModesConfig,
    val debloat: DebloatConfig,
)
