package it.manlio.nova.feature.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun keys(vararg k: String) = k.toList()

private fun HomeLayout.ids() = items.map { it.id }

class HomeLayoutReconcileTest {
    @Test
    fun `new apps go at the end in alphabetical order, dock apps stay out`() {
        val l = HomeLayout(listOf(AppItem("b"))).reconcile(keys("a", "b", "c", "d"), dock = setOf("d"))
        assertEquals(listOf("b", "a", "c"), l.ids())
    }

    @Test
    fun `gone, hidden and docked apps leave the layout, and a folder left with one app dissolves`() {
        val start = HomeLayout(listOf(AppItem("x"), FolderItem("f", "F", keys("a", "gone")), FolderItem("g", "G", keys("gone", "also"))))
        val l = start.reconcile(keys("a", "x"), dock = emptySet())
        assertEquals(listOf("x", "a"), l.ids())
        assertEquals(true, l.items.all { it is AppItem })
    }

    @Test
    fun `an app that is in two places stays in the first`() {
        val l = HomeLayout(listOf(FolderItem("f", "F", keys("a", "b")), AppItem("a"))).reconcile(keys("a", "b"), emptySet())
        assertEquals(listOf("f"), l.ids())
        assertEquals(keys("a", "b"), (l.items.single() as FolderItem).keys)
    }

    @Test
    fun `apps taken off the home are not brought back, but only while they exist`() {
        val l = HomeLayout(emptyList(), offHome = setOf("a", "gone")).reconcile(keys("a", "b"), emptySet())
        assertEquals(listOf("b"), l.ids())
        assertEquals(setOf("a"), l.offHome)
    }

    @Test
    fun `nothing changes when everything is already in order`() {
        val l = HomeLayout(listOf(AppItem("a"), WidgetItem("w", WidgetKind.CLOCK))).reconcile(keys("a"), emptySet())
        assertEquals(true, l === l.reconcile(keys("a"), emptySet()))
    }
}

class HomeLayoutEditTest {
    private val base = HomeLayout(listOf(AppItem("a"), AppItem("b"), AppItem("c"), WidgetItem("w", WidgetKind.CLOCK)))

    @Test
    fun `move puts an item before another, or at the end`() {
        assertEquals(listOf("b", "a", "c", "w"), base.move("a", "c").ids())
        assertEquals(listOf("b", "c", "w", "a"), base.move("a", null).ids())
        assertEquals(listOf("w", "a", "b", "c"), base.move("w", "a").ids())
        assertEquals(base, base.move("a", "a"))
        assertEquals(base, base.move("nope", "a"))
    }

    @Test
    fun `app onto app makes a folder at the target, app onto folder joins it`() {
        val f = base.merge("a", "b", "f1", "Cartella")
        assertEquals(listOf("f1", "c", "w"), f.ids())
        assertEquals(FolderItem("f1", "Cartella", keys("b", "a")), f.items.first())
        val g = f.merge("c", "f1", "unused", "x")
        assertEquals(keys("b", "a", "c"), (g.items.first() as FolderItem).keys)
        assertEquals(listOf("f1", "w"), g.ids())
    }

    @Test
    fun `widgets cannot be merged and nothing merges with itself`() {
        assertEquals(base, base.merge("a", "w", "f", "F"))
        assertEquals(base, base.merge("a", "a", "f", "F"))
        assertEquals(base, base.merge("w", "a", "f", "F"))
    }

    @Test
    fun `taking an app out puts it after the folder, and a folder with one app left dissolves`() {
        val three = HomeLayout(listOf(FolderItem("f", "F", keys("a", "b", "c"))))
        val two = three.takeOut("f", "b")
        assertEquals(listOf("f", "b"), two.ids())
        assertEquals(keys("a", "c"), (two.items.first() as FolderItem).keys)
        val dissolved = two.takeOut("f", "a")
        assertEquals(listOf("c", "a", "b"), dissolved.ids())
        assertEquals(three, three.takeOut("f", "zzz"))
    }

    @Test
    fun `remove takes apps and folders off the home but deletes widgets for good`() {
        assertEquals(setOf("a"), base.remove("a").offHome)
        assertEquals(listOf("b", "c", "w"), base.remove("a").ids())
        assertEquals(emptySet<String>(), base.remove("w").offHome)
        val folder = HomeLayout(listOf(FolderItem("f", "F", keys("a", "b")))).remove("f")
        assertEquals(setOf("a", "b"), folder.offHome)
        assertEquals(emptySet<String>(), folder.placeOnHome("a").placeOnHome("b").offHome)
    }

    @Test
    fun `folder names and notes are trimmed to a sensible length`() {
        val l = HomeLayout(listOf(FolderItem("f", "x", keys("a", "b")), WidgetItem("n", WidgetKind.NOTE)))
        assertEquals("Ciao", (l.renameFolder("f", "  Ciao  ").items.first() as FolderItem).name)
        assertEquals(HomeLayout.MAX_FOLDER_NAME, (l.renameFolder("f", "y".repeat(100)).items.first() as FolderItem).name.length)
        assertEquals(HomeLayout.MAX_NOTE, (l.setNote("n", "z".repeat(900)).items.last() as WidgetItem).text.length)
        assertEquals("f", l.folderOf("a")?.id)
        assertNull(l.folderOf("q"))
    }
}

class PackTest {
    private fun apps(n: Int) = (1..n).map { AppItem("a$it") }

    @Test
    fun `icons fill the page in reading order and overflow to the next page`() {
        val pages = pack(apps(7), columns = 3, rows = 2)
        assertEquals(listOf(6, 1), pages.map { it.size })
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1), pages[0].map { it.col to it.row })
        assertEquals(1, pages[1].single().page)
    }

    @Test
    fun `a wide widget takes its cells and later icons go around it`() {
        val items = listOf(AppItem("a"), WidgetItem("w", WidgetKind.CLOCK), AppItem("b"), AppItem("c"))
        val p = pack(items, columns = 4, rows = 2)[0]
        // a in (0,0); il widget 2×2 occupa le colonne 1-2 delle due righe; b in (3,0); c nella prima casella libera dopo: (0,1).
        assertEquals(listOf(0 to 0, 1 to 0, 3 to 0, 0 to 1), p.map { it.col to it.row })
        assertEquals(2 to 2, p[1].cols to p[1].rows)
    }

    @Test
    fun `a widget that does not fit the rest of the page starts the next one, and never goes backwards`() {
        val items =
            listOf(AppItem("a"), AppItem("b"), AppItem("c"), AppItem("d"), AppItem("e"), WidgetItem("w", WidgetKind.CLOCK), AppItem("f"))
        val pages = pack(items, columns = 3, rows = 2)
        assertEquals(listOf("a", "b", "c", "d", "e"), pages[0].map { it.item.id })
        assertEquals(listOf("w", "f"), pages[1].map { it.item.id })
    }

    @Test
    fun `an oversized widget shrinks to the page, and no items still means one page`() {
        val big = pack(listOf(WidgetItem("w", WidgetKind.EXTERNAL, cols = 9, rows = 9)), columns = 3, rows = 2).single().single()
        assertEquals(3 to 2, big.cols to big.rows)
        assertEquals(1, pack(emptyList(), 3, 2).size)
    }
}

class LayoutStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `everything survives a restart, including odd keys, names and notes`() =
        runTest {
            val file = tmp.root.resolve("layout.txt")
            val store = HomeLayoutStore(file)
            val layout =
                HomeLayout(
                    listOf(
                        AppItem("com.a/.Main#0"),
                        FolderItem("f1", "Caffè | è = #1", keys("com.b/.X#10", "com.c/.Y#0")),
                        WidgetItem("w1", WidgetKind.NOTE, text = "riga uno\nriga due | con barra"),
                        WidgetItem("w2", WidgetKind.EXTERNAL, cols = 3, rows = 2, ref = 42),
                    ),
                    offHome = setOf("com.z/.Z#0"),
                )
            store.update { layout }
            assertEquals(layout, HomeLayoutStore(file).layout.value)
        }

    @Test
    fun `a missing, foreign or damaged file gives an empty layout and bad lines are skipped`() {
        assertEquals(HomeLayout(), HomeLayoutStore(tmp.root.resolve("none.txt")).layout.value)
        assertEquals(HomeLayout(), LayoutCodec.read("ciao"))
        val text = "nova-layout 1\napp|a\nwidget|w|MAGICO|1|1|0\nfolder|f\nwidget|ok|CLOCK|2|2|0\noff|b\n"
        assertEquals(HomeLayout(listOf(AppItem("a"), WidgetItem("ok", WidgetKind.CLOCK)), setOf("b")), LayoutCodec.read(text))
    }
}

class AppSearchTest {
    @Test
    fun `case and accents do not matter`() {
        assertEquals(0, AppSearch.score("CAFFE", "Caffè"))
        assertEquals(0, AppSearch.score("  orolo", "Orologio"))
        assertEquals(null, AppSearch.score("", "Orologio"))
        assertEquals(null, AppSearch.score("zz", "Orologio"))
    }

    @Test
    fun `starts with comes before a word start, which comes before contains`() {
        val names = listOf("Mappe", "Google Maps", "Camping", "Zampa", "Map Viewer")
        assertEquals(listOf("Map Viewer", "Mappe", "Google Maps"), AppSearch.search(names, "ma") { it })
    }
}

class DropActionTest {
    private val a = AppItem("a")
    private val b = AppItem("b")
    private val c = AppItem("c")
    private val w = WidgetItem("w", WidgetKind.CLOCK)
    private val page =
        listOf(
            a to Rect(0f, 0f, 100f, 100f),
            b to Rect(100f, 0f, 200f, 100f),
            c to Rect(200f, 0f, 300f, 100f),
            w to Rect(0f, 100f, 200f, 300f),
        )
    private val all = listOf<HomeItem>(a, b, c, w)

    @Test
    fun `dropping on the middle of another app merges`() {
        assertEquals(DropAction.Merge("a", "b"), dropAction(a, Offset(150f, 50f), page, all, null))
    }

    @Test
    fun `dropping near the side inserts, after the target when moving forward and before it when moving back`() {
        assertEquals(DropAction.Move("a", "c"), dropAction(a, Offset(110f, 50f), page, all, null)) // avanti: dopo b, cioè prima di c
        assertEquals(DropAction.Move("c", "b"), dropAction(c, Offset(110f, 50f), page, all, null)) // indietro: prima di b
    }

    @Test
    fun `a widget is never merged into and apps dropped on it are inserted`() {
        assertEquals(DropAction.Move("a", null), dropAction(a, Offset(100f, 200f), page, all, null))
    }

    @Test
    fun `dropping in an empty cell goes to the end of the page, and on itself does nothing`() {
        assertEquals(DropAction.Move("a", "next"), dropAction(a, Offset(280f, 250f), page, all, "next"))
        assertEquals(DropAction.None, dropAction(a, Offset(50f, 50f), page, all, null))
    }

    @Test
    fun `a folder accepts an app on its middle, a widget never merges`() {
        val f = FolderItem("f", "F", keys("x", "y"))
        val p = listOf(a to Rect(0f, 0f, 100f, 100f), f to Rect(100f, 0f, 200f, 100f))
        assertEquals(DropAction.Merge("a", "f"), dropAction(a, Offset(150f, 50f), p, listOf(a, f), null))
        assertEquals(DropAction.Move("w", "a"), dropAction(w, Offset(50f, 50f), p, listOf(a, f, w), null))
    }
}
