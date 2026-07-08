package me.mudkip.moememos.ui.page.memos

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.MemosCard
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.LocalMemos

private const val REVIEW_DAYS_AGO: Long = 30
private const val REVIEW_COUNT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReviewPage(
    drawerState: DrawerState? = null,
    navController: NavHostController? = null,
) {
    val scope = rememberCoroutineScope()
    val memosViewModel = LocalMemos.current

    // Generate the initial batch and allow shuffling.
    var reviewMemos by remember {
        mutableStateOf(memosViewModel.getDailyReviewMemos(REVIEW_DAYS_AGO, REVIEW_COUNT))
    }
    var shuffleKey by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = R.string.daily_review.string) },
                navigationIcon = {
                    if (drawerState != null) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                        }
                    } else {
                        IconButton(onClick = { navController?.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = R.string.back.string)
                        }
                    }
                },
                actions = {
                    IconButton(
                        enabled = reviewMemos.isNotEmpty(),
                        onClick = {
                            shuffleKey++
                            reviewMemos = memosViewModel.getDailyReviewMemos(REVIEW_DAYS_AGO, REVIEW_COUNT)
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = R.string.daily_review_shuffle.string)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (reviewMemos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = R.string.daily_review_empty.string.format(REVIEW_DAYS_AGO),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        text = R.string.daily_review_subtitle.string.format(REVIEW_DAYS_AGO),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                items(reviewMemos, key = { it.identifier + shuffleKey }) { memo ->
                    MemosCard(
                        memo = memo,
                        onClick = { selectedMemo ->
                            navController?.navigate(
                                "${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(selectedMemo.identifier)}"
                            )
                        },
                        previewMode = true,
                        showSyncStatus = false,
                        onTagClick = { tag ->
                            navController?.navigate("${RouteName.TAG}/${Uri.encode(tag, "UTF-8")}") {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}
