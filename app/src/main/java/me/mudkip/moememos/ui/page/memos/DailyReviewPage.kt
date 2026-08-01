package me.mudkip.moememos.ui.page.memos

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.MemosCard
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.LocalMemos

private const val REVIEW_DAYS_AGO: Long = 30
private const val REVIEW_COUNT = 5

private const val TAB_DAILY_REVIEW = 0
private const val TAB_RANDOM_WALK = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReviewPage(
    drawerState: DrawerState? = null,
    navController: NavHostController? = null,
) {
    val scope = rememberCoroutineScope()
    val memosViewModel = LocalMemos.current
    var selectedTab by remember { mutableIntStateOf(TAB_DAILY_REVIEW) }

    // Daily review: a fixed batch of memos from N days ago, re-shuffleable.
    var reviewMemos by remember {
        mutableStateOf(memosViewModel.getDailyReviewMemos(REVIEW_DAYS_AGO, REVIEW_COUNT))
    }
    var shuffleKey by remember { mutableIntStateOf(0) }

    // Random walk: a single random memo that you can wander away from.
    var walkMemo by remember { mutableStateOf(memosViewModel.getRandomMemo()) }
    var walkKey by remember { mutableIntStateOf(0) }

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
                    if (selectedTab == TAB_DAILY_REVIEW) {
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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == TAB_DAILY_REVIEW,
                    onClick = { selectedTab = TAB_DAILY_REVIEW },
                    text = { Text(R.string.daily_review.string) }
                )
                Tab(
                    selected = selectedTab == TAB_RANDOM_WALK,
                    onClick = { selectedTab = TAB_RANDOM_WALK },
                    text = { Text(R.string.random_walk.string) }
                )
            }

            when (selectedTab) {
                TAB_DAILY_REVIEW -> DailyReviewContent(
                    reviewMemos = reviewMemos,
                    shuffleKey = shuffleKey,
                    navController = navController
                )
                TAB_RANDOM_WALK -> RandomWalkContent(
                    memo = walkMemo,
                    walkKey = walkKey,
                    onWander = {
                        walkKey++
                        walkMemo = memosViewModel.getRandomMemo()
                    },
                    navController = navController
                )
            }
        }
    }
}

@Composable
private fun DailyReviewContent(
    reviewMemos: List<MemoEntity>,
    shuffleKey: Int,
    navController: NavHostController?,
) {
    if (reviewMemos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
            modifier = Modifier.fillMaxSize(),
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

@Composable
private fun RandomWalkContent(
    memo: MemoEntity?,
    walkKey: Int,
    onWander: () -> Unit,
    navController: NavHostController?,
) {
    if (memo == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = R.string.random_walk_empty.string,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item(key = memo.identifier + walkKey) {
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onWander,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(R.string.random_walk_next.string)
            }
        }
    }
}
