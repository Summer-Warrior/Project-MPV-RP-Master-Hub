package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.NexusNetworkClient
import com.example.data.util.BigIntMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Nexus Master Hub", appName)
    }

    @Test
    fun `bigInt math handles 21+ digit numbers without overflow`() {
        val max21 = "999999999999999999999"
        assertEquals("1000000000000000000000", BigIntMath.add(max21, "1"))
        assertEquals("999,999,999,999,999,999,999", BigIntMath.formatWithCommas(max21))
    }

    @Test
    fun `network client validates discord webhooks and rejects unsafe urls`() {
        val client = NexusNetworkClient()
        assertTrue(client.validateDiscordWebhookUrl("https://discord.com/api/webhooks/123/abc").isSuccess)
        assertTrue(client.validateDiscordWebhookUrl("https://evil.example.com/api/webhooks/123/abc").isFailure)
        assertTrue(client.validateSafeExternalHttpsUrl("http://localhost:8080/pack.json").isFailure)
    }
}
