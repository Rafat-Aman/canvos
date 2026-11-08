package com.rafat.canvos.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.data.Repository
import com.rafat.canvos.data.api.NetworkState
import com.rafat.canvos.data.api.UnauthorizedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Failed(val message: String, val unauthorized: Boolean = false) : Load<Nothing>
}

data class LoadState<T>(
    val load: Load<T> = Load.Loading,
    val refreshing: Boolean = false,
    /** Shown from the offline copy rather than fresh from Canvas. */
    val saved: Boolean = false,
) {
    val data: T? get() = (load as? Load.Ok<T>)?.data
}

/** Holds one async result for a screen; survives rotation; supports refresh. */
class LoaderViewModel<T>(private val loader: suspend (force: Boolean) -> T) : ViewModel() {
    private val _state = MutableStateFlow(LoadState<T>())
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load(force = false)
        // Back online after showing saved data: refresh by itself.
        viewModelScope.launch {
            NetworkState.hasNetwork.drop(1).collect { online ->
                if (online && _state.value.saved && job?.isActive != true) load(force = true)
            }
        }
    }

    fun refresh() = load(force = true)

    /** Update the loaded value locally (optimistic UI). */
    fun update(f: (T) -> T) {
        val cur = _state.value.data ?: return
        _state.value = _state.value.copy(load = Load.Ok(f(cur)))
    }

    private fun load(force: Boolean) {
        job?.cancel()
        val hasData = _state.value.data != null
        _state.value = if (hasData) _state.value.copy(refreshing = true) else LoadState(Load.Loading)
        job = viewModelScope.launch {
            try {
                val before = NetworkState.savedServed
                val d = loader(force)
                _state.value = LoadState(Load.Ok(d), saved = NetworkState.savedServed != before)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failed = Load.Failed(e.userMessage(), e is UnauthorizedException)
                // Keep showing old data if a refresh fails; surface the error otherwise.
                _state.value = if (hasData) _state.value.copy(refreshing = false) else LoadState(failed)
                if (e is UnauthorizedException) AuthEvents.expired()
                if (hasData) ErrorEvents.emit(failed.message)
            }
        }
    }
}

fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

/** Repository of the signed-in account. Screens are only shown while signed in. */
fun repo(): Repository = CanvosApp.instance.repo.value ?: error("Not signed in")

/**
 * Creates (or reuses) a loader for [key]. `force` is true on pull-to-refresh so
 * caches can be bypassed.
 */
@Composable
fun <T> rememberLoader(key: String, loader: suspend Repository.(force: Boolean) -> T): LoaderViewModel<T> {
    val r = repo()
    return viewModel(key = key + "@" + System.identityHashCode(r)) { LoaderViewModel { force -> r.loader(force) } }
}

/** Spinner / error with retry / content with pull-to-refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Loaded(
    vm: LoaderViewModel<T>,
    modifier: Modifier = Modifier,
    /** Screens whose content runs under the status bar show [SavedBanner] themselves. */
    savedBanner: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    val st by vm.state.collectAsState()
    when (val l = st.load) {
        is Load.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Failed -> ErrorState(l.message, modifier) { vm.refresh() }
        is Load.Ok -> Column(modifier.fillMaxSize()) {
            if (st.saved && savedBanner) SavedBanner()
            PullToRefreshBox(
                isRefreshing = st.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { content(l.data) }
        }
    }
}

/** Thin strip telling the user they are looking at the offline copy. */
@Composable
fun SavedBanner(modifier: Modifier = Modifier) {
    val online by NetworkState.hasNetwork.collectAsState()
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CloudOff, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (online) "Couldn't reach Canvas · showing saved content" else "Offline · showing saved content",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String? = null, modifier: Modifier = Modifier, inList: Boolean = false) {
    // Scrollable so pull-to-refresh still works on empty screens; inside a list it must not scroll itself.
    Column(
        (if (inList) modifier.fillMaxWidth() else modifier.fillMaxSize().verticalScroll(rememberScrollState())).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Token expiry is global: any screen hitting 401 sends the user back to login. */
object AuthEvents {
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()
    fun expired() { _expired.value = true }
    fun reset() { _expired.value = false }
}

/** Non-fatal errors (e.g. a refresh failed) shown as a snackbar. */
object ErrorEvents {
    private val _events = MutableStateFlow<Pair<Long, String>?>(null)
    val events: StateFlow<Pair<Long, String>?> = _events.asStateFlow()
    fun emit(msg: String) { _events.value = System.nanoTime() to msg }
}
