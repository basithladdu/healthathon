package `in`.wedevit.care

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Material 3 pull-to-refresh around a scrolling list: pull down, the round spinner shows until [refreshing] is
 * false again. The spinner is only for a pull; the app's own refreshes (on opening) stay quiet. The screen's
 * Refresh button stays as it is for TalkBack and for anyone who does not pull. [enabled] false: no gesture.
 */
@Composable fun SaharaPullRefresh(refreshing: StateFlow<Boolean>, onRefresh: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var pulled by remember { mutableStateOf(false) }
    Box(modifier.pullToRefresh(isRefreshing = pulled, state = state, enabled = enabled, onRefresh = {
        scope.launch {
            pulled = true
            // onRefresh marks refreshing at once when it starts; if it did not start (busy), the spinner just closes.
            try { onRefresh(); refreshing.first { !it } } finally { pulled = false }
        }
    })) {
        content()
        if (enabled) PullToRefreshDefaults.Indicator(state = state, isRefreshing = pulled, modifier = Modifier.align(Alignment.TopCenter), containerColor = Paper, color = Rust)
    }
}
