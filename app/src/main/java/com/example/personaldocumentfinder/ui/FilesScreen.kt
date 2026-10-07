package com.example.personaldocumentfinder.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personaldocumentfinder.data.DocumentEntity

@Composable
fun FilesScreen(
    viewModel: DocumentViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDatabaseLoaded by viewModel.isDatabaseLoaded.collectAsState()
    val isSearchLoading by viewModel.isSearchLoading.collectAsState()
    val documents by viewModel.allDocuments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val rankedSearchResults by viewModel.rankedSearchResults.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var documentToMove by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToRename by remember { mutableStateOf<DocumentEntity?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.scanAndAnalyzeDocuments(uris)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = onBack) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "📁 My Documents",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search by name, OCR text, or category...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "✕",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.setSearchQuery("") }
                            .padding(12.dp)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                filePicker.launch(
                    arrayOf(
                        "application/pdf",
                        "image/*",
                        "text/plain",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📄 Add Documents")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (importState is DocumentViewModel.ImportState.Processing) {
            val progress = (importState as DocumentViewModel.ImportState.Processing).progress
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = progress, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val totalMatches = rankedSearchResults.primaryMatches.size + rankedSearchResults.mentionMatches.size
        val countLabel = if (isDatabaseLoaded) {
            if (searchQuery.isNotBlank()) "$totalMatches result(s) found" else "${documents.size} document(s) stored"
        } else {
            "— document(s) stored"
        }

        Text(
            text = countLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (searchQuery.isNotBlank()) {
            if (isSearchLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Searching documents...", fontSize = 14.sp)
                    }
                }
            } else if (rankedSearchResults.primaryMatches.isEmpty() && rankedSearchResults.mentionMatches.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📄", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No matching documents found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (rankedSearchResults.primaryMatches.isNotEmpty()) {
                        items(rankedSearchResults.primaryMatches, key = { it.id }) { doc ->
                            DocumentCard(
                                document = doc,
                                onOpen = { viewModel.openDocument(context, doc) },
                                onRename = { documentToRename = doc },
                                onChangeCategory = { documentToMove = doc },
                                onToggleFavorite = { viewModel.toggleFavorite(doc) },
                                onDelete = { documentToDelete = doc }
                            )
                        }
                    }

                    if (rankedSearchResults.mentionMatches.isNotEmpty()) {
                        item {
                            Text(
                                text = if (rankedSearchResults.primaryMatches.isNotEmpty()) {
                                    "Other documents mentioning \"$searchQuery\""
                                } else {
                                    "Documents mentioning \"$searchQuery\""
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(rankedSearchResults.mentionMatches, key = { it.id }) { doc ->
                            DocumentCard(
                                document = doc,
                                onOpen = { viewModel.openDocument(context, doc) },
                                onRename = { documentToRename = doc },
                                onChangeCategory = { documentToMove = doc },
                                onToggleFavorite = { viewModel.toggleFavorite(doc) },
                                onDelete = { documentToDelete = doc }
                            )
                        }
                    }
                }
            }
        } else {
            if (!isDatabaseLoaded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.width(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading documents...", fontSize = 14.sp)
                    }
                }
            } else if (documents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📄", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No documents imported yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(documents, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            onOpen = { viewModel.openDocument(context, doc) },
                            onRename = { documentToRename = doc },
                            onChangeCategory = { documentToMove = doc },
                            onToggleFavorite = { viewModel.toggleFavorite(doc) },
                            onDelete = { documentToDelete = doc }
                        )
                    }
                }
            }
        }
    }

    documentToRename?.let { doc ->
        DocumentRenameDialog(
            currentName = doc.effectiveDisplayName,
            onSave = { newName ->
                viewModel.renameDocument(doc, newName)
                documentToRename = null
            },
            onDismiss = { documentToRename = null }
        )
    }

    documentToMove?.let { doc ->
        CategoryMoveDialog(
            currentCategory = doc.category,
            onCategorySelected = { newCat ->
                viewModel.updateDocumentCategory(doc, newCat)
                documentToMove = null
            },
            onDismiss = { documentToMove = null }
        )
    }

    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Remove Document") },
            text = { Text("Are you sure you want to remove '${doc.effectiveDisplayName}' from Personal Document Finder?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(doc)
                        documentToDelete = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
