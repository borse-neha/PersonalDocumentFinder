package com.example.personaldocumentfinder.domain

import com.example.personaldocumentfinder.data.DocumentEntity

data class SearchIntent(
    val rawQuery: String,
    val normalizedQuery: String,
    val targetDocumentTypes: List<String> = emptyList(),
    val targetCategories: List<String> = emptyList(),
    val synonyms: List<String> = emptyList()
)

data class RankedSearchResults(
    val primaryMatches: List<DocumentEntity> = emptyList(),
    val mentionMatches: List<DocumentEntity> = emptyList()
) {
    val allRanked: List<DocumentEntity>
        get() = primaryMatches + mentionMatches

    val totalCount: Int
        get() = primaryMatches.size + mentionMatches.size

    val isEmpty: Boolean
        get() = primaryMatches.isEmpty() && mentionMatches.isEmpty()
}

object DocumentSearchEngine {

    fun resolveIntent(query: String): SearchIntent {
        val normalized = query.lowercase().trim().replace(Regex("""\s+"""), " ")
        val docTypes = mutableListOf<String>()
        val categories = mutableListOf<String>()
        val synonyms = mutableListOf<String>()

        when {
            normalized == "aadhaar" || normalized == "aadhar" ||
            normalized == "aadhaar card" || normalized == "aadhar card" ||
            normalized == "uidai" -> {
                docTypes.add("Aadhaar Card")
                categories.add("Identity")
                synonyms.addAll(listOf("aadhaar", "aadhar", "aadhaar card", "aadhar card", "uidai"))
            }

            normalized == "pan" || normalized == "pan card" -> {
                docTypes.add("PAN Card")
                categories.add("Identity")
                synonyms.addAll(listOf("pan", "pan card", "permanent account number"))
            }

            normalized == "rc" || normalized == "vehicle rc" ||
            normalized == "registration certificate" || normalized == "vehicle registration" ||
            normalized == "rc book" || normalized == "registration" -> {
                docTypes.addAll(listOf("Vehicle RC", "Vehicle Registration Certificate"))
                categories.add("Vehicle")
                synonyms.addAll(listOf("rc", "vehicle rc", "registration certificate", "vehicle registration", "rc book", "registration"))
            }

            normalized == "puc" || normalized == "puc certificate" ||
            normalized == "pollution" || normalized == "pollution under control" -> {
                docTypes.add("PUC Certificate")
                categories.add("Vehicle")
                synonyms.addAll(listOf("puc", "puc certificate", "pollution under control", "pollution"))
            }

            normalized == "vehicle" || normalized == "automobile" ||
            normalized == "car" || normalized == "bike" -> {
                categories.add("Vehicle")
                docTypes.addAll(listOf("Vehicle RC", "Vehicle Registration Certificate", "PUC Certificate", "Driving License"))
                synonyms.addAll(listOf("vehicle", "car", "bike"))
            }

            normalized == "college" || normalized == "university" || normalized == "campus" -> {
                categories.add("College")
                docTypes.addAll(listOf("Hall Ticket", "Marksheet", "College Document", "College Notice", "Certificate", "Scholarship Document"))
                synonyms.addAll(listOf("college", "university"))
            }

            normalized == "marksheet" || normalized == "mark sheet" ||
            normalized == "grade card" || normalized == "results" ||
            normalized == "transcript" -> {
                docTypes.add("Marksheet")
                categories.add("College")
                synonyms.addAll(listOf("marksheet", "mark sheet", "grade card", "result", "transcript"))
            }

            normalized == "certificate" || normalized == "certification" -> {
                docTypes.addAll(listOf("Certificate", "College Certificate"))
                categories.addAll(listOf("College", "Personal"))
                synonyms.addAll(listOf("certificate", "certification", "certifies", "awarded"))
            }

            normalized == "scholarship" || normalized == "fellowship" ||
            normalized == "freeship" || normalized == "mahadbt" -> {
                docTypes.add("Scholarship Document")
                categories.add("College")
                synonyms.addAll(listOf("scholarship", "mahadbt", "sanction"))
            }

            normalized == "hall ticket" || normalized == "hallticket" ||
            normalized == "admit card" -> {
                docTypes.add("Hall Ticket")
                categories.add("College")
                synonyms.addAll(listOf("hall ticket", "hallticket", "admit card"))
            }

            normalized == "receipt" || normalized == "fee receipt" ||
            normalized == "fee" || normalized == "fees" -> {
                docTypes.addAll(listOf("Fee Receipt", "Payment Receipt"))
                categories.add("Finance")
                synonyms.addAll(listOf("fee receipt", "receipt", "fees", "fee"))
            }

            normalized == "invoice" || normalized == "tax invoice" || normalized == "bill" -> {
                docTypes.add("Tax Invoice")
                categories.add("Finance")
                synonyms.addAll(listOf("tax invoice", "invoice", "bill"))
            }

            normalized == "salary" || normalized == "salary slip" ||
            normalized == "payslip" || normalized == "pay slip" -> {
                docTypes.add("Salary Slip")
                categories.add("Office")
                synonyms.addAll(listOf("salary slip", "payslip", "pay slip", "salary"))
            }

            normalized == "id" || normalized == "identity" || normalized == "id card" -> {
                categories.add("Identity")
                docTypes.addAll(listOf("Aadhaar Card", "PAN Card", "Driving License"))
                synonyms.addAll(listOf("identity", "id", "id card"))
            }

            normalized == "license" || normalized == "licence" ||
            normalized == "driving license" || normalized == "driving licence" || normalized == "dl" -> {
                docTypes.add("Driving License")
                categories.add("Identity")
                synonyms.addAll(listOf("driving license", "driving licence", "license", "licence", "dl"))
            }

            normalized == "finance" -> {
                categories.add("Finance")
                docTypes.addAll(listOf("Fee Receipt", "Tax Invoice", "Payment Receipt"))
                synonyms.addAll(listOf("finance"))
            }

            normalized == "office" -> {
                categories.add("Office")
                docTypes.addAll(listOf("Salary Slip", "Offer Letter"))
                synonyms.addAll(listOf("office"))
            }

            else -> {
                // Token-based fallback intent resolution for multi-word or compound queries
                if (normalized.contains("aadhaar") || normalized.contains("aadhar")) {
                    docTypes.add("Aadhaar Card")
                    categories.add("Identity")
                    synonyms.addAll(listOf("aadhaar", "aadhar"))
                }
                if (normalized.contains("marksheet") || normalized.contains("mark sheet")) {
                    docTypes.add("Marksheet")
                    categories.add("College")
                    synonyms.addAll(listOf("marksheet", "mark sheet"))
                }
                if (normalized.contains("certificate")) {
                    docTypes.addAll(listOf("Certificate", "College Certificate"))
                    categories.addAll(listOf("College", "Personal"))
                }
                if (normalized.contains("scholarship")) {
                    docTypes.add("Scholarship Document")
                    categories.add("College")
                }
                if (normalized.contains("hall") || normalized.contains("ticket") || normalized.contains("admit")) {
                    docTypes.add("Hall Ticket")
                    categories.add("College")
                }
                if (normalized.contains("receipt") || normalized.contains("fee")) {
                    docTypes.addAll(listOf("Fee Receipt", "Payment Receipt"))
                    categories.add("Finance")
                }
                if (normalized.contains("invoice")) {
                    docTypes.add("Tax Invoice")
                    categories.add("Finance")
                }
                if (normalized.contains("puc") || normalized.contains("pollution")) {
                    docTypes.add("PUC Certificate")
                    categories.add("Vehicle")
                }
                if (normalized.contains("rc") || normalized.contains("registration")) {
                    docTypes.addAll(listOf("Vehicle RC", "Vehicle Registration Certificate"))
                    categories.add("Vehicle")
                }
                if (normalized.contains("vehicle")) {
                    categories.add("Vehicle")
                }
                if (normalized.contains("college") || normalized.contains("university")) {
                    categories.add("College")
                }
            }
        }

        return SearchIntent(
            rawQuery = query,
            normalizedQuery = normalized,
            targetDocumentTypes = docTypes.distinct(),
            targetCategories = categories.distinct(),
            synonyms = synonyms.distinct()
        )
    }

    fun calculateRelevanceScore(doc: DocumentEntity, intent: SearchIntent): Int {
        val query = intent.normalizedQuery
        if (query.isBlank()) return 100

        var score = 0
        val docName = doc.effectiveDisplayName.lowercase()
        val docType = doc.documentType.lowercase()
        val category = doc.category.lowercase()
        val originalName = doc.originalName.lowercase()
        val ocr = doc.ocrText.lowercase()

        // 1. Target Intent Match on Classified Metadata (Strongest Intent Boost)
        val matchesTargetDocType = intent.targetDocumentTypes.any { it.equals(doc.documentType, ignoreCase = true) }
        val matchesTargetCategory = intent.targetCategories.any { it.equals(doc.category, ignoreCase = true) }

        if (matchesTargetDocType) {
            score += 1000
        }
        if (matchesTargetCategory) {
            score += 400
        }

        // 2. Exact or substring match on Effective Display Name
        when {
            docName == query || intent.synonyms.any { docName == it } -> score += 1000
            docName.startsWith(query) -> score += 700
            docName.contains(query) || intent.synonyms.any { docName.contains(it) } -> score += 500
        }

        // 3. DocumentType field match
        when {
            docType == query || intent.synonyms.any { docType == it } -> score += 800
            docType.startsWith(query) -> score += 600
            docType.contains(query) || intent.synonyms.any { docType.contains(it) } -> score += 400
        }

        // 4. Category field match
        when {
            category == query -> score += 500
            category.contains(query) -> score += 300
        }

        // 5. Multi-Word Query Token Handling (college marksheet, fee receipt, vehicle rc, scholarship certificate)
        val tokens = query.split(" ").filter { it.length >= 2 }
        if (tokens.size > 1) {
            val allInName = tokens.all { docName.contains(it) }
            val allInType = tokens.all { docType.contains(it) }
            val allInMeta = tokens.all { docName.contains(it) || docType.contains(it) || category.contains(it) }

            if (allInName || allInType) score += 600
            else if (allInMeta) score += 450

            tokens.forEach { token ->
                if (docName.contains(token)) score += 150
                if (docType.contains(token)) score += 100
                if (category.contains(token)) score += 80
            }
        }

        // 6. Original Name match
        when {
            originalName == query -> score += 300
            originalName.contains(query) || intent.synonyms.any { originalName.contains(it) } -> score += 150
        }

        // 7. OCR Text Mention (Lowest Priority: 20 - 10)
        val matchesOcr = ocr.contains(query) || intent.synonyms.any { ocr.contains(it) }
        if (matchesOcr) {
            score += 20
        } else if (tokens.isNotEmpty() && tokens.any { ocr.contains(it) }) {
            score += 10
        }

        return score
    }

    fun rankAndGroupDocuments(documents: List<DocumentEntity>, query: String): RankedSearchResults {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return RankedSearchResults(primaryMatches = documents, mentionMatches = emptyList())
        }

        val intent = resolveIntent(trimmed)

        val scored = documents.map { doc ->
            doc to calculateRelevanceScore(doc, intent)
        }.filter { it.second > 0 }
         .sortedWith(
             compareByDescending<Pair<DocumentEntity, Int>> { it.second }
                 .thenByDescending { it.first.dateImported }
         )

        // Partition: Primary matches (score >= 100) vs Mention-only matches (score < 100)
        val primary = scored.filter { it.second >= 100 }.map { it.first }
        val mentions = scored.filter { it.second < 100 }.map { it.first }

        return RankedSearchResults(primaryMatches = primary, mentionMatches = mentions)
    }
}
