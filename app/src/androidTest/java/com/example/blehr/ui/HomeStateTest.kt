package com.example.blehr.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blehr.data.ProfileStore
import com.example.blehr.data.Repo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real Compose wiring regression: changing the BLE input must change the visible home assessment. */
class HomeStateTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableSetOf<String>()
    private val prefix = "ui_test_${UUID.randomUUID()}_"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            names += prefix + name
            return base.getSharedPreferences(prefix + name, mode)
        }
    }

    @After fun cleanup() { names.forEach { base.deleteSharedPreferences(it) } }

    @Test fun personaRouteAndUnsavedDraftSurviveRecreation() {
        ProfileStore.markSeen(context)
        Repo.init(context)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val resultOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            val backOwner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            CompositionLocalProvider(LocalContext provides context,
                LocalActivityResultRegistryOwner provides resultOwner,
                LocalOnBackPressedDispatcherOwner provides backOwner) {
                HuixueApp(null, "测试输入", null, emptyList(), null, null, false,
                    {}, {}, {}, {}, {}, {})
            }
        }
        compose.onAllNodesWithText("我的").onLast().performClick()
        compose.onNodeWithText("她的性格").performScrollTo().performClick()
        compose.onNodeWithText("我自己写").performScrollTo().performClick()
        compose.onNodeWithTag("persona-custom").performScrollTo().performTextReplacement("转屏后保留这份草稿")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("persona-custom").performScrollTo().assertTextEquals("转屏后保留这份草稿")
        compose.onNodeWithText("← 返回").performScrollTo().performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
    }

    @Test fun liveHeartRateChangesScoreAndDisconnectRemovesPenalty() {
        ProfileStore.markSeen(context)
        Repo.init(context)
        val hr = mutableStateOf<Int?>(65)
        compose.setContent {
            val resultOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            val backOwner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            CompositionLocalProvider(LocalContext provides context,
                LocalActivityResultRegistryOwner provides resultOwner,
                LocalOnBackPressedDispatcherOwner provides backOwner) {
                HuixueApp(hr.value, "测试输入", 65, emptyList(), null, null, false,
                    {}, {}, {}, {}, {}, {})
            }
        }
        fun score() = compose.onNodeWithTag("state-score").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text.toInt()
        compose.waitForIdle()
        val baseline = score()
        compose.runOnIdle { hr.value = 90 }
        compose.waitForIdle()
        assertEquals(baseline - 22, score())
        compose.runOnIdle { hr.value = null }
        compose.waitForIdle()
        assertEquals(baseline, score())
    }
}
