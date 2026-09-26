package com.example.personaldocumentfinder.ui

import android.text.format.Formatter
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ALL_CATEGORIES = listOf(
    "College",
    "Identity",
    "Finance",
    "Office",
    "Vehicle",
    "Personal",
    "Other Documents"
)

@Composable
fun CategoryDetailScreen(
    category: String,
    viewModel: DocumentViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.getCategoryDocuments(category).collectAsState()
    var documentToMove by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

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
            Column {
                Text(
                    text = category,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${documents.size} document(s)",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (documents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📁", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No documents in $category",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Imported documents matching this category will appear here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        onChangeCategory = { documentToMove = doc },
                        onToggleFavorite = { viewModel.toggleFavorite(doc) },
                        onDelete = { documentToDelete = doc }
                    )
                }
            }
        }
    }

    // Move category dialog
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

    // Delete confirmation dialog
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Remove Document") },
            text = { Text("Are you sure you want to remove '${doc.originalName}' from Personal Document Finder? The app-private copy will be deleted, but original external files are not modified.") },
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
fun DocumentCard(
    document: DocumentEntity,
    onOpen: () -> Unit,
    onChangeCategory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedSize = remember(document.fileSize) {
        Formatter.formatShortFileSize(context, document.fileSize)
    }
    val formattedDate = remember(document.dateImported) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(document.dateImported))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = getFileIcon(document.mimeType, document.originalName),
                    fontSize = 28.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.originalName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${document.documentType} • $formattedSize • $formattedDate",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onChangeCategory) {
                    Text("🏷️ ${document.category}", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onToggleFavorite) {
                    Text(if (document.isFavorite) "★ Favorited" else "☆ Favorite", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onOpen) {
                    Text("Open", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onDelete) {
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun CategoryMoveDialog(
    currentCategory: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Category") },
        text = {
            Column {
                Text("Select new category:", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                ALL_CATEGORIES.forEach { category ->
                    TextButton(
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (category == currentCategory) "✓ $category" else category,
                            fontWeight = if (category == currentCategory) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getFileIcon(mimeType: String, fileName: String): String {
    return when {
        mimeType.startsWith("image/") -> "🖼️"
        mimeType == "application/pdf" -> "📄"
        mimeType.startsWith("text/") -> "📝"
        fileName.endsWith(".doc", ignoreCase = true) || fileName.endsWith(".docx", ignoreCase = true) -> "📑"
        else -> "📂"
    }
}
