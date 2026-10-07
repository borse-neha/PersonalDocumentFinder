package com.example.personaldocumentfinder.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personaldocumentfinder.domain.CandidateImportItem

@Composable
fun ReviewImportScreen(
    candidates: List<CandidateImportItem>,
    modifier: Modifier = Modifier,
    onCategoryChanged: (index: Int, newCategory: String) -> Unit,
    onRemoveCandidate: (index: Int) -> Unit,
    onConfirmAll: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "🔍 Review Scanned Candidates",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${candidates.size} candidate(s) analyzed",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(candidates) { index, candidate ->
                CandidateCard(
                    candidate = candidate,
                    onCategorySelected = { newCat ->
                        onCategoryChanged(index, newCat)
                    },
                    onDismiss = {
                        onRemoveCandidate(index)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "🛡️ Original files on your device will remain untouched. Clean copies will be organized in Documents.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onConfirmAll,
            enabled = candidates.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm & Import Selected Documents (${candidates.size})")
        }
    }
}

@Composable
fun CandidateCard(
    candidate: CandidateImportItem,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (candidate.classification.isDocument) "📄" else "📷",
                    fontSize = 28.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "Type: ${candidate.classification.documentType}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ConfidenceBadge(
                    confidence = candidate.classification.categoryConfidence,
                    isDocument = candidate.classification.isDocument
                )
            }

            if (candidate.classification.detectionReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Analysis: ${candidate.classification.detectionReason}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (candidate.classification.ocrText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "OCR Snippet: \"${candidate.classification.ocrText.take(120)}...\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            if (candidate.isDuplicate) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ Duplicate content detected in database",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Category: ", fontSize = 13.sp)
                    OutlinedButton(onClick = { expandedMenu = true }) {
                        Text("${candidate.selectedCategory} ▾", fontSize = 13.sp)
                    }

                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        ALL_CATEGORIES.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    onCategorySelected(category)
                                    expandedMenu = false
                                }
                            )
                        }
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Exclude", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ConfidenceBadge(confidence: Float, isDocument: Boolean) {
    val label: String
    val color = when {
        !isDocument -> {
            label = "Rejected Photo"
            MaterialTheme.colorScheme.error
        }
        confidence >= 0.7f -> {
            label = "High Confidence"
            MaterialTheme.colorScheme.primary
        }
        confidence >= 0.4f -> {
            label = "Medium Confidence"
            MaterialTheme.colorScheme.secondary
        }
        else -> {
            label = "Low Confidence"
            MaterialTheme.colorScheme.tertiary
        }
    }

    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}
