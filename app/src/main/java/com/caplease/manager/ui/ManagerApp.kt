package com.caplease.manager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caplease.manager.ui.screens.AuditScreen
import com.caplease.manager.ui.screens.ExecScreen
import com.caplease.manager.ui.screens.GrantScreen
import com.caplease.manager.ui.screens.LeasesScreen
import com.caplease.manager.ui.screens.OverviewScreen
import com.caplease.manager.ui.screens.SessionScreen

private enum class Destination(val label: String, val icon: ImageVector) {
    Overview("状态", Icons.Filled.Shield),
    Session("会话", Icons.Filled.Lock),
    Grant("签发", Icons.Filled.VerifiedUser),
    Leases("租约", Icons.Filled.Bolt),
    Exec("执行", Icons.Filled.Gavel),
    Audit("审计", Icons.Filled.ReceiptLong)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerApp(viewModel: ManagerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var destination by rememberSaveable { mutableStateOf(Destination.Overview.name) }
    val current = Destination.valueOf(destination)

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("租约式权限管理") },
                actions = {
                    if (state.busy) {
                        Box(modifier = Modifier.padding(end = 16.dp)) {
                            CircularProgressIndicator()
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = current == item,
                        onClick = { destination = item.name },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopStart
        ) {
            when (current) {
                Destination.Overview -> OverviewScreen(
                    state = state,
                    onRefresh = viewModel::refreshKernel
                )
                Destination.Session -> SessionScreen(
                    state = state,
                    onPair = viewModel::pair,
                    onUnpair = viewModel::unpair,
                    onOpen = viewModel::openSession,
                    onClose = viewModel::closeSession
                )
                Destination.Grant -> GrantScreen(
                    state = state,
                    onToggle = viewModel::toggleCapability,
                    onSelectAll = viewModel::selectAllCapabilities,
                    onClear = viewModel::clearCapabilities,
                    onDuration = viewModel::updateDuration,
                    onOneShot = viewModel::toggleOneShot,
                    onInheritable = viewModel::toggleInheritable,
                    onPackage = viewModel::updatePackageName,
                    onGrant = viewModel::acquireLease
                )
                Destination.Leases -> LeasesScreen(
                    state = state,
                    onRelease = viewModel::releaseLease,
                    onExtend = { id -> viewModel.extendLease(id, DEFAULT_EXTENSION_SECONDS) },
                    onRevokeAll = viewModel::revokeAll
                )
                Destination.Exec -> ExecScreen(
                    state = state,
                    onCommand = viewModel::updateExecCommand,
                    onToggleCapability = viewModel::toggleExecCapability,
                    onTimeout = viewModel::updateExecTimeout,
                    onRun = viewModel::executeOnce
                )
                Destination.Audit -> AuditScreen(
                    state = state,
                    onRefresh = viewModel::refreshAudit,
                    onClear = viewModel::clearAudit
                )
            }
        }
    }
}

private const val DEFAULT_EXTENSION_SECONDS = 300L