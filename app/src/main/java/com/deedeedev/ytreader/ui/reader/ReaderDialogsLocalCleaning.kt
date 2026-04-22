package com.deedeedev.ytreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R

@Composable
internal fun LocalCleaningDialog(
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var normalizeUnicodeWhitespace by remember { mutableStateOf(true) }
    var removeHtmlTags by remember { mutableStateOf(true) }
    var removeAsdCcArtifacts by remember { mutableStateOf(true) }
    var normalizeQuotationMarks by remember { mutableStateOf(true) }
    var normalizeEllipsis by remember { mutableStateOf(true) }
    var removeDuplicateSpaces by remember { mutableStateOf(true) }
    var removeSpacesBeforePunctuation by remember { mutableStateOf(true) }
    var trimLines by remember { mutableStateOf(true) }
    var removeBlankLines by remember { mutableStateOf(true) }
    var capitalizeFirstLetter by remember { mutableStateOf(true) }
    var addSpaceAfterPunctuation by remember { mutableStateOf(true) }
    var capitalizeAfterSentenceEnd by remember { mutableStateOf(true) }
    var mergeShortFragments by remember { mutableStateOf(true) }
    var removeMidSentenceLineBreaks by remember { mutableStateOf(true) }
    var replaceLineBreaksWithSpace by remember { mutableStateOf(false) }

    val title = stringResource(R.string.local_cleaning)
    val okLabel = stringResource(R.string.reader_apply)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                CleaningToggle(normalizeUnicodeWhitespace, { normalizeUnicodeWhitespace = it }, R.string.local_clean_normalize_unicode_whitespace)
                CleaningToggle(removeHtmlTags, { removeHtmlTags = it }, R.string.local_clean_remove_html_tags)
                CleaningToggle(removeAsdCcArtifacts, { removeAsdCcArtifacts = it }, R.string.local_clean_remove_asd_cc_artifacts)
                CleaningToggle(normalizeQuotationMarks, { normalizeQuotationMarks = it }, R.string.local_clean_normalize_quotation_marks)
                CleaningToggle(normalizeEllipsis, { normalizeEllipsis = it }, R.string.local_clean_normalize_ellipsis)
                CleaningToggle(removeDuplicateSpaces, { removeDuplicateSpaces = it }, R.string.local_clean_remove_duplicate_spaces)
                CleaningToggle(removeSpacesBeforePunctuation, { removeSpacesBeforePunctuation = it }, R.string.local_clean_remove_spaces_before_punctuation)
                CleaningToggle(trimLines, { trimLines = it }, R.string.local_clean_trim_lines)
                CleaningToggle(removeBlankLines, { removeBlankLines = it }, R.string.local_clean_remove_blank_lines)
                CleaningToggle(capitalizeFirstLetter, { capitalizeFirstLetter = it }, R.string.local_clean_capitalize_first_letter)
                CleaningToggle(addSpaceAfterPunctuation, { addSpaceAfterPunctuation = it }, R.string.local_clean_add_space_after_punctuation)
                CleaningToggle(capitalizeAfterSentenceEnd, { capitalizeAfterSentenceEnd = it }, R.string.local_clean_capitalize_after_sentence_end)
                CleaningToggle(mergeShortFragments, { mergeShortFragments = it }, R.string.local_clean_merge_short_fragments)
                CleaningToggle(removeMidSentenceLineBreaks, { removeMidSentenceLineBreaks = it }, R.string.local_clean_remove_mid_sentence_line_breaks)
                CleaningToggle(replaceLineBreaksWithSpace, { replaceLineBreaksWithSpace = it }, R.string.local_clean_replace_line_breaks_with_space)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply()
                    onDismiss()
                }
            ) {
                Text(okLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun CleaningToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labelRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}