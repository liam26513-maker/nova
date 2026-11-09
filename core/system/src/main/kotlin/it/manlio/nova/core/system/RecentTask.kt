package it.manlio.nova.core.system

/** Un'app nelle recenti di Android: il task, il pacchetto e l'activity in cima (vuota se Android non la dice). */
data class RecentTask(
    val id: Int,
    val packageName: String,
    val component: String,
)

/** Le righe di [IShellService.recentTasks]: puro, si prova senza Android. Righe rovinate si saltano. */
object RecentTasksCodec {
    fun encode(tasks: List<RecentTask>): String = tasks.joinToString("\n") { "${it.id}\t${it.packageName}\t${it.component}" }

    fun decode(text: String): List<RecentTask> =
        text.lines().mapNotNull { line ->
            val f = line.split('\t')
            val id = f.getOrNull(0)?.toIntOrNull()
            val pkg = f.getOrNull(1)?.takeIf { it.isNotBlank() }
            if (id == null || pkg == null) null else RecentTask(id, pkg, f.getOrNull(2).orEmpty())
        }
}
