package com.bubble.rikkahub.di

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.bubble.rikkahub.data.local.AppDatabase
import com.bubble.rikkahub.data.preferences.AppPreferences
import com.bubble.rikkahub.data.remote.ConnectionMonitor
import com.bubble.rikkahub.data.remote.RikkaHubApi
import com.bubble.rikkahub.data.repository.ChatRepository
import com.bubble.rikkahub.data.repository.ConversationRepository
import com.bubble.rikkahub.data.repository.CustomizationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

// Top-level singleton delegate. Must NOT live inside the class: every AppContainer
// recreation would then create a second DataStore on the same file and crash with
// "There are multiple DataStores active for the same file".
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppContainer(private val application: Application) {

    val appPreferences by lazy { AppPreferences(application.dataStore) }

    val database by lazy { AppDatabase.build(application) }
    val customizationDao by lazy { database.customizationDao() }
    val cachedConversationDao by lazy { database.cachedConversationDao() }
    val pendingMessageDao by lazy { database.pendingMessageDao() }

    /** App-lifetime scope for the connection monitor's background probing. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _api: RikkaHubApi? = null
    val api: RikkaHubApi get() { if (_api == null) _api = createApi(); return _api!! }

    fun recreateApi() { _api = createApi() }

    private fun createApi(): RikkaHubApi {
        val baseUrl = runBlocking { appPreferences.serverUrl.first() }
        Log.d("AppContainer", "Creating API client for: $baseUrl")
        val client = HttpClient(CIO) {
            // encodeDefaults=true is REQUIRED: TextPart.type defaults to "text" and would
            // otherwise be omitted from the JSON, making the server reject the send with a 500.
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
            // INFO instead of BODY: BODY logging would dump every chunk of the long-lived SSE streams
            install(Logging) { level = LogLevel.INFO }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            defaultRequest { url(baseUrl.trimEnd('/')); contentType(ContentType.Application.Json) }
        }
        // SSE streams are read with a plain HttpURLConnection (Ktor's CIO engine hangs on
        // streaming responses in this app), so the API only needs the main client + base URL.
        return RikkaHubApi(client, baseUrl)
    }

    val conversationRepository by lazy { ConversationRepository(api, cachedConversationDao) }
    val chatRepository by lazy { ChatRepository(api, pendingMessageDao) }
    val customizationRepository by lazy { CustomizationRepository(customizationDao) }

    val connectionMonitor by lazy {
        ConnectionMonitor(api, appScope) { chatRepository.flushPending() }.also {
            // Flush any messages queued during a previous offline session as soon as the app starts.
            appScope.launch { runCatching { chatRepository.flushPending() } }
        }
    }

    fun conversationListViewModel() =
        com.bubble.rikkahub.ui.screens.conversations.ConversationListViewModel(
            conversationRepository, customizationRepository, connectionMonitor, application, appPreferences
        )

    fun chatViewModel() =
        com.bubble.rikkahub.ui.screens.chat.ChatViewModel(
            chatRepository, conversationRepository, appPreferences, connectionMonitor, customizationRepository
        )

    fun settingsViewModel() =
        com.bubble.rikkahub.ui.screens.settings.SettingsViewModel(
            appPreferences, customizationRepository
        )
}
