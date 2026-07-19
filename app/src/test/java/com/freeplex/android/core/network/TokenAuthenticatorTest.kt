package com.freeplex.android.core.network

import com.freeplex.android.core.preferences.SessionStore
import com.freeplex.android.core.preferences.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val json = Json { ignoreUnknownKeys = true }
    private val authenticator = TokenAuthenticator(sessionStore, settingsRepository, json)
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        every { settingsRepository.currentBaseUrl() } returns
            server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun tokenAlreadyRefreshedByAnotherThread_retriesWithCurrentToken_withoutRefreshCall() {
        every { sessionStore.accessToken } returns "new-token"
        every { sessionStore.refreshToken } returns "refresh-1"

        val result = authenticator.authenticate(null, unauthorized(sentToken = "old-token"))

        assertThat(result).isNotNull()
        assertThat(result!!.header("Authorization")).isEqualTo("Bearer new-token")
        // No refresh endpoint call — the fresh token is reused as-is.
        assertThat(server.requestCount).isEqualTo(0)
        verify(exactly = 0) { sessionStore.updateTokens(any(), any()) }
    }

    @Test
    fun refreshSucceeds_updatesTokens_andRetriesWithNewAccessToken() {
        every { sessionStore.accessToken } returns "old-token"
        every { sessionStore.refreshToken } returns "refresh-1"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"accessToken":"new-access","refreshToken":"new-refresh"}"""),
        )

        val result = authenticator.authenticate(null, unauthorized(sentToken = "old-token"))

        assertThat(result).isNotNull()
        assertThat(result!!.header("Authorization")).isEqualTo("Bearer new-access")
        assertThat(server.requestCount).isEqualTo(1)
        verify { sessionStore.updateTokens("new-access", "new-refresh") }
    }

    @Test
    fun refreshRejectedByServer_clearsSession_andGivesUp() {
        every { sessionStore.accessToken } returns "old-token"
        every { sessionStore.refreshToken } returns "refresh-1"
        server.enqueue(MockResponse().setResponseCode(401))

        val result = authenticator.authenticate(null, unauthorized(sentToken = "old-token"))

        assertThat(result).isNull()
        verify { sessionStore.clear() }
    }

    @Test
    fun secondFailedAttempt_givesUpWithoutRefreshing() {
        every { sessionStore.accessToken } returns "old-token"
        every { sessionStore.refreshToken } returns "refresh-1"
        val first = unauthorized(sentToken = "old-token")
        val second = first.newBuilder().priorResponse(first).build()

        val result = authenticator.authenticate(null, second)

        assertThat(result).isNull()
        assertThat(server.requestCount).isEqualTo(0)
    }

    private fun unauthorized(sentToken: String): Response {
        val request = Request.Builder()
            .url("http://example.test/api/v1/home")
            .header("Authorization", "Bearer $sentToken")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
    }
}
