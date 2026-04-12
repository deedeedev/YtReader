package com.deedeedev.ytreader.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.data.VideoCollection

@Composable
fun AddToCollectionDialog(
    collections: List<VideoCollection>,
    onDismiss: () -> Unit,
    onCreateCollection: (String) -> Boolean,
    onAddToCollection: (String) -> Unit
) {
    var newCollectionName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collection_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.collection_new_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        val created = onCreateCollection(newCollectionName)
                        if (created) {
                            newCollectionName = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.collection_create))
                }

                if (collections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.collections_empty_dialog),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.collection_choose),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        collections.forEach { collection ->
                            TextButton(
                                onClick = { onAddToCollection(collection.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(collection.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_close))
            }
        }
    )
}
