package com.codecrafters.leaktrace.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.server.BackendFeature

/** App sections. Those with [inBottomBar] are top-level tabs; Settings opens from the top bar. */
enum class LeakTraceDestination(
    val route: String,
    @param:StringRes val navLabel: Int,
    @param:StringRes val title: Int,
    val icon: ImageVector,
    /** Server feature the section's data comes from, if any. */
    val feature: BackendFeature?,
    val inBottomBar: Boolean = true,
) {
    Home("home", R.string.nav_home, R.string.app_name, Icons.Filled.Home, null),
    Websites("websites", R.string.nav_websites, R.string.title_websites, Icons.AutoMirrored.Filled.List, BackendFeature.Websites),
    Email("email", R.string.nav_email, R.string.title_email, Icons.Filled.Email, BackendFeature.Email),
    Sms("sms", R.string.nav_sms, R.string.title_sms, Icons.Filled.Search, BackendFeature.Sms),
    Calls("calls", R.string.nav_calls, R.string.title_calls, Icons.Filled.Search, null, inBottomBar = false),
    Timeline("timeline", R.string.nav_timeline, R.string.title_timeline, Icons.Filled.DateRange, BackendFeature.Timeline),
    Settings("settings", R.string.nav_settings, R.string.title_settings, Icons.Filled.Settings, null, inBottomBar = false),
    Account("account", R.string.nav_account, R.string.title_account, Icons.Filled.Person, BackendFeature.Account, inBottomBar = false);

    companion object {
        val bottomBar: List<LeakTraceDestination> = entries.filter { it.inBottomBar }

        fun fromRoute(route: String?): LeakTraceDestination =
            entries.firstOrNull { it.route == route } ?: Home
    }
}
