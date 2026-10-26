package com.codecrafters.leaktrace.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.ui.account.AccountScreen
import com.codecrafters.leaktrace.ui.email.EmailActivityScreen
import com.codecrafters.leaktrace.ui.home.HomeScreen
import com.codecrafters.leaktrace.ui.mobile.MobileViewModel
import com.codecrafters.leaktrace.ui.server.ServerViewModel
import com.codecrafters.leaktrace.ui.settings.SettingsScreen
import com.codecrafters.leaktrace.ui.sms.SmsInvestigationScreen
import com.codecrafters.leaktrace.ui.sms.SmsInvestigationViewModel
import com.codecrafters.leaktrace.ui.timeline.TimelineScreen
import com.codecrafters.leaktrace.ui.websites.WebsitesScreen
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability

/**
 * @param sharedText text shared into LeakTrace from another app, shown on the SMS screen.
 * @param onSharedTextConsumed called once [sharedText] has been handed to the SMS screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeakTraceApp(
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    navController: NavHostController = rememberNavController(),
    // Activity-scoped so every tab shares one server check and the SMS draft survives tab changes.
    serverViewModel: ServerViewModel = viewModel(factory = ServerViewModel.Factory),
    smsViewModel: SmsInvestigationViewModel = viewModel(),
    mobileViewModel: MobileViewModel = viewModel(factory = MobileViewModel.Factory),
) {
    val serverState by serverViewModel.state.collectAsStateWithLifecycle()
    val session by mobileViewModel.session.collectAsStateWithLifecycle()
    val pairingError by mobileViewModel.pairingError.collectAsStateWithLifecycle()
    val websites by mobileViewModel.websites.collectAsStateWithLifecycle()
    val websiteSnapshot by mobileViewModel.websiteSnapshot.collectAsStateWithLifecycle()
    val emails by mobileViewModel.emails.collectAsStateWithLifecycle()
    val emailSnapshot by mobileViewModel.emailSnapshot.collectAsStateWithLifecycle()
    val timeline by mobileViewModel.timeline.collectAsStateWithLifecycle()
    val smsRemote by mobileViewModel.sms.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = LeakTraceDestination.fromRoute(backStackEntry?.destination?.route)

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            smsViewModel.onSharedText(sharedText)
            mobileViewModel.clearSms()
            navController.navigateToTopLevel(LeakTraceDestination.Sms)
            onSharedTextConsumed()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(current.title)) },
                navigationIcon = {
                    if (!current.inBottomBar) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (current.inBottomBar) {
                        IconButton(onClick = { navController.navigate(LeakTraceDestination.Settings.route) { launchSingleTop = true } }) {
                            Icon(Icons.Filled.Settings, stringResource(R.string.nav_settings))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current.inBottomBar) {
                NavigationBar {
                    LeakTraceDestination.bottomBar.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == current,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.navLabel)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LeakTraceDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(LeakTraceDestination.Home.route) {
                HomeScreen(
                    serverState = serverState,
                    session = session,
                    onRetry = { serverViewModel.refresh(); mobileViewModel.refreshSession() },
                    onOpenSection = navController::navigateToTopLevel,
                    onOpenAccount = { navController.navigate(LeakTraceDestination.Account.route) { launchSingleTop = true } },
                )
            }
            composable(LeakTraceDestination.Websites.route) {
                LaunchedEffect(Unit) {
                    mobileViewModel.refreshWebsitesIfStale()
                    while (true) { delay(30_000); mobileViewModel.refreshWebsitesIfStale() }
                }
                WebsitesScreen(serverState, websites, snapshot = websiteSnapshot,
                    onRetry = { serverViewModel.refresh(); mobileViewModel.refreshWebsites() },
                    onOpenAccount = { navController.navigate(LeakTraceDestination.Account.route) })
            }
            composable(LeakTraceDestination.Email.route) {
                LaunchedEffect(Unit) {
                    mobileViewModel.refreshEmailsIfStale()
                    while (true) { delay(30_000); mobileViewModel.refreshEmailsIfStale() }
                }
                EmailActivityScreen(serverState, emails, snapshot = emailSnapshot,
                    onRetry = { serverViewModel.refresh(); mobileViewModel.refreshEmails() },
                    onOpenAccount = { navController.navigate(LeakTraceDestination.Account.route) })
            }
            composable(LeakTraceDestination.Sms.route) {
                SmsInvestigationScreen(viewModel = smsViewModel, session = session, websites = websites, serverAnalysisAvailable = (session as? com.codecrafters.leaktrace.ui.mobile.MobileSessionState.Connected)?.account?.scopes?.contains("sms:analyze") == true, remote = smsRemote, onAnalyzeRemote = mobileViewModel::analyzeSms, onClearRemote = mobileViewModel::clearSms)
            }
            composable(LeakTraceDestination.Timeline.route) {
                TimelineScreen(serverState, timeline, onRetry = { serverViewModel.refresh(); mobileViewModel.refreshTimeline() }, onOpenAccount = { navController.navigate(LeakTraceDestination.Account.route) })
            }
            composable(LeakTraceDestination.Settings.route) {
                SettingsScreen(
                    serverState,
                    session = session,
                    onRetry = serverViewModel::refresh,
                    onOpenAccount = { navController.navigate(LeakTraceDestination.Account.route) { launchSingleTop = true } },
                )
            }
            composable(LeakTraceDestination.Account.route) {
                AccountScreen(serverState, session, pairingError, onRetry = { serverViewModel.refresh(); mobileViewModel.refreshSession() }, onStartPairing = mobileViewModel::startPairing, onCheckPairing = mobileViewModel::checkPairing, onSignOut = mobileViewModel::signOut, onLogin = mobileViewModel::login)
            }
        }
    }
}

/** Switches tabs without stacking duplicates, keeping each tab's state, so Back always returns Home. */
private fun NavHostController.navigateToTopLevel(destination: LeakTraceDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
