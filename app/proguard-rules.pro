# kotlinx.serialization: conserva i serializzatori generati dei modelli di configurazione.
-keepclassmembers class it.manlio.nova.core.config.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class it.manlio.nova.core.config.** {
    kotlinx.serialization.KSerializer serializer(...);
}
