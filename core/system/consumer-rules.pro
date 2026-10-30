# Shizuku crea ShellService per nome (UserService): non va rinominato né rimosso.
-keep class it.manlio.nova.core.system.ShellService { <init>(...); *; }
-keep class it.manlio.nova.core.system.IShellService { *; }
-keep class it.manlio.nova.core.system.IShellService$* { *; }
