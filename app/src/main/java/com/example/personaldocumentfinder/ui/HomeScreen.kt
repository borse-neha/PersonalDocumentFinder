package com.example.personaldocumentfinder.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.personaldocumentfinder.domain.PermissionManager

data class CategoryItem(
    val icon: String,
    val name: String
)

val CATEGORY_ITEMS = listOf(
    CategoryItem("🎓", "College"),
    CategoryItem("🪪", "Identity"),
    CategoryItem("💰", "Finance"),
    CategoryItem("🏢", "Office"),
    CategoryItem("🚗", "Vehicle"),
    CategoryItem("👤", "Personal"),
    CategoryItem("📁", "Other Documents")
)

@Composable
fun HomeScreen(
    viewModel: DocumentViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onCategorySelected: (String) -> Unit,
    onFilesClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val allDocuments by viewModel.allDocuments.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var documentToMove by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.scanAndAnalyzeDocuments(uris)
        }
    }

    LaunchedEffect(importState) {
        if (importState is DocumentViewModel.ImportState.PermissionRequired) {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Personal Document Finder",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Find your documents quickly",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onSettingsClick) {
                    Text("⚙️ Settings")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search by name, OCR text, or category...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.startDeviceScan() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔍 Scan Device (Auto Find Documents)")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📄 Add Files")
                    }

                    OutlinedButton(
                        onClick = onFilesClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📂 My Documents (${allDocuments.size})")
                    }
                }
            }
        }

        if (importState is DocumentViewModel.ImportState.Processing) {
            item {
                val progress = (importState as DocumentViewModel.ImportState.Processing).progress
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = progress, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.stopDeviceScan() }) {
                            Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Categories",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Category Cards Grid with persistent categoryCounts Map
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in CATEGORY_ITEMS.indices step 2) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val item1 = CATEGORY_ITEMS[i]
                        val count1 = categoryCounts[item1.name] ?: 0

                        CategoryCardItem(
                            icon = item1.icon,
                            title = item1.name,
                            count = count1,
                            modifier = Modifier.weight(1f),
                            onClick = { onCategorySelected(item1.name) }
                        )

                        if (i + 1 < CATEGORY_ITEMS.size) {
                            val item2 = CATEGORY_ITEMS[i + 1]
                            val count2 = categoryCounts[item2.name] ?: 0

                            CategoryCardItem(
                                icon = item2.icon,
                                title = item2.name,
                                count = count2,
                                modifier = Modifier.weight(1f),
                                onClick = { onCategorySelected(item2.name) }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (searchQuery.isNotBlank()) "Search Results" else "Recent Documents",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val displayedDocs = if (searchQuery.isNotBlank()) searchResults else allDocuments.take(10)

        if (displayedDocs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📄", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching documents found" else "No documents imported yet",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Scan Device' above to search external folders, or 'Add Files' to select manually.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(displayedDocs, key = { it.id }) { doc ->
                DocumentCard(
                    document = doc,
                    onOpen = { viewModel.openDocument(context, doc) },
                    onChangeCategory = { documentToMove = doc },
                    onToggleFavorite = { viewModel.toggleFavorite(doc) },
                    onDelete = { documentToDelete = doc }
                )
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Storage Access Required") },
            text = {
                Text("To automatically discover and index your personal documents across device folders (PDFs, receipts, identity cards, hall tickets), Personal Document Finder requires All Files Access permission.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        viewModel.clearCandidateQueue()
                        val intent = PermissionManager.getManageStorageIntent(context)
                        context.startActivity(intent)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        viewModel.clearCandidateQueue()
                    }
                ) {
                    Text("Cancel")
                }
            }
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
            text = { Text("Are you sure you want to remove '${doc.originalName}' from Personal Document Finder?") },
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

@Composable
fun CategoryCardItem(
    icon: String,
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(88.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count document(s)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
