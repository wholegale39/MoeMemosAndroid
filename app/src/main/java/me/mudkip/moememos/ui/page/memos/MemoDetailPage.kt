package me.mudkip.moememos.ui.page.memos

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoRelation
import me.mudkip.moememos.ext.icon
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ext.titleResource
import me.mudkip.moememos.ui.component.MemoContent
import me.mudkip.moememos.ui.component.MemosCardActionButton
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailPage(
    navController: NavHostController,
    memoIdentifier: String
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val layoutDirection = LocalLayoutDirection.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val scope = rememberCoroutineScope()
    val memo = remember(memosViewModel.memos.toList(), memoIdentifier) {
        memosViewModel.memos.firstOrNull { it.identifier == memoIdentifier }
    }
    var hadMemo by rememberSaveable(memoIdentifier) { mutableStateOf(false) }

    var outgoing by remember { mutableStateOf<List<MemoEntity>>(emptyList()) }
    var backlinks by remember { mutableStateOf<List<MemoEntity>>(emptyList()) }
    var backendRelations by remember { mutableStateOf<List<MemoRelation>>(emptyList()) }
    var showAddRelation by remember { mutableStateOf(false) }

    LaunchedEffect(memo?.identifier) {
        when {
            memo != null -> hadMemo = true
            hadMemo -> navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
        }
    }

    LaunchedEffect(memo?.identifier) {
        val m = memo ?: return@LaunchedEffect
        outgoing = memosViewModel.getOutgoingLinks(m.identifier)
        backlinks = memosViewModel.getBacklinks(m.identifier)
        backendRelations = when (val resp = memosViewModel.getRelations(m.identifier)) {
            is ApiResponse.Success -> resp.data
            else -> emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = R.string.memo.string) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = R.string.back.string)
                    }
                },
                actions = {
                    memo?.let { MemosCardActionButton(it) }
                }
            )
        }
    ) { innerPadding ->
        if (memo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = R.string.memo_not_found.string)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                )
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 15.dp, top = 10.dp, end = 15.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        memo.date.toEpochMilli(),
                        System.currentTimeMillis(),
                        DateUtils.SECOND_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                if (currentAccount !is Account.Local && memo.needsSync) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = R.string.memo_sync_pending.string,
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(20.dp),
                    )
                }
                if (userStateViewModel.currentUser?.defaultVisibility != memo.visibility) {
                    Icon(
                        imageVector = memo.visibility.icon,
                        contentDescription = stringResource(memo.visibility.titleResource),
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(20.dp)
                    )
                }
            }

            MemoContent(
                memo = memo,
                selectable = true,
                checkboxChange = { checked, startOffset, endOffset ->
                    scope.launch {
                        var text = memo.content.substring(startOffset, endOffset)
                        text = if (checked) {
                            text.replace("[ ]", "[x]")
                        } else {
                            text.replace("[x]", "[ ]")
                        }
                        memosViewModel.editMemo(
                            memo.identifier,
                            memo.content.replaceRange(startOffset, endOffset, text),
                            memo.resources,
                            memo.visibility
                        )
                    }
                }
            )

            RelationSection(
                memosViewModel = memosViewModel,
                outgoing = outgoing,
                backlinks = backlinks,
                backendRelations = backendRelations,
                onNavigateToMemo = { targetIdentifier ->
                    navController.navigate("${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(targetIdentifier)}") {
                        launchSingleTop = true
                    }
                },
                onAddRelation = { showAddRelation = true }
            )

            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
        }

        if (showAddRelation && memo != null) {
            AddRelationDialog(
                memosViewModel = memosViewModel,
                currentMemoIdentifier = memo.identifier,
                onDismiss = { showAddRelation = false },
                onAdded = { targetIdentifier ->
                    showAddRelation = false
                    outgoing = memosViewModel.getOutgoingLinks(memo.identifier)
                    backendRelations = when (val resp = memosViewModel.getRelations(memo.identifier)) {
                        is ApiResponse.Success -> resp.data
                        else -> emptyList()
                    }
                }
            )
        }
    }
}

@Composable
private fun RelationSection(
    memosViewModel: MemosViewModel,
    outgoing: List<MemoEntity>,
    backlinks: List<MemoEntity>,
    backendRelations: List<MemoRelation>,
    onNavigateToMemo: (String) -> Unit,
    onAddRelation: () -> Unit
) {
    val relatedMemos = remember(outgoing, backendRelations, memosViewModel.memos) {
        val fromContent = outgoing
        val fromBackend = backendRelations.mapNotNull { rel ->
            memosViewModel.memos.firstOrNull { m ->
                !m.remoteId.isNullOrBlank() && (
                    m.remoteId == rel.relatedMemoName ||
                    m.remoteId == "memos/${rel.relatedMemoName}" ||
                    m.remoteId.endsWith("/${rel.relatedMemoName}") ||
                    m.remoteId.substringAfterLast('/') == rel.relatedMemoName.substringAfterLast('/')
                )
            }
        }
        (fromContent + fromBackend).distinctBy { it.identifier }
    }

    HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

    Text(
        text = R.string.related_memos.string,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 4.dp)
    )

    if (relatedMemos.isEmpty()) {
        Text(
            text = R.string.no_relations.string,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 8.dp)
        )
    } else {
        relatedMemos.forEach { related ->
            RelationRow(memo = related) { onNavigateToMemo(related.identifier) }
        }
    }

    if (backlinks.isNotEmpty()) {
        Text(
            text = R.string.backlinks.string,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 4.dp)
        )
        backlinks.forEach { back ->
            RelationRow(memo = back) { onNavigateToMemo(back.identifier) }
        }
    }

    TextButton(
        onClick = onAddRelation,
        modifier = Modifier.padding(start = 8.dp, end = 15.dp, bottom = 8.dp)
    ) {
        Text(R.string.add_relation.string)
    }
}

@Composable
private fun RelationRow(memo: MemoEntity, onClick: () -> Unit) {
    val preview = (memo.content.lineSequence().firstOrNull()?.trim()?.take(80)) ?: R.string.memo.string
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 15.dp, end = 15.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AddRelationDialog(
    memosViewModel: MemosViewModel,
    currentMemoIdentifier: String,
    onDismiss: () -> Unit,
    onAdded: suspend (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val candidates = remember(memosViewModel.memos) {
        memosViewModel.memos.filter { it.identifier != currentMemoIdentifier && !it.remoteId.isNullOrBlank() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(R.string.close.string) } },
        title = { Text(R.string.add_relation.string) },
        text = {
            if (candidates.isEmpty()) {
                Text(R.string.no_memos.string)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(candidates.size) { index ->
                        val candidate = candidates[index]
                        val preview = (candidate.content.lineSequence().firstOrNull()?.trim()?.take(60)) ?: R.string.memo.string
                        Text(
                            text = preview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        memosViewModel.addRelation(currentMemoIdentifier, candidate.identifier)
                                        onAdded(candidate.identifier)
                                    }
                                }
                                .padding(vertical = 10.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    )
}
