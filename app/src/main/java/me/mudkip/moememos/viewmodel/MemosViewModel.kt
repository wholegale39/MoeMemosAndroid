package me.mudkip.moememos.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mudkip.moememos.data.constant.MemosVersionSupport
import me.mudkip.moememos.data.constant.MoeMemosException
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.local.entity.ResourceEntity
import me.mudkip.moememos.data.model.DailyUsageStat
import me.mudkip.moememos.data.model.MemoRelation
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.RelationType
import me.mudkip.moememos.data.model.SyncStatus
import me.mudkip.moememos.data.service.AccountService
import me.mudkip.moememos.data.service.MemoService
import me.mudkip.moememos.ext.getErrorMessage
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.util.extractMemoLinks
import me.mudkip.moememos.util.tagRewritePattern
import me.mudkip.moememos.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class MemosViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    var memos = mutableStateListOf<MemoEntity>()
        private set
    var trashedMemos = mutableStateListOf<MemoEntity>()
        private set
    var tags = mutableStateListOf<String>()
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var matrix by mutableStateOf(DailyUsageStat.initialMatrix)
        private set

    val host: StateFlow<String?> =
        accountService.currentAccount
            .map { it?.getAccountInfo()?.host }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val syncStatus: StateFlow<SyncStatus> =
        memoService.syncStatus.stateIn(viewModelScope, SharingStarted.Eagerly, SyncStatus())

    init {
        snapshotFlow { memos.toList() }
            .onEach { matrix = calculateMatrix() }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            loadMemosSnapshot()

            memoService.syncStatus
                .map { it.syncing }
                .distinctUntilChanged()
                .collectLatest { syncing ->
                    if (syncing) {
                        return@collectLatest
                    }
                    memoService.memos.collectLatest { latestMemos ->
                        applyMemos(latestMemos)
                    }
                }
        }
    }

    private suspend fun loadMemosSnapshot() {
        when (val response = memoService.getRepository().listMemos()) {
            is ApiResponse.Success -> {
                applyMemos(response.data)
            }
            else -> {
                errorMessage = response.getErrorMessage()
            }
        }
    }

    suspend fun refreshLocalSnapshot() = withContext(viewModelScope.coroutineContext) {
        loadMemosSnapshot()
    }

    private fun applyMemos(latestMemos: List<MemoEntity>) {
        memos.clear()
        memos.addAll(latestMemos)
        errorMessage = null
    }

    suspend fun loadMemos(syncAfterLoad: Boolean = true) = withContext(viewModelScope.coroutineContext) {
        if (syncAfterLoad) {
            val compatibility = accountService.checkCurrentAccountSyncCompatibility(isAutomatic = true)
            if (compatibility !is AccountService.SyncCompatibility.Allowed) {
                return@withContext
            }

            val syncResult = memoService.sync(false)
            if (syncResult is ApiResponse.Success) {
                WidgetUpdater.updateWidgets(appContext)
            } else {
                if (!syncResult.isAccessTokenInvalidFailure()) {
                    errorMessage = syncResult.getErrorMessage()
                }
            }
        }
    }

    suspend fun refreshMemos(): ManualSyncResult = withContext(viewModelScope.coroutineContext) {
        when (val compatibility = accountService.checkCurrentAccountSyncCompatibility(
            isAutomatic = false,
        )) {
            is AccountService.SyncCompatibility.Blocked -> {
                return@withContext ManualSyncResult.Blocked(
                    compatibility.message ?: MemosVersionSupport.supportedVersionsMessage(appContext)
                )
            }
            AccountService.SyncCompatibility.Allowed -> Unit
        }

        val syncResult = memoService.sync(true)
        if (syncResult is ApiResponse.Success) {
            WidgetUpdater.updateWidgets(appContext)
        } else {
            val message = syncResult.getErrorMessage()
            errorMessage = message
            return@withContext ManualSyncResult.Failed(message)
        }
        ManualSyncResult.Completed
    }

    private fun ApiResponse<Unit>.isAccessTokenInvalidFailure(): Boolean {
        return this is ApiResponse.Failure.Exception && this.throwable == MoeMemosException.accessTokenInvalid
    }

    fun loadTags() = viewModelScope.launch {
        memoService.getRepository().listTags().suspendOnSuccess {
            tags.clear()
            tags.addAll(data)
        }
    }

    fun loadTrashedMemos() = viewModelScope.launch {
        memoService.getRepository().purgeExpiredTrashedMemos()
        memoService.trashedMemos.collectLatest { trashed ->
            trashedMemos.clear()
            trashedMemos.addAll(trashed)
        }
    }

    suspend fun restoreTrashedMemo(identifier: String): Boolean = withContext(viewModelScope.coroutineContext) {
        val restored = memoService.getRepository().restoreTrashedMemo(identifier) is ApiResponse.Success
        if (restored) {
            loadMemos(syncAfterLoad = true)
        }
        restored
    }

    suspend fun deleteMemoPermanently(identifier: String): Boolean = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().deleteMemoPermanently(identifier) is ApiResponse.Success
    }

    /**
     * Renames [oldTag] to [newTag] across all memos by rewriting the embedded
     * `#tag` text (tags have no storage of their own). Nested tags under
     * [oldTag] (e.g. `#oldTag/sub`) move along with it, mirroring flomo.
     */
    suspend fun renameTag(oldTag: String, newTag: String): Boolean = withContext(viewModelScope.coroutineContext) {
        applyTagRewrite(tagRewritePattern(oldTag), "#$newTag")
    }

    /**
     * Removes [tag] from all memos (the memos themselves are kept).
     */
    suspend fun removeTag(tag: String): Boolean = withContext(viewModelScope.coroutineContext) {
        applyTagRewrite(tagRewritePattern(tag), "")
    }

    private suspend fun applyTagRewrite(pattern: Regex, replacement: String): Boolean = withContext(viewModelScope.coroutineContext) {
        var allSucceeded = true
        memos.toList().filter { pattern.containsMatchIn(it.content) }.forEach { memo ->
            val newContent = memo.content.replace(pattern, replacement)
            if (editMemo(memo.identifier, newContent, memo.resources, memo.visibility) !is ApiResponse.Success) {
                allSucceeded = false
            }
        }
        loadTags()
        allSucceeded
    }

    suspend fun updateMemoPinned(memoIdentifier: String, pinned: Boolean) = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().updateMemo(memoIdentifier, pinned = pinned).suspendOnSuccess {
            updateMemo(data)
            // Update widgets after pinning/unpinning a memo
            WidgetUpdater.updateWidgets(appContext)
        }
    }

    suspend fun editMemo(memoIdentifier: String, content: String, resourceList: List<ResourceEntity>?, visibility: MemoVisibility): ApiResponse<MemoEntity> = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().updateMemo(memoIdentifier, content, resourceList, visibility).suspendOnSuccess {
            updateMemo(data)
            // Update widgets after editing a memo
            WidgetUpdater.updateWidgets(appContext)
        }
    }

    suspend fun archiveMemo(memoIdentifier: String) = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().archiveMemo(memoIdentifier).suspendOnSuccess {
            memos.removeIf { it.identifier == memoIdentifier }
            // Update widgets after archiving a memo
            WidgetUpdater.updateWidgets(appContext)
        }
    }

    suspend fun deleteMemo(memoIdentifier: String) = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().deleteMemo(memoIdentifier).suspendOnSuccess {
            memos.removeIf { it.identifier == memoIdentifier }
            // Update widgets after deleting a memo
            WidgetUpdater.updateWidgets(appContext)
        }
    }

    suspend fun cacheResourceFile(resourceIdentifier: String, downloadedUri: Uri): ApiResponse<Unit> = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().cacheResourceFile(resourceIdentifier, downloadedUri)
    }

    suspend fun getResourceById(resourceIdentifier: String): ResourceEntity? = withContext(viewModelScope.coroutineContext) {
        when (val response = memoService.getRepository().listResources()) {
            is ApiResponse.Success -> response.data.firstOrNull { it.identifier == resourceIdentifier }
            else -> null
        }
    }

    private fun updateMemo(memo: MemoEntity) {
        val index = memos.indexOfFirst { it.identifier == memo.identifier }
        if (index != -1) {
            memos[index] = memo
        }
    }

    private fun calculateMatrix(): List<DailyUsageStat> {
        val countMap = HashMap<LocalDate, Int>()

        for (memo in memos) {
            val date = memo.date.atZone(OffsetDateTime.now().offset).toLocalDate()
            countMap[date] = (countMap[date] ?: 0) + 1
        }

        return DailyUsageStat.initialMatrix.map {
            it.copy(count = countMap[it.date] ?: 0)
        }
    }

    /**
     * Returns up to [count] random memos created at least [daysAgo] days before now.
     * Non-archived, non-deleted memos only. Results are shuffled on each call.
     */
    fun getDailyReviewMemos(daysAgo: Long = 30, count: Int = 5): List<MemoEntity> {
        val cutoff = Instant.now().minus(daysAgo, ChronoUnit.DAYS)
        return memos
            .filter { !it.archived && it.date.isBefore(cutoff) }
            .shuffled()
            .take(count)
    }

    /**
     * Returns a single random non-archived memo (any age) for the flomo-style
     * "random walk" feature, or null when there is nothing to wander through.
     */
    fun getRandomMemo(): MemoEntity? {
        return memos.filter { !it.archived }.shuffled().firstOrNull()
    }

    /**
     * Resolves the [[target]] wikilinks embedded in [memo]'s content to locally
     * known memos (outgoing references).
     */
    fun getOutgoingLinks(identifier: String): List<MemoEntity> {
        val memo = memos.firstOrNull { it.identifier == identifier } ?: return emptyList()
        return extractMemoLinks(memo.content)
            .mapNotNull { target -> resolveMemoByTarget(target) }
            .distinctBy { it.identifier }
    }

    /**
     * Finds memos whose content contains a [[thisMemo]] link — i.e. memos that
     * reference the given memo (incoming backlinks), computed client-side.
     */
    fun getBacklinks(identifier: String): List<MemoEntity> {
        val memo = memos.firstOrNull { it.identifier == identifier } ?: return emptyList()
        val selfId = memo.remoteId ?: memo.identifier
        val selfName = selfId.substringAfterLast('/')
        return memos.filter { other ->
            other.identifier != identifier &&
                extractMemoLinks(other.content).any { t -> targetMatches(t, selfId, selfName) }
        }
    }

    private fun resolveMemoByTarget(target: String): MemoEntity? {
        return memos.firstOrNull { m ->
            val id = m.remoteId ?: m.identifier
            targetMatches(target, id, id.substringAfterLast('/'))
        }
    }

    private fun targetMatches(target: String, remoteId: String, name: String): Boolean {
        return target == remoteId ||
            target == "memos/$remoteId" ||
            target.endsWith("/$remoteId") ||
            target.substringAfterLast('/') == name
    }

    suspend fun getRelations(identifier: String): ApiResponse<List<MemoRelation>> = withContext(viewModelScope.coroutineContext) {
        memoService.getRepository().getRelations(identifier)
    }

    /**
     * Adds a relation from [identifier] to [targetIdentifier] by appending a
     * [[targetRemoteId]] wikilink to the memo content (the portable, source-of-
     * truth representation) and, best-effort, persisting it server-side via
     * SetMemoRelations so it is reflected on other clients too.
     */
    suspend fun addRelation(identifier: String, targetIdentifier: String): Boolean = withContext(viewModelScope.coroutineContext) {
        val memo = memos.firstOrNull { it.identifier == identifier } ?: return@withContext false
        val target = memos.firstOrNull { it.identifier == targetIdentifier } ?: return@withContext false
        // Local-only memos have no remote id yet; the local identifier keeps the
        // [[link]] resolvable offline and is rewritten on sync once a remote id exists.
        val targetLink = target.remoteId ?: target.identifier

        val alreadyLinked = extractMemoLinks(memo.content).any { t -> targetMatches(t, targetLink, targetLink.substringAfterLast('/')) }
        if (!alreadyLinked) {
            val newContent = if (memo.content.isBlank()) "[[$targetLink]]" else "${memo.content}\n\n[[$targetLink]]"
            val editResp = editMemo(identifier, newContent, memo.resources, memo.visibility)
            if (editResp !is ApiResponse.Success) {
                return@withContext false
            }
        }

        // Best-effort: also persist server-side relations (no-op on local and legacy servers).
        val existing = when (val r = getRelations(identifier)) {
            is ApiResponse.Success -> r.data
            else -> emptyList()
        }
        val updated = (existing + MemoRelation(relatedMemoName = targetLink, type = RelationType.REFERENCE))
            .distinctBy { it.relatedMemoName }
        memoService.getRepository().setRelations(identifier, updated)
        true
    }
}

val LocalMemos =
    compositionLocalOf<MemosViewModel> { error(me.mudkip.moememos.R.string.memos_view_model_not_found.string) }

sealed class ManualSyncResult {
    object Completed : ManualSyncResult()
    data class Blocked(val message: String) : ManualSyncResult()
    data class Failed(val message: String) : ManualSyncResult()
}
