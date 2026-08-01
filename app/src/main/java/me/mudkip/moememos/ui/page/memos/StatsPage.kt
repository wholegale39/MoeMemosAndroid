package me.mudkip.moememos.ui.page.memos

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.Heatmap
import me.mudkip.moememos.ui.component.HeatmapStat
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.util.extractCustomTags
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPage(navController: NavHostController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val memos = memosViewModel.memos.toList()
    val user = userStateViewModel.currentUser

    val totalCount = memos.count()
    val now = LocalDate.now()
    val thisMonthCount = memos.count {
        YearMonth.from(it.date.atZone(OffsetDateTime.now().offset).toLocalDate()) == YearMonth.now()
    }
    val thisWeekCount = memos.count {
        val date = it.date.atZone(OffsetDateTime.now().offset).toLocalDate()
        ChronoUnit.DAYS.between(date, now) < 7
    }
    val daysUsed = user?.let {
        ChronoUnit.DAYS.between(
            it.startDate.atZone(OffsetDateTime.now().offset).toLocalDate(),
            now
        ) + 1
    } ?: 0

    // Last 12 months distribution
    val months = remember(memos) {
        (0L..11L).map { i ->
            val month = YearMonth.now().minusMonths(i)
            val count = memos.count {
                YearMonth.from(it.date.atZone(OffsetDateTime.now().offset).toLocalDate()) == month
            }
            month to count
        }.reversed()
    }
    val maxMonthCount = months.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    // Top tags
    val tagCounts = remember(memos) {
        memos.flatMap { extractCustomTags(it.content).toList() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = R.string.stats.string) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = R.string.back.string)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        value = totalCount.toString(),
                        label = R.string.stats_total.string,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = thisMonthCount.toString(),
                        label = R.string.stats_this_month.string,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = thisWeekCount.toString(),
                        label = R.string.stats_this_week.string,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = daysUsed.toString(),
                        label = R.string.stats_days_used.string,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Heatmap
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = R.string.stats.string,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            Heatmap()
                        }
                    }
                }
            }

            // Monthly distribution bar chart
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = R.string.stats_monthly_distribution.string,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        MonthlyBarChart(
                            months = months,
                            maxCount = maxMonthCount
                        )
                    }
                }
            }

            // Top tags
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = R.string.stats_tag_top.string,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (tagCounts.isEmpty()) {
                            Text(
                                text = R.string.no_memos.string,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            tagCounts.forEach { (tag, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthlyBarChart(
    months: List<Pair<YearMonth, Int>>,
    maxCount: Int
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("M月", Locale.getDefault())
    }
    val barColor = MaterialTheme.colorScheme.primary

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            months.forEach { (month, count) ->
                val fraction = count.toFloat() / maxCount
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (count > 0) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val barWidth = size.width * 0.6f
                            drawRoundRect(
                                color = if (count > 0) barColor else barColor.copy(alpha = 0.15f),
                                topLeft = Offset((size.width - barWidth) / 2, size.height * (1 - fraction)),
                                size = Size(barWidth, size.height * fraction),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                            )
                        }
                    }
                    Text(
                        text = month.format(formatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
