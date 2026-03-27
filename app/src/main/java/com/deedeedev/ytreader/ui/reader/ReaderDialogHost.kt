package com.deedeedev.ytreader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.SnackbarHostState
import com.deedeedev.ytreader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ReaderDialogHost(
    showUnsavedDialog: Boolean,
    onDismissUnsaved: () -> Unit,
    onDiscardUnsaved: () -> Unit,
    showFindDialog: Boolean,
    findQuery: String,
    onFindQueryChange: (String) -> Unit,
    findErrorMessage: String?,
    hasSearchedFind: Boolean,
    findResults: List<ReaderFindResult>,
    originalSegmentFindResults: List<OriginalSegmentFindResult>,
    findIsCaseSensitive: Boolean,
    onFindIsCaseSensitiveChange: (Boolean) -> Unit,
    onRunFindSearch: () -> Unit,
    onSelectStudyFindResult: (ReaderFindResult) -> Unit,
    onSelectOriginalFallbackFindResult: (ReaderFindResult) -> Unit,
    onSelectOriginalSegmentFindResult: (OriginalSegmentFindResult) -> Unit,
    isOriginalMode: Boolean,
    onCloseFindDialog: () -> Unit,
    showFindReplaceDialog: Boolean,
    findText: String,
    onFindTextChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    findReplaceErrorMessage: String?,
    isCaseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    onReplace: () -> Unit,
    showInteractiveReplace: Boolean,
    onInteractiveReplace: () -> Unit,
    onCancelFindReplace: () -> Unit,
    showAiPreviewDialog: Boolean,
    previewText: String?,
    onApplyAiPreview: (String) -> Unit,
    onCancelAiPreview: () -> Unit,
    showAiErrorDialog: Boolean,
    aiErrorSummary: String?,
    aiErrorLog: String?,
    clipboardSetText: (AnnotatedString) -> Unit,
    onDismissAiError: () -> Unit,
    showEmptyDialog: Boolean,
    onDismissEmptyDialog: () -> Unit,
    showHighlightNoteDialog: Boolean,
    highlightNoteText: String,
    hasExistingHighlightNote: Boolean,
    onHighlightNoteTextChange: (String) -> Unit,
    onSaveHighlightNote: () -> Unit,
    onDismissHighlightNote: () -> Unit,
    onDeleteHighlightNote: () -> Unit,
    showBookmarkDialog: Boolean,
    isEditingBookmark: Boolean,
    bookmarkTitleText: String,
    onBookmarkTitleTextChange: (String) -> Unit,
    onSaveBookmark: () -> Unit,
    onDismissBookmark: () -> Unit,
    onDeleteBookmark: () -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onDiscard = onDiscardUnsaved,
            onCancel = onDismissUnsaved
        )
    }

    if (showFindDialog) {
        FindDialog(
            modifier = Modifier.testTag(READER_FIND_DIALOG_TAG),
            inputTag = READER_FIND_INPUT_TAG,
            resultsTag = READER_FIND_RESULTS_TAG,
            findQuery = findQuery,
            onFindQueryChange = onFindQueryChange,
            findErrorMessage = findErrorMessage,
            hasSearchedFind = hasSearchedFind,
            findResults = findResults,
            originalSegmentFindResults = originalSegmentFindResults,
            isCaseSensitive = findIsCaseSensitive,
            onCaseSensitiveChange = onFindIsCaseSensitiveChange,
            onRunSearch = onRunFindSearch,
            onSelectStudyResult = onSelectStudyFindResult,
            onSelectOriginalFallbackResult = onSelectOriginalFallbackFindResult,
            onSelectOriginalSegmentResult = onSelectOriginalSegmentFindResult,
            isOriginalMode = isOriginalMode,
            onClose = onCloseFindDialog
        )
    }

    if (showFindReplaceDialog) {
        FindReplaceDialog(
            inputTag = READER_FIND_REPLACE_INPUT_TAG,
            replacementTag = READER_FIND_REPLACE_REPLACEMENT_TAG,
            findText = findText,
            onFindTextChange = onFindTextChange,
            replaceText = replaceText,
            onReplaceTextChange = onReplaceTextChange,
            findReplaceErrorMessage = findReplaceErrorMessage,
            isCaseSensitive = isCaseSensitive,
            onCaseSensitiveChange = onCaseSensitiveChange,
            showInteractiveReplace = showInteractiveReplace,
            onInteractiveReplace = onInteractiveReplace,
            onReplaceAll = onReplace,
            onCancel = onCancelFindReplace
        )
    }

    if (showAiPreviewDialog) {
        val text = previewText
        if (text != null) {
            AiPreviewDialog(
                previewText = text,
                onApply = { onApplyAiPreview(text) },
                onCancel = onCancelAiPreview
            )
        }
    }

    if (showAiErrorDialog) {
        val errorLog = aiErrorLog
        if (!errorLog.isNullOrBlank()) {
            AiErrorDialog(
                errorSummary = aiErrorSummary,
                errorLog = errorLog,
                onCopyError = {
                    clipboardSetText(AnnotatedString(errorLog))
                    onDismissAiError()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.error_copied_to_clipboard)
                        )
                    }
                },
                onDismiss = onDismissAiError
            )
        }
    }

    if (showEmptyDialog) {
        EmptyTextDialog(onOk = onDismissEmptyDialog)
    }

    if (showHighlightNoteDialog) {
        HighlightNoteDialog(
            noteText = highlightNoteText,
            hasExistingNote = hasExistingHighlightNote,
            onNoteTextChange = onHighlightNoteTextChange,
            onSave = onSaveHighlightNote,
            onDismiss = onDismissHighlightNote,
            onDelete = onDeleteHighlightNote
        )
    }

    if (showBookmarkDialog) {
        BookmarkTitleDialog(
            isEditing = isEditingBookmark,
            titleText = bookmarkTitleText,
            onTitleTextChange = onBookmarkTitleTextChange,
            onSave = onSaveBookmark,
            onDismiss = onDismissBookmark,
            onDelete = onDeleteBookmark
        )
    }
}
