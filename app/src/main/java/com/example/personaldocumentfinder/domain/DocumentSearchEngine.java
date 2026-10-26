package com.example.personaldocumentfinder.domain;

import com.example.personaldocumentfinder.data.DocumentEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DocumentSearchEngine {

    private DocumentSearchEngine() {}

    public static SearchIntent resolveIntent(String query) {
        String safeQuery = query != null ? query : "";
        String normalized = safeQuery.toLowerCase().trim().replaceAll("\\s+", " ");
        Set<String> docTypes = new LinkedHashSet<>();
        Set<String> categories = new LinkedHashSet<>();
        Set<String> synonyms = new LinkedHashSet<>();

        if (normalized.equals("aadhaar") || normalized.equals("aadhar") ||
                normalized.equals("aadhaar card") || normalized.equals("aadhar card") ||
                normalized.equals("uidai")) {
            docTypes.add("Aadhaar Card");
            categories.add("Identity");
            synonyms.addAll(Arrays.asList("aadhaar", "aadhar", "aadhaar card", "aadhar card", "uidai"));
        } else if (normalized.equals("pan") || normalized.equals("pan card")) {
            docTypes.add("PAN Card");
            categories.add("Identity");
            synonyms.addAll(Arrays.asList("pan", "pan card", "permanent account number"));
        } else if (normalized.equals("rc") || normalized.equals("vehicle rc") ||
                normalized.equals("registration certificate") || normalized.equals("vehicle registration") ||
                normalized.equals("rc book") || normalized.equals("registration")) {
            docTypes.addAll(Arrays.asList("Vehicle RC", "Vehicle Registration Certificate"));
            categories.add("Vehicle");
            synonyms.addAll(Arrays.asList("rc", "vehicle rc", "registration certificate", "vehicle registration", "rc book", "registration"));
        } else if (normalized.equals("puc") || normalized.equals("puc certificate") ||
                normalized.equals("pollution") || normalized.equals("pollution under control")) {
            docTypes.add("PUC Certificate");
            categories.add("Vehicle");
            synonyms.addAll(Arrays.asList("puc", "puc certificate", "pollution under control", "pollution"));
        } else if (normalized.equals("vehicle") || normalized.equals("automobile") ||
                normalized.equals("car") || normalized.equals("bike")) {
            categories.add("Vehicle");
            docTypes.addAll(Arrays.asList("Vehicle RC", "Vehicle Registration Certificate", "PUC Certificate", "Driving License"));
            synonyms.addAll(Arrays.asList("vehicle", "car", "bike"));
        } else if (normalized.equals("college") || normalized.equals("university") || normalized.equals("campus")) {
            categories.add("College");
            docTypes.addAll(Arrays.asList("Hall Ticket", "Marksheet", "College Document", "College Notice", "Certificate", "Scholarship Document"));
            synonyms.addAll(Arrays.asList("college", "university"));
        } else if (normalized.equals("marksheet") || normalized.equals("mark sheet") ||
                normalized.equals("grade card") || normalized.equals("results") ||
                normalized.equals("transcript")) {
            docTypes.add("Marksheet");
            categories.add("College");
            synonyms.addAll(Arrays.asList("marksheet", "mark sheet", "grade card", "result", "transcript"));
        } else if (normalized.equals("certificate") || normalized.equals("certification")) {
            docTypes.addAll(Arrays.asList("Certificate", "College Certificate"));
            categories.addAll(Arrays.asList("College", "Personal"));
            synonyms.addAll(Arrays.asList("certificate", "certification", "certifies", "awarded"));
        } else if (normalized.equals("scholarship") || normalized.equals("fellowship") ||
                normalized.equals("freeship") || normalized.equals("mahadbt")) {
            docTypes.add("Scholarship Document");
            categories.add("College");
            synonyms.addAll(Arrays.asList("scholarship", "mahadbt", "sanction"));
        } else if (normalized.equals("hall ticket") || normalized.equals("hallticket") ||
                normalized.equals("admit card")) {
            docTypes.add("Hall Ticket");
            categories.add("College");
            synonyms.addAll(Arrays.asList("hall ticket", "hallticket", "admit card"));
        } else if (normalized.equals("receipt") || normalized.equals("fee receipt") ||
                normalized.equals("fee") || normalized.equals("fees")) {
            docTypes.addAll(Arrays.asList("Fee Receipt", "Payment Receipt"));
            categories.add("Finance");
            synonyms.addAll(Arrays.asList("fee receipt", "receipt", "fees", "fee"));
        } else if (normalized.equals("invoice") || normalized.equals("tax invoice") || normalized.equals("bill")) {
            docTypes.add("Tax Invoice");
            categories.add("Finance");
            synonyms.addAll(Arrays.asList("tax invoice", "invoice", "bill"));
        } else if (normalized.equals("salary") || normalized.equals("salary slip") ||
                normalized.equals("payslip") || normalized.equals("pay slip")) {
            docTypes.add("Salary Slip");
            categories.add("Office");
            synonyms.addAll(Arrays.asList("salary slip", "payslip", "pay slip", "salary"));
        } else if (normalized.equals("id") || normalized.equals("identity") || normalized.equals("id card")) {
            categories.add("Identity");
            docTypes.addAll(Arrays.asList("Aadhaar Card", "PAN Card", "Driving License"));
            synonyms.addAll(Arrays.asList("identity", "id", "id card"));
        } else if (normalized.equals("license") || normalized.equals("licence") ||
                normalized.equals("driving license") || normalized.equals("driving licence") || normalized.equals("dl")) {
            docTypes.add("Driving License");
            categories.add("Identity");
            synonyms.addAll(Arrays.asList("driving license", "driving licence", "license", "licence", "dl"));
        } else if (normalized.equals("finance")) {
            categories.add("Finance");
            docTypes.addAll(Arrays.asList("Fee Receipt", "Tax Invoice", "Payment Receipt"));
            synonyms.add("finance");
        } else if (normalized.equals("office")) {
            categories.add("Office");
            docTypes.addAll(Arrays.asList("Salary Slip", "Offer Letter"));
            synonyms.add("office");
        } else {
            // Token-based fallback intent resolution for multi-word or compound queries
            if (normalized.contains("aadhaar") || normalized.contains("aadhar")) {
                docTypes.add("Aadhaar Card");
                categories.add("Identity");
                synonyms.addAll(Arrays.asList("aadhaar", "aadhar"));
            }
            if (normalized.contains("marksheet") || normalized.contains("mark sheet")) {
                docTypes.add("Marksheet");
                categories.add("College");
                synonyms.addAll(Arrays.asList("marksheet", "mark sheet"));
            }
            if (normalized.contains("certificate")) {
                docTypes.addAll(Arrays.asList("Certificate", "College Certificate"));
                categories.addAll(Arrays.asList("College", "Personal"));
            }
            if (normalized.contains("scholarship")) {
                docTypes.add("Scholarship Document");
                categories.add("College");
            }
            if (normalized.contains("hall") || normalized.contains("ticket") || normalized.contains("admit")) {
                docTypes.add("Hall Ticket");
                categories.add("College");
            }
            if (normalized.contains("receipt") || normalized.contains("fee")) {
                docTypes.addAll(Arrays.asList("Fee Receipt", "Payment Receipt"));
                categories.add("Finance");
            }
            if (normalized.contains("invoice")) {
                docTypes.add("Tax Invoice");
                categories.add("Finance");
            }
            if (normalized.contains("puc") || normalized.contains("pollution")) {
                docTypes.add("PUC Certificate");
                categories.add("Vehicle");
            }
            if (normalized.contains("rc") || normalized.contains("registration")) {
                docTypes.addAll(Arrays.asList("Vehicle RC", "Vehicle Registration Certificate"));
                categories.add("Vehicle");
            }
            if (normalized.contains("vehicle")) {
                categories.add("Vehicle");
            }
            if (normalized.contains("college") || normalized.contains("university")) {
                categories.add("College");
            }
        }

        return new SearchIntent(
                query,
                normalized,
                new ArrayList<>(docTypes),
                new ArrayList<>(categories),
                new ArrayList<>(synonyms)
        );
    }

    public static int calculateRelevanceScore(DocumentEntity doc, SearchIntent intent) {
        String query = intent.getNormalizedQuery();
        if (query.isEmpty()) return 100;

        int score = 0;
        String docName = (doc.getEffectiveDisplayName() != null ? doc.getEffectiveDisplayName() : "").toLowerCase();
        String docType = (doc.getDocumentType() != null ? doc.getDocumentType() : "").toLowerCase();
        String category = (doc.getCategory() != null ? doc.getCategory() : "").toLowerCase();
        String originalName = (doc.getOriginalName() != null ? doc.getOriginalName() : "").toLowerCase();
        String ocr = (doc.getOcrText() != null ? doc.getOcrText() : "").toLowerCase();

        // 1. Target Intent Match on Classified Metadata (Strongest Intent Boost)
        boolean matchesTargetDocType = false;
        for (String target : intent.getTargetDocumentTypes()) {
            if (target.equalsIgnoreCase(doc.getDocumentType())) {
                matchesTargetDocType = true;
                break;
            }
        }

        boolean matchesTargetCategory = false;
        for (String target : intent.getTargetCategories()) {
            if (target.equalsIgnoreCase(doc.getCategory())) {
                matchesTargetCategory = true;
                break;
            }
        }

        if (matchesTargetDocType) {
            score += 1000;
        }
        if (matchesTargetCategory) {
            score += 400;
        }

        // 2. Exact or substring match on Effective Display Name
        boolean matchesSynonymDocName = false;
        for (String syn : intent.getSynonyms()) {
            if (docName.equals(syn) || docName.contains(syn)) {
                matchesSynonymDocName = true;
                break;
            }
        }

        if (docName.equals(query) || (matchesSynonymDocName && docName.equals(query))) {
            score += 1000;
        } else if (docName.startsWith(query)) {
            score += 700;
        } else if (docName.contains(query) || matchesSynonymDocName) {
            score += 500;
        }

        // 3. DocumentType field match
        boolean matchesSynonymDocType = false;
        for (String syn : intent.getSynonyms()) {
            if (docType.equals(syn) || docType.contains(syn)) {
                matchesSynonymDocType = true;
                break;
            }
        }

        if (docType.equals(query)) {
            score += 800;
        } else if (docType.startsWith(query)) {
            score += 600;
        } else if (docType.contains(query) || matchesSynonymDocType) {
            score += 400;
        }

        // 4. Category field match
        if (category.equals(query)) {
            score += 500;
        } else if (category.contains(query)) {
            score += 300;
        }

        // 5. Multi-Word Query Token Handling
        String[] rawTokens = query.split(" ");
        List<String> tokens = new ArrayList<>();
        for (String t : rawTokens) {
            if (t.length() >= 2) {
                tokens.add(t);
            }
        }

        if (tokens.size() > 1) {
            boolean allInName = true;
            boolean allInType = true;
            boolean allInMeta = true;

            for (String token : tokens) {
                if (!docName.contains(token)) allInName = false;
                if (!docType.contains(token)) allInType = false;
                if (!docName.contains(token) && !docType.contains(token) && !category.contains(token)) {
                    allInMeta = false;
                }
            }

            if (allInName || allInType) score += 600;
            else if (allInMeta) score += 450;

            for (String token : tokens) {
                if (docName.contains(token)) score += 150;
                if (docType.contains(token)) score += 100;
                if (category.contains(token)) score += 80;
            }
        }

        // 6. Original Name match
        boolean matchesSynonymOriginal = false;
        for (String syn : intent.getSynonyms()) {
            if (originalName.contains(syn)) {
                matchesSynonymOriginal = true;
                break;
            }
        }

        if (originalName.equals(query)) {
            score += 300;
        } else if (originalName.contains(query) || matchesSynonymOriginal) {
            score += 150;
        }

        // 7. OCR Text Mention (Lowest Priority: 20 - 10)
        boolean matchesOcr = ocr.contains(query);
        if (!matchesOcr) {
            for (String syn : intent.getSynonyms()) {
                if (ocr.contains(syn)) {
                    matchesOcr = true;
                    break;
                }
            }
        }

        if (matchesOcr) {
            score += 20;
        } else if (!tokens.isEmpty()) {
            for (String token : tokens) {
                if (ocr.contains(token)) {
                    score += 10;
                    break;
                }
            }
        }

        return score;
    }

    public static RankedSearchResults rankAndGroupDocuments(List<DocumentEntity> documents, String query) {
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return new RankedSearchResults(documents, Collections.emptyList());
        }

        SearchIntent intent = resolveIntent(trimmed);

        class ScoredDoc {
            final DocumentEntity doc;
            final int score;

            ScoredDoc(DocumentEntity doc, int score) {
                this.doc = doc;
                this.score = score;
            }
        }

        List<ScoredDoc> scored = new ArrayList<>();
        for (DocumentEntity doc : documents) {
            int score = calculateRelevanceScore(doc, intent);
            if (score > 0) {
                scored.add(new ScoredDoc(doc, score));
            }
        }

        Collections.sort(scored, new Comparator<ScoredDoc>() {
            @Override
            public int compare(ScoredDoc o1, ScoredDoc o2) {
                int scoreCompare = Integer.compare(o2.score, o1.score);
                if (scoreCompare != 0) return scoreCompare;
                return Long.compare(o2.doc.getDateImported(), o1.doc.getDateImported());
            }
        });

        List<DocumentEntity> primary = new ArrayList<>();
        List<DocumentEntity> mentions = new ArrayList<>();

        for (ScoredDoc sd : scored) {
            if (sd.score >= 100) {
                primary.add(sd.doc);
            } else {
                mentions.add(sd.doc);
            }
        }

        return new RankedSearchResults(primary, mentions);
    }
}
