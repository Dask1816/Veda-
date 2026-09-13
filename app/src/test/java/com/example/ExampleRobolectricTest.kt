package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.action.DeviceActionManager
import com.example.data.models.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string resource from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Veda", appName)
    }

    @Test
    fun `verify assistant states enum`() {
        assertEquals("IDLE", AssistantState.IDLE.name)
        assertEquals("LISTENING", AssistantState.LISTENING.name)
        assertEquals("SPEAKING", AssistantState.SPEAKING.name)
        assertEquals("CONNECTING", AssistantState.CONNECTING.name)
        assertEquals("ERROR", AssistantState.ERROR.name)
    }

    @Test
    fun `verify device action manager initialization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actionManager = DeviceActionManager(context)
        val result = actionManager.openUrl("https://ai.google.dev")
        assertNotNull(result)
        assertEquals("openUrl", result.action)
    }
}
