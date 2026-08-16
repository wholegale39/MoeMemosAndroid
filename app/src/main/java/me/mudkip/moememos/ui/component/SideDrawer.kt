package me.mudkip.moememos.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun SideDrawer(
    memosNavController: NavHostController,
    drawerState: DrawerState? = null
) {
    val weekDays = remember {
        val day = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        List(DayOfWeek.entries.size) { index ->
            day.plus(index.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }
    var showHeatMap by remember {
        mutableStateOf(false)
    }
    var tagToManage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val hasExplore = currentAccount !is Account.Local
    val rootNavController = LocalRootNavController.current
    val navBackStackEntry by memosNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    fun isSelected(route: String): Boolean {
        return currentDestination?.hierarchy?.any { it.route == route } == true
    }

    fun isTagSelected(tag: String): Boolean {
        if (!isSelected("${RouteName.TAG}/{tag}")) return false

        val currentTag = navBackStackEntry?.arguments?.getString("tag")
        val encodedTag = URLEncoder.encode(tag, "UTF-8")
        return currentTag == tag || currentTag == encodedTag
    }

    LazyColumn {
        item {
            Stats()
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 5.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(weekDays[0],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text(weekDays[3],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text(weekDays[6],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
                if (showHeatMap) {
                    Heatmap()
                }
            }
        }

        item {
            Text(
                R.string.moe_memos.string,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(20.dp)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.memos.string) },
                icon = { Icon(Icons.Outlined.GridView, contentDescription = null) },
                selected = isSelected(RouteName.MEMOS),
                onClick = {
                    scope.launch {
                        memosNavController.navigate(RouteName.MEMOS) {
                            launchSingleTop = true
                            restoreState = true
                        }
                        drawerState?.close()
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        if (hasExplore) {
            item {
                NavigationDrawerItem(
                    label = { Text(R.string.explore.string) },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    selected = isSelected(RouteName.EXPLORE),
                    onClick = {
                        scope.launch {
                            memosNavController.navigate(RouteName.EXPLORE) {
                                launchSingleTop = true
                                restoreState = true
                            }
                            drawerState?.close()
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.resources.string) },
                icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                selected = false,
                onClick = {
                    scope.launch {
                        drawerState?.close()
                        rootNavController.navigate(RouteName.RESOURCE)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.archived.string) },
                icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                selected = isSelected(RouteName.ARCHIVED),
                onClick = {
                    scope.launch {
                        memosNavController.navigate(RouteName.ARCHIVED) {
                            launchSingleTop = true
                            restoreState = true
                        }
                        drawerState?.close()
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.trash.string) },
                icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                selected = isSelected(RouteName.TRASH),
                onClick = {
                    scope.launch {
                        drawerState?.close()
                        rootNavController.navigate(RouteName.TRASH) {
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.daily_review.string) },
                icon = { Icon(Icons.Outlined.HistoryEdu, contentDescription = null) },
                selected = isSelected(RouteName.DAILY_REVIEW),
                onClick = {
                    scope.launch {
                        memosNavController.navigate(RouteName.DAILY_REVIEW) {
                            launchSingleTop = true
                            restoreState = true
                        }
                        drawerState?.close()
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.stats.string) },
                icon = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                selected = isSelected(RouteName.STATS),
                onClick = {
                    scope.launch {
                        drawerState?.close()
                        rootNavController.navigate(RouteName.STATS) {
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.archive_browse.string) },
                icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                selected = isSelected(RouteName.ARCHIVE_BROWSE),
                onClick = {
                    scope.launch {
                        drawerState?.close()
                        rootNavController.navigate(RouteName.ARCHIVE_BROWSE) {
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.memo_graph.string) },
                icon = { Icon(Icons.Outlined.Hub, contentDescription = null) },
                selected = isSelected(RouteName.MEMO_GRAPH),
                onClick = {
                    scope.launch {
                        drawerState?.close()
                        rootNavController.navigate(RouteName.MEMO_GRAPH) {
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.settings.string) },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = {
                    scope.launch {
                        drawerState?.close()
                        rootNavController.navigate(RouteName.SETTINGS)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
        }

        item {
            Text(
                R.string.tags.string,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(20.dp)
            )
        }

        memosViewModel.tags.toList().forEach { tag ->
            item {
                TagDrawerItem(
                    tag = tag,
                    selected = isTagSelected(tag),
                    memosNavController = memosNavController,
                    drawerState = drawerState,
                    onLongClick = { tagToManage = tag }
                )
            }
        }
    }

    tagToManage?.let { tag ->
        TagManageDialog(
            tag = tag,
            onRename = { newName ->
                scope.launch {
                    memosViewModel.renameTag(tag, newName)
                    tagToManage = null
                }
            },
            onRemove = {
                scope.launch {
                    memosViewModel.removeTag(tag)
                    tagToManage = null
                }
            },
            onDismiss = { tagToManage = null }
        )
    }

    LaunchedEffect(Unit) {
        memosViewModel.loadTags()
        delay(0)
        showHeatMap = true
    }
}

@Composable
private fun TagManageDialog(
    tag: String,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember(tag) { mutableStateOf(tag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(R.string.rename_tag.string) },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.replace("#", "") },
                    singleLine = true,
                    label = { Text(R.string.tag_name.string) }
                )
                Text(
                    text = R.string.remove_tag_desc.string,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(newName.trim()) },
                enabled = newName.isNotBlank() && newName.trim() != tag
            ) { Text(R.string.confirm.string) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text(R.string.delete.string, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(R.string.cancel.string) }
            }
        }
    )
}
