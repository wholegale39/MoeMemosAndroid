package me.mudkip.moememos.ui.page.memos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.viewmodel.LocalMemos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashPage(navController: NavHostController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val memosViewModel = LocalMemos.current
    val scope = rememberCoroutineScope()
    var memoToDelete by remember { mutableStateOf<MemoEntity?>(null) }

    LaunchedEffect(Unit) {
        memosViewModel.loadTrashedMemos()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(R.string.trash.string) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = R.string.back.string
                        )
                    }
                }
            )
        },
        content = { innerPadding ->
            if (memosViewModel.trashedMemos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.RestoreFromTrash,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = R.string.trash_empty.string,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = R.string.trash_retention_hint.string,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(memosViewModel.trashedMemos, key = { it.identifier }) { memo ->
                        TrashCard(
                            memo = memo,
                            onRestore = {
                                scope.launch { memosViewModel.restoreTrashedMemo(memo.identifier) }
                            },
                            onDeletePermanently = { memoToDelete = memo }
                        )
                    }
                }
            }
        }
    )

    memoToDelete?.let { memo ->
        AlertDialog(
            onDismissRequest = { memoToDelete = null },
            title = { Text(R.string.delete_this_memo.string) },
            text = { Text(R.string.delete_permanently_desc.string) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { memosViewModel.deleteMemoPermanently(memo.identifier) }
                    memoToDelete = null
                }) {
                    Text(R.string.delete.string, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoToDelete = null }) { Text(R.string.cancel.string) }
            }
        )
    }
}

@Composable
private fun TrashCard(
    memo: MemoEntity,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = memo.content.lineSequence().firstOrNull()?.trim()?.take(120)
                    ?: R.string.memo.string,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Outlined.RestoreFromTrash,
                        contentDescription = R.string.restore.string,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeletePermanently) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        contentDescription = R.string.delete_permanently.string,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
