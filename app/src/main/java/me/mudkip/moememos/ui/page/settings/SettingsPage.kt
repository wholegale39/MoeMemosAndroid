package me.mudkip.moememos.ui.page.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoEditGesture
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.data.model.UserSettings
import me.mudkip.moememos.data.model.displayTitle
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.settingsDataStore
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.notification.DailyReviewNotifier
import me.mudkip.moememos.notification.DailyReviewReminderScheduler
import me.mudkip.moememos.ui.component.MemosIcon
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.security.AppLockAuthenticator
import me.mudkip.moememos.ui.security.AppLockSession
import me.mudkip.moememos.viewmodel.LocalUserState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    navController: NavHostController
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val userStateViewModel = LocalUserState.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val accounts by userStateViewModel.accounts.collectAsState()
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val settings by context.settingsDataStore.data.collectAsState(initial = Settings())
    val appLockSupported = remember(context, AppLockSession.foregroundGeneration) {
        AppLockAuthenticator.canAuthenticate(context)
    }
    var showEditGestureDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var showDailyReviewTimeDialog by remember { mutableStateOf(false) }

    fun setAppLockEnabled(enabled: Boolean) {
        if (enabled && !appLockSupported) {
            return
        }
        if (enabled) {
            AppLockSession.lock()
        }
        scope.launch(Dispatchers.IO) {
            context.settingsDataStore.updateData { existingSettings ->
                existingSettings.copy(appLockEnabled = enabled)
            }
        }
    }
    val currentEditGesture = settings.usersList
        .firstOrNull { it.accountKey == settings.currentUser }
        ?.settings
        ?.editGesture
        ?: MemoEditGesture.NONE

    val currentUserSettings = settings.usersList
        .firstOrNull { it.accountKey == settings.currentUser }
        ?.settings
    val dailyReviewEnabled = currentUserSettings?.dailyReviewReminderEnabled ?: false
    val dailyReviewHour = currentUserSettings?.dailyReviewReminderHour ?: 21
    val dailyReviewMinute = currentUserSettings?.dailyReviewReminderMinute ?: 0

    fun updateCurrentUserSettings(transform: (UserSettings) -> UserSettings) {
        scope.launch(Dispatchers.IO) {
            context.settingsDataStore.updateData { existingSettings ->
                val userIndex = existingSettings.usersList.indexOfFirst { user ->
                    user.accountKey == existingSettings.currentUser
                }
                if (userIndex == -1) {
                    return@updateData existingSettings
                }
                val users = existingSettings.usersList.toMutableList()
                users[userIndex] = users[userIndex].copy(
                    settings = transform(users[userIndex].settings)
                )
                existingSettings.copy(usersList = users)
            }
        }
    }

    fun setDailyReviewReminderEnabled(enabled: Boolean) {
        updateCurrentUserSettings { it.copy(dailyReviewReminderEnabled = enabled) }
        if (enabled) {
            DailyReviewNotifier.createChannel(context)
            DailyReviewReminderScheduler.scheduleNext(context, dailyReviewHour, dailyReviewMinute)
        } else {
            DailyReviewReminderScheduler.cancel(context)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setDailyReviewReminderEnabled(true)
        }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = R.string.settings.string) },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = R.string.back.string)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            item {
                Text(
                    R.string.accounts.string,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            accounts.forEach { account ->
                when (account) {
                    is Account.MemosV0 -> item {
                        SettingItem(
                            icon = MemosIcon,
                            text = account.info.displayTitle(),
                            subtitle = account.info.host,
                            trailingIcon = {
                                if (currentAccount?.accountKey() == account.accountKey()) {
                                    Icon(Icons.Outlined.Check,
                                        contentDescription = R.string.selected.string,
                                        modifier = Modifier.padding(start = 16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }) {
                            navController.navigate("${RouteName.ACCOUNT}?accountKey=${account.accountKey()}")
                        }
                    }
                    is Account.MemosV1 -> item {
                        SettingItem(
                            icon = MemosIcon,
                            text = account.info.displayTitle(),
                            subtitle = account.info.host,
                            trailingIcon = {
                                if (currentAccount?.accountKey() == account.accountKey()) {
                                    Icon(Icons.Outlined.Check,
                                        contentDescription = R.string.selected.string,
                                        modifier = Modifier.padding(start = 16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }) {
                            navController.navigate("${RouteName.ACCOUNT}?accountKey=${account.accountKey()}")
                        }
                    }
                    is Account.Local -> item {
                        SettingItem(icon = Icons.Outlined.Home, text = R.string.local_account.string, trailingIcon = {
                            if (currentAccount?.accountKey() == account.accountKey()) {
                                Icon(Icons.Outlined.Check,
                                    contentDescription = R.string.selected.string,
                                    modifier = Modifier.padding(start = 16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }) {
                            navController.navigate("${RouteName.ACCOUNT}?accountKey=${account.accountKey()}")
                        }
                    }
                }
            }

            item {
                SettingItem(icon = Icons.Outlined.PersonAdd, text = R.string.add_account.string) {
                    navController.navigate(RouteName.ADD_ACCOUNT)
                }
            }

            item {
                Text(
                    R.string.preferences.string,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            item {
                SettingItem(
                    icon = Icons.Outlined.Edit,
                    text = R.string.edit_gesture.string,
                    trailingIcon = {
                        Text(
                            text = currentEditGesture.titleResource.string,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                ) {
                    showEditGestureDialog = true
                }
            }

            item {
                SettingItem(
                    icon = Icons.Outlined.Notifications,
                    text = R.string.daily_review_reminder.string,
                    subtitle = R.string.daily_review_reminder_summary.string,
                    trailingIcon = {
                        Switch(
                            checked = dailyReviewEnabled,
                            onCheckedChange = null,
                        )
                    }
                ) {
                    if (dailyReviewEnabled) {
                        setDailyReviewReminderEnabled(false)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            setDailyReviewReminderEnabled(true)
                        }
                    }
                }
            }

            if (dailyReviewEnabled) {
                item {
                    SettingItem(
                        icon = Icons.Outlined.Notifications,
                        text = R.string.daily_review_reminder_time.string,
                        trailingIcon = {
                            Text(
                                text = String.format(
                                    java.util.Locale.getDefault(),
                                    "%02d:%02d",
                                    dailyReviewHour,
                                    dailyReviewMinute
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    ) {
                        showDailyReviewTimeDialog = true
                    }
                }
            }

            item {
                SettingItem(
                    icon = Icons.Outlined.AutoAwesome,
                    text = R.string.ai_settings.string,
                    subtitle = R.string.ai_settings_summary.string,
                ) {
                    showAiSettingsDialog = true
                }
            }

            item {
                Text(
                    R.string.security.string,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            item {
                val appLockToggleEnabled = appLockSupported || settings.appLockEnabled
                SettingItem(
                    icon = Icons.Outlined.Lock,
                    text = R.string.app_lock.string,
                    subtitle = if (appLockSupported) {
                        R.string.app_lock_summary.string
                    } else {
                        R.string.app_lock_unavailable_short.string
                    },
                    trailingIcon = {
                        Switch(
                            checked = settings.appLockEnabled,
                            onCheckedChange = null,
                            enabled = appLockToggleEnabled,
                        )
                    },
                    enabled = appLockToggleEnabled,
                ) {
                    setAppLockEnabled(!settings.appLockEnabled)
                }
            }

            item {
                Text(
                    R.string.about.string,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            item {
                SettingItem(icon = Icons.Outlined.Web, text = R.string.website.string) {
                    uriHandler.openUri("https://memos.moe")
                }
            }

            item {
                SettingItem(icon = Icons.Outlined.Lock, text = R.string.privacy_policy.string) {
                    uriHandler.openUri("https://memos.moe/privacy")
                }
            }

            item {
                SettingItem(icon = Icons.Outlined.Source, text = R.string.acknowledgements.string) {
                    uriHandler.openUri("https://memos.moe/android-acknowledgements")
                }
            }

            item {
                SettingItem(icon = Icons.Outlined.BugReport, text = R.string.report_an_issue.string) {
                    uriHandler.openUri("https://github.com/mudkipme/MoeMemosAndroid/issues")
                }
            }
        }
    }

    if (showEditGestureDialog) {
        AlertDialog(
            onDismissRequest = { showEditGestureDialog = false },
            title = { Text(R.string.edit_gesture.string) },
            text = {
                LazyColumn {
                    items(MemoEditGesture.entries.size) { index ->
                        val gesture = MemoEditGesture.entries[index]
                        TextButton(
                            onClick = {
                                showEditGestureDialog = false
                                scope.launch(Dispatchers.IO) {
                                    context.settingsDataStore.updateData { existingSettings ->
                                        val userIndex = existingSettings.usersList.indexOfFirst { user ->
                                            user.accountKey == existingSettings.currentUser
                                        }
                                        if (userIndex == -1) {
                                            return@updateData existingSettings
                                        }
                                        val users = existingSettings.usersList.toMutableList()
                                        val user = users[userIndex]
                                        users[userIndex] = user.copy(
                                            settings = user.settings.copy(editGesture = gesture)
                                        )
                                        existingSettings.copy(usersList = users)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = gesture.titleResource.string,
                                color = if (gesture == currentEditGesture) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditGestureDialog = false }) {
                    Text(R.string.close.string)
                }
            }
        )
    }

    if (showAiSettingsDialog) {
        AiSettingsDialog(
            onDismiss = { showAiSettingsDialog = false },
        )
    }

    if (showDailyReviewTimeDialog) {
        DailyReviewTimeDialog(
            initialHour = dailyReviewHour,
            initialMinute = dailyReviewMinute,
            onDismiss = { showDailyReviewTimeDialog = false },
            onConfirm = { hour, minute ->
                updateCurrentUserSettings {
                    it.copy(
                        dailyReviewReminderHour = hour,
                        dailyReviewReminderMinute = minute
                    )
                }
                DailyReviewReminderScheduler.scheduleNext(context, hour, minute)
                showDailyReviewTimeDialog = false
            }
        )
    }
}

@Composable
private fun DailyReviewTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val presets = listOf(
        8 to 0,
        12 to 0,
        18 to 0,
        20 to 0,
        21 to 0,
        22 to 0,
        23 to 0
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(R.string.daily_review_reminder_time.string) },
        text = {
            LazyColumn {
                presets.forEach { (hour, minute) ->
                    item {
                        TextButton(
                            onClick = { onConfirm(hour, minute) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute),
                                color = if (hour == initialHour && minute == initialMinute) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(R.string.cancel.string) }
        }
    )
}

@Composable
private fun AiSettingsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { me.mudkip.moememos.data.service.AiSettingsStorage(context) }
    val current by storage.settings.collectAsState(initial = me.mudkip.moememos.data.service.AiSettings("", "", me.mudkip.moememos.data.service.AiSettingsStorage.DEFAULT_MODEL))
    var endpoint by remember(current) { mutableStateOf(current.endpoint) }
    var apiKey by remember(current) { mutableStateOf(current.apiKey) }
    var model by remember(current) { mutableStateOf(current.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(R.string.ai_settings.string) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    R.string.ai_settings_summary.string,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text(R.string.ai_settings_api_endpoint.string) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(R.string.ai_settings_api_key.string) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(R.string.ai_settings_model.string) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    storage.update(endpoint, apiKey, model)
                    onDismiss()
                }
            }) { Text(R.string.confirm.string) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(R.string.cancel.string) }
        }
    )
}

private val MemoEditGesture.titleResource: Int
    get() = when (this) {
        MemoEditGesture.NONE -> R.string.edit_gesture_none
        MemoEditGesture.SINGLE -> R.string.edit_gesture_single
        MemoEditGesture.DOUBLE -> R.string.edit_gesture_double
        MemoEditGesture.LONG -> R.string.edit_gesture_long
    }
