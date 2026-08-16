package me.mudkip.moememos.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import me.mudkip.moememos.ui.page.common.RouteName
import java.net.URLEncoder

/**
 * Drawer item for a tag. Supports nested tags separated by "/" (e.g.
 * "读书/小说"): deeper tags are indented and shown with a sub-tag marker.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagDrawerItem(
    tag: String,
    selected: Boolean,
    memosNavController: NavHostController,
    drawerState: DrawerState? = null,
    onLongClick: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val segments = tag.split("/")
    val depth = (segments.size - 1).coerceAtLeast(0)
    val displayName = segments.last()

    val item: @Composable () -> Unit = {
        NavigationDrawerItem(
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(if (depth == 0) 18.dp else 14.dp),
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " $displayName",
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            },
            icon = {},
            selected = selected,
            onClick = {
                scope.launch {
                    memosNavController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                        launchSingleTop = true
                        restoreState = true
                    }
                    drawerState?.close()
                }
            },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .padding(start = (depth * 12).dp)
        )
    }

    if (onLongClick != null) {
        // Long-press falls through NavigationDrawerItem (it only handles taps)
        // to the parent combinedClickable, opening tag management.
        Box(
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
        ) {
            item()
        }
    } else {
        item()
    }
}
