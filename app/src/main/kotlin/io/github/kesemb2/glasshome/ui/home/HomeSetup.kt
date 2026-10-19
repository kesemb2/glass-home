package io.github.kesemb2.glasshome.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether Glass Home owns the Home button, and the actions in Settings › Home and startup. */
@Stable
class HomeSetup(
    val requestDefaultHome: () -> Unit,
    val openHelperSettings: () -> Unit,
    val openTvHome: () -> Unit,
    val openTvSettings: () -> Unit,
) {
    /** The system opens Glass Home for Home (it is the default home app). */
    var isDefaultHome by mutableStateOf(false)

    /** The Home helper (accessibility service) is switched on. */
    var helperEnabled by mutableStateOf(false)
}
