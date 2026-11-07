package it.manlio.nova.core.system

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsParserTest {
    @Test
    fun `only open floating windows are kept`() {
        val text =
            """
            * RecentTaskInfo #0:
                id=705 userId=0 hasTask=true lastActiveTime=97028001
                realActivity={it.manlio.nova/it.manlio.nova.MainActivity}
                isExcluded=false activityType=standard windowingMode=freeform supportsMultiWindow=true
            * RecentTaskInfo #1:
                id=686 userId=0 hasTask=true lastActiveTime=91583168
                realActivity={com.android.chrome/org.chromium.chrome.browser.ChromeTabbedActivity}
                isExcluded=false activityType=standard windowingMode=fullscreen supportsMultiWindow=true
            * RecentTaskInfo #2:
                id=600 userId=0 hasTask=false lastActiveTime=1
                realActivity={com.example.old/com.example.old.Main}
                isExcluded=false activityType=standard windowingMode=freeform supportsMultiWindow=true
            """.trimIndent()

        assertEquals(listOf("it.manlio.nova/it.manlio.nova.MainActivity"), RecentsParser.openWindows(text))
    }
}
