package me.mudkip.moememos.ui.page.memos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.LocalMemos
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit

private enum class TimeRangeFilter(val labelRes: Int) {
    ALL(R.string.search_time_all),
    LAST_7_DAYS(R.string.search_time_7d),
    LAST_30_DAYS(R.string.search_time_30d),
    THIS_YEAR(R.string.search_time_year);

    fun startInstant(): Instant? {
        val now = Instant.now()
        return when (this) {
            ALL -> null
            LAST_7_DAYS -> now.minus(7, ChronoUnit.DAYS)
            LAST_30_DAYS -> now.minus(30, ChronoUnit.DAYS)
            THIS_YEAR -> now.minus(365, ChronoUnit.DAYS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(navController: NavHostController) {
    var searchText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var timeRange by rememberSaveable { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        modifier = Modifier
                            .padding(end = 15.dp)
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        placeholder = { Text(R.string.search.string) },
                        shape = ShapeDefaults.ExtraLarge,
                        leadingIcon = {
                            IconButton(onClick = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = R.string.back.string
                                )
                            }
                        }
                    )
                }
            )
        },

        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TimeRangeFilter.entries.size) { index ->
                        val filter = TimeRangeFilter.entries[index]
                        FilterChip(
                            selected = timeRange == index,
                            onClick = { timeRange = index },
                            label = { Text(filter.labelRes.string) }
                        )
                    }
                }

                MemosList(
                    contentPadding = PaddingValues(0.dp),
                    searchString = searchText.text,
                    timeRangeStart = TimeRangeFilter.entries[timeRange].startInstant(),
                    onTagClick = { tag ->
                        navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    )

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
}
