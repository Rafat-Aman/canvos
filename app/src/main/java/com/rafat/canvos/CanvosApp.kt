package com.rafat.canvos

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.webkit.CookieManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.rafat.canvos.data.Notifier
import com.rafat.canvos.data.Repository
import com.rafat.canvos.data.Session
import com.rafat.canvos.data.SessionStore
import com.rafat.canvos.data.SettingsStore
import com.rafat.canvos.data.SyncWorker
import com.rafat.canvos.data.api.CanvasApi
import com.rafat.canvos.data.api.NetworkState
import com.rafat.canvos.data.api.User
import com.rafat.canvos.data.offline.OfflineStore
import com.rafat.canvos.data.offline.OfflineSync
import com.rafat.canvos.data.offline.OfflineSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

class CanvosApp : Application(), SingletonImageLoader.Factory {
    lateinit var sessions: SessionStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var offline: OfflineStore
        private set

    private val _repo = MutableStateFlow<Repository?>(null)
    /** The signed-in account, or null when logged out. */
    val repo: StateFlow<Repository?> = _repo.asStateFlow()

    private val plainClient by lazy { OkHttpClient() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessions = SessionStore(this)
        settings = SettingsStore(this)
        offline = OfflineStore(this)
        watchNetwork()
        sessions.load()?.let { _repo.value = Repository(api(it)) }
        if (_repo.value != null) SyncWorker.schedule(this)
    }

    private fun api(s: Session) = CanvasApi(
        s.domain, s.token,
        readOnly = BuildConfig.DEBUG && settings.state.value.readOnly,
        cache = offline.responses,
    )

    /** Keeps [NetworkState.hasNetwork] current so offline screens use saved data at once. */
    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        fun hasInternet(n: Network?) =
            n != null && cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        NetworkState.hasNetwork.value = hasInternet(cm.activeNetwork)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    NetworkState.hasNetwork.value = true
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    NetworkState.hasNetwork.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                override fun onLost(network: Network) {
                    NetworkState.hasNetwork.value = false
                }
            })
        }
    }

    /** [user] is the profile the token was checked with on the login screen. */
    fun signIn(session: Session, user: User) {
        // Saved offline content stays when the same person signs in again (e.g. new token).
        offline.claim(OfflineSync.ownerOf(session.domain, user.id))
        sessions.save(session)
        _repo.value = Repository(api(session))
        SyncWorker.schedule(this)
        SyncWorker.runOnce(this)
        OfflineSyncWorker.schedule(this)
        OfflineSyncWorker.syncNow(this, userAsked = false)
    }

    /** Rebuild the client, e.g. after toggling debug read-only mode. */
    fun reconnect() {
        sessions.load()?.let { _repo.value = Repository(api(it)) }
    }

    /**
     * [keepOffline] keeps saved courses when the token merely expired; logging out on
     * purpose removes everything the account left on the phone.
     */
    fun signOut(keepOffline: Boolean = false) {
        SyncWorker.cancelAll(this)
        OfflineSyncWorker.cancel(this)
        sessions.clear()
        _repo.value = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        cacheDir.listFiles()?.filter { it.name.startsWith("upload") || it.name.startsWith("photo") }?.forEach { it.delete() }
        if (!keepOffline) {
            offline.wipe()
            File(cacheDir, "downloads").deleteRecursively()
            runCatching { SingletonImageLoader.get(this).diskCache?.clear() }
            Notifier.reset(this)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            // Course images and avatars on the Canvas host need the token; others must not get it.
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { _repo.value?.api?.http ?: plainClient })) }
            // Kept with the offline content (not in the cache folder) so images show offline.
            .diskCache {
                DiskCache.Builder()
                    .directory(File(filesDir, "offline/images").toOkioPath())
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: CanvosApp
            private set
    }
}
