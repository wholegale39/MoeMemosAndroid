package me.mudkip.moememos.ui.page.memoinput

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.mudkip.moememos.MoeMemosFileProvider
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.ShareContent
import me.mudkip.moememos.data.service.AiSettings
import me.mudkip.moememos.data.service.AiSettingsStorage
import me.mudkip.moememos.data.service.LlmService
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ext.suspendOnErrorMessage
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.ui.util.PickMultipleImagesContract
import me.mudkip.moememos.util.extractCustomTags
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.MemoInputViewModel

private const val MaxSelectableImages = 100

@Composable
fun MemoInputPage(
    viewModel: MemoInputViewModel = hiltViewModel(),
    memoIdentifier: String? = null,
    shareContent: ShareContent? = null
) {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val navController = LocalRootNavController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val context = LocalContext.current
    val memo = remember { memosViewModel.memos.toList().find { it.identifier == memoIdentifier } }
    var initialContent by remember { mutableStateOf(memo?.content ?: "") }
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(memo?.content ?: "", TextRange(memo?.content?.length ?: 0)))
    }
    var visibilityMenuExpanded by remember { mutableStateOf(false) }
    var tagMenuExpanded by remember { mutableStateOf(false) }
    var photoImageUri by remember { mutableStateOf<Uri?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    val defaultVisibility = userStateViewModel.currentUser?.defaultVisibility ?: MemoVisibility.PRIVATE
    var currentVisibility by remember { mutableStateOf(memo?.visibility ?: defaultVisibility) }

    val validMimeTypePrefixes = remember {
        setOf("text/")
    }

    // --- Voice input (system speech recognition intent; reliable on API 33+) ---
    var voiceInputActive by remember { mutableStateOf(false) }

    // --- AI assist ---
    val aiSettingsStorage = remember { AiSettingsStorage(context) }
    val aiSettings by aiSettingsStorage.settings.collectAsState(initial = AiSettings("", "", AiSettingsStorage.DEFAULT_MODEL))
    var aiLoading by remember { mutableStateOf(false) }
    val llmService = remember { LlmService() }

    // Voice input via the system speech recognition dialog. The bound
    // SpeechRecognizer API is deprecated/unreliable on API 33+ and frequently
    // fails to invoke any callback; the activity-based RecognizerIntent is the
    // stable path and works wherever a speech recognizer service exists.
    val speechResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        voiceInputActive = false
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spoken = matches?.firstOrNull().orEmpty()
                if (spoken.isNotEmpty()) {
                    val newText = if (text.text.isEmpty()) spoken else "${text.text} $spoken"
                    text = TextFieldValue(newText, TextRange(newText.length))
                }
            }
            Activity.RESULT_CANCELED -> Unit // user dismissed the dialog
            else -> coroutineScope.launch {
                snackbarState.showSnackbar(R.string.voice_input_no_speech.string)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchSpeech() else coroutineScope.launch {
            snackbarState.showSnackbar(R.string.voice_input_permission_required.string)
        }
    }

    fun launchSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            coroutineScope.launch {
                snackbarState.showSnackbar(R.string.voice_input_unavailable.string)
            }
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, context.resources.configuration.locales[0].toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_input_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceInputActive = true
            speechResultLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            voiceInputActive = false
            coroutineScope.launch {
                snackbarState.showSnackbar(R.string.voice_input_unavailable.string)
            }
        }
    }

    fun toggleVoiceInput() {
        if (voiceInputActive) {
            // The system speech dialog manages its own lifecycle; just reset the flag.
            voiceInputActive = false
            return
        }
        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    fun runAiAssist(action: AiAssistAction) {
        if (text.text.isBlank()) {
            coroutineScope.launch { snackbarState.showSnackbar(R.string.ai_assist_no_text.string) }
            return
        }
        coroutineScope.launch {
            aiLoading = true
            try {
                val prompt = when (action) {
                    AiAssistAction.POLISH ->
                        "You are a writing assistant. Refine the user's text for clarity, grammar, and flow. Keep the original meaning and language. Output only the polished text, no explanations."
                    AiAssistAction.SUMMARIZE ->
                        "You are a writing assistant. Summarize the user's text concisely in the same language. Output only the summary, no explanations."
                    AiAssistAction.EXPAND ->
                        "You are a writing assistant. Expand the user's text with relevant details and richer expression, keeping the same language and tone. Output only the expanded text, no explanations."
                    AiAssistAction.TRANSLATE ->
                        "You are a professional translator. Translate the user's text into English. Output only the translation, no explanations."
                }
                val result = llmService.transform(aiSettings, prompt, text.text)
                text = TextFieldValue(result, TextRange(result.length))
            } catch (e: Exception) {
                snackbarState.showSnackbar(R.string.ai_assist_error.string.format(e.message ?: ""))
            } finally {
                aiLoading = false
            }
        }
    }

    fun submit() = coroutineScope.launch {
        val tags = extractCustomTags(text.text)

        memo?.let {
            viewModel.editMemo(memo.identifier, text.text, currentVisibility, tags.toList()).suspendOnSuccess {
                memosViewModel.refreshLocalSnapshot()
                navController.popBackStack()
            }.suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
            return@launch
        }

        viewModel.createMemo(text.text, currentVisibility, tags.toList()).suspendOnSuccess {
            text = TextFieldValue("")
            viewModel.updateDraft("")
            memosViewModel.refreshLocalSnapshot()
            navController.popBackStack()
        }.suspendOnErrorMessage { message ->
            snackbarState.showSnackbar(message)
        }
    }

    fun handleExit() {
        if (text.text != initialContent || viewModel.uploadResources.size != (memo?.resources?.size ?: 0)) {
            showExitConfirmation = true
        } else {
            navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
        }
    }

    fun uploadImages(uris: List<Uri>) = coroutineScope.launch {
        uris.take(MaxSelectableImages).forEach { uri ->
            viewModel.upload(uri, memo?.identifier).suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
        }
        delay(300)
        focusRequester.requestFocus()
    }

    fun uploadImage(uri: Uri) {
        uploadImages(listOf(uri))
    }

    val pickImages = rememberLauncherForActivityResult(
        PickMultipleImagesContract(MaxSelectableImages)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uploadImages(uris)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            photoImageUri?.let { uploadImage(it) }
        }
    }

    val pickAttachment = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            coroutineScope.launch {
                viewModel.upload(it, memo?.identifier).suspendOnErrorMessage { message ->
                    snackbarState.showSnackbar(message)
                }
            }
        }
    }

    BackHandler {
        handleExit()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            MemoInputTopBar(
                isEditMode = memo != null,
                canSubmit = (text.text.isNotEmpty() || viewModel.uploadResources.isNotEmpty()) && !aiLoading,
                onClose = { handleExit() },
                onSubmit = { submit() }
            )
        },
        bottomBar = {
            MemoInputBottomBar(
                currentAccount = currentAccount,
                currentVisibility = currentVisibility,
                visibilityMenuExpanded = visibilityMenuExpanded,
                onVisibilityExpandedChange = { visibilityMenuExpanded = it },
                onVisibilitySelected = { currentVisibility = it },
                tags = memosViewModel.tags.toList(),
                tagMenuExpanded = tagMenuExpanded,
                onTagExpandedChange = { tagMenuExpanded = it },
                onHashTagClick = {
                    text = replaceSelection(text, "#")
                },
                onTagSelected = { tag ->
                    text = replaceSelection(text, "#$tag ")
                },
                onToggleTodoItem = {
                    text = toggleTodoItemInText(text)
                },
                onPickImage = {
                    pickImages.launch(Unit)
                },
                onPickAttachment = {
                    pickAttachment.launch(arrayOf("*/*"))
                },
                onTakePhoto = {
                    try {
                        val uri = MoeMemosFileProvider.getImageUri(navController.context)
                        photoImageUri = uri
                        takePhoto.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        coroutineScope.launch {
                            snackbarState.showSnackbar(e.localizedMessage ?: "Unable to take picture.")
                        }
                    }
                },
                onFormat = { format ->
                    text = applyMarkdownFormatToText(text, format)
                },
                isListening = voiceInputActive,
                onToggleVoiceInput = { toggleVoiceInput() },
                onAiAssist = { action -> runAiAssist(action) },
                aiAssistEnabled = aiSettings.isEnabled,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarState)
        }
    ) { innerPadding ->
        MemoInputEditor(
            modifier = Modifier.padding(innerPadding),
            text = text,
            onTextChange = { updated ->
                if (
                    text.text != updated.text &&
                    updated.selection.start == updated.selection.end &&
                    updated.text.length == text.text.length + 1 &&
                    updated.selection.start > 0 &&
                    updated.text[updated.selection.start - 1] == '\n'
                ) {
                    val handled = handleEnterInText(text)
                    if (handled != null) {
                        text = handled
                        return@MemoInputEditor
                    }
                }
                text = updated
            },
            focusRequester = focusRequester,
            validMimeTypePrefixes = validMimeTypePrefixes,
            onDroppedText = { droppedText ->
                text = text.copy(text = text.text + droppedText)
            },
            uploadResources = viewModel.uploadResources.toList(),
            inputViewModel = viewModel
        )
    }

    if (showExitConfirmation) {
        SaveChangesDialog(
            onSave = {
                showExitConfirmation = false
                submit()
            },
            onDiscard = {
                showExitConfirmation = false
                text = TextFieldValue("")
                navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
            },
            onDismiss = {
                showExitConfirmation = false
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.uploadResources.clear()
        when {
            memo != null -> {
                viewModel.uploadResources.addAll(memo.resources)
                initialContent = memo.content
            }

            shareContent != null -> {
                text = TextFieldValue(shareContent.text, TextRange(shareContent.text.length))
                for (item in shareContent.images) {
                    uploadImage(item)
                }
            }

            else -> {
                viewModel.draft.first()?.let {
                    text = TextFieldValue(it, TextRange(it.length))
                }
            }
        }
        delay(300)
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (memo == null && shareContent == null) {
                viewModel.updateDraft(text.text)
            }
        }
    }
}
