# Personal Document Finder — Autonomous Android Build Specification

## Purpose

You are the lead Android engineer responsible for turning this project into a real, working, installable Android application.

Do not build a UI-only prototype. Implement the actual document discovery, content analysis, classification, private-copy storage, database, search, and persistence workflow described here.

Project:
- Name: Personal Document Finder
- Package: com.example.personaldocumentfinder
- Kotlin
- Jetpack Compose
- Material 3
- Android Studio
- Existing project already has a working welcome/home/category navigation prototype.
- Compile SDK is already configured around Android 37. Do not unnecessarily change AGP/Kotlin/Gradle versions.

---

# 1. PRODUCT VISION

Personal Document Finder is a privacy-first Android application that helps users find, organize, and protect personal documents scattered across accessible device storage and Gallery.

The application must analyze the actual contents of files/images, not only filenames.

Examples:

- IMG_8392.jpg may contain Aadhaar.
- Screenshot_2026-09-20.png may contain a payment receipt.
- WhatsApp Image....jpg may contain a hall ticket.
- A normal family photograph must not be imported merely because it is an image.
- An unknown but clearly recognizable document must go into "Other Documents".

The long-term user experience is:

DISCOVER
→ ANALYZE
→ DETECT DOCUMENT
→ OCR
→ CLASSIFY
→ CONFIDENCE CHECK
→ REVIEW IF UNCERTAIN
→ COPY INTO APP-PRIVATE STORAGE
→ INDEX IN ROOM
→ SEARCH / BROWSE / OPEN

---

# 2. CORE CATEGORIES

Required categories:

1. College
2. Identity
3. Finance
4. Office
5. Vehicle
6. Personal
7. Other Documents

"Other Documents" is mandatory.

If something is clearly a document but the category cannot be determined confidently, put it in Other Documents instead of forcing a wrong category.

---

# 3. CRITICAL ANDROID STORAGE RULE

Never bypass Android storage/privacy protections.

Never attempt to secretly access another application's private storage.

Use supported Android APIs, including where appropriate:

- Storage Access Framework
- ActivityResultContracts.OpenMultipleDocuments
- ActivityResultContracts.OpenDocument
- MediaStore
- Android Photo Picker
- persisted URI permissions
- app-private internal storage
- WorkManager

The app cannot assume unrestricted access to every directory on every Android version.

If a source requires user permission, provide an explicit permission/import flow.

Do not claim unrestricted scanning if Android does not allow it.

---

# 4. PERMANENT APP COPY

Imported documents must eventually be copied into app-private storage.

Do not depend exclusively on the original URI.

Example:

Original:
Downloads/HallTicket.pdf

App copy:
app-private/College/HallTicket.pdf

If the user later deletes the original from Downloads, the app's private copy must remain accessible.

The app must never automatically delete the original file during import.

Important limitation:
app-private files can be removed when the application is uninstalled or the device is factory-reset. Do not claim protection against those events unless a separate backup feature exists.

---

# 5. DISCOVERY SOURCES

Support these sources where Android permissions/API access allow:

## Documents/files

Use supported document APIs and user-selected imports.

Support common formats such as:

- PDF
- JPG/JPEG
- PNG
- TXT
- DOC
- DOCX
- other safe/appropriate document formats where practical

## Gallery/images

Use MediaStore and/or Android Photo Picker as appropriate.

The scanner must consider that ordinary-looking filenames can contain important documents.

## Screenshots

Screenshots must be considered candidates.

A screenshot containing:

- a receipt
- hall ticket
- government page
- certificate
- ticket
- application
- financial document

may be imported if content analysis identifies it as a document.

## Other apps

Do not attempt to access private storage belonging to WhatsApp, Telegram, Gmail, Google Drive, etc. directly.

Use supported sharing/import flows where needed.

---

# 6. DOCUMENT DETECTION

Do not classify based only on filename.

The application must determine whether a candidate is:

- DOCUMENT
- NON_DOCUMENT
- UNCERTAIN

Examples of documents:

- Aadhaar
- PAN
- passport
- hall ticket
- marksheet
- receipt
- invoice
- bank statement
- salary slip
- insurance
- vehicle RC
- certificate
- government form
- scanned paper
- office document

Examples of non-documents:

- selfie
- family photo
- landscape
- food photo
- meme
- ordinary unrelated screenshot

---

# 7. CONTENT ANALYSIS

For images:

Image
→ preprocessing if necessary
→ document detection
→ OCR
→ extracted text
→ visual/layout analysis
→ document type
→ category

For text PDFs:

PDF
→ extract text
→ analyze text
→ classify

For scanned/image PDFs:

PDF
→ render relevant pages
→ OCR
→ analyze
→ classify

Avoid processing thousands of pages unnecessarily. Use a safe sampling/batching strategy where appropriate.

---

# 8. OCR

Prefer reliable on-device OCR such as ML Kit where compatible and appropriate.

OCR should work with:

- camera photographs
- screenshots
- scanned documents
- image PDFs where practical

Store useful extracted OCR text locally in Room.

Do not upload private documents or OCR text to an external AI service by default.

Privacy is a core requirement.

---

# 9. CLASSIFICATION

Use multiple signals:

- OCR text
- filename
- MIME type
- document structure
- visible keywords
- layout
- metadata where useful

Filename is only one signal.

Examples:

"Examination Hall Ticket", "University", "Seat No", "Student"
→ College

"Aadhaar", "Government of India", "Date of Birth"
→ Identity

"Bank", "Account Number", "Transaction", "Amount"
→ Finance

"Employee", "Salary", "Company", "Payslip"
→ Office

"Registration Certificate", "Vehicle", "Chassis", "Engine"
→ Vehicle

If category confidence is low:
→ Other Documents

Do not force uncertain content into Personal simply because it is a default category.

---

# 10. CONFIDENCE

Store at least:

- documentConfidence
- categoryConfidence

Use meaningful thresholds.

High document confidence + high category confidence:
→ automatic classification

Document likely + category uncertain:
→ Other Documents

Document uncertain:
→ Review Queue or do not automatically import

Do not fabricate confidence values.

---

# 11. REVIEW QUEUE

Create a review workflow for uncertain candidates.

Example:

Review Documents

IMG_4827.jpg
Identity
High confidence

IMG_8392.jpg
Other Documents
Low confidence

User must be able to:

- Accept
- Change category
- Reject/ignore
- Retry analysis where practical
- Delete imported copy where appropriate

---

# 12. IMPORT WORKFLOW

Do not immediately copy every candidate.

Preferred workflow:

Scan
→ Analyze
→ Show results
→ User confirms
→ Copy selected documents into app-private storage
→ Create database records

For future automatic background scans:

High-confidence document → automatic import can be allowed if the user has enabled it.

Low-confidence document → Review Queue.

Non-document → ignore.

---

# 13. ROOM DATABASE

Use a real Room database.

Suggested entity:

DocumentEntity

Fields:

- id
- originalName
- storedFileName
- mimeType
- originalUri
- internalPath
- category
- documentType
- documentConfidence
- categoryConfidence
- ocrText
- fileSize
- dateImported
- lastModified
- isFavorite
- isReviewed

Architecture should separate:

- Entity
- DAO
- Database
- Repository
- ViewModel
- UI

Do not put database operations directly into Compose UI.

---

# 14. DUPLICATE DETECTION

Prevent repeated imports.

Use appropriate signals:

- URI
- metadata
- filename
- file size
- content hash

Content hash is preferred where practical.

If two files are byte-identical, do not create unnecessary duplicate copies.

---

# 15. SEARCH

Implement real search.

Search must work across:

- filename
- OCR text
- category
- document type
- tags if later implemented

Example:

File:
IMG_8392.jpg

OCR:
"Examination Hall Ticket"

Search:
hall ticket

Result:
IMG_8392.jpg

Search must remain responsive with a large database.

---

# 16. DOCUMENT OPENING

When a user opens an imported document, open the app's stored copy.

Do not depend on the original source file still existing.

Handle common MIME types.

If the device has no compatible external viewer, show a clear message instead of crashing.

---

# 17. HOME SCREEN

Create a production-quality Material 3 home screen.

Title:
Personal Document Finder

Subtitle:
Find your documents quickly

Categories:

College
Identity
Finance
Office
Vehicle
Personal
Other Documents

Also provide:

- Search
- Scan/Import
- Recent documents
- Favorites if implemented
- Document counts

---

# 18. CATEGORY SCREEN

Each category should show:

- count
- document list
- filename
- document type
- date imported
- thumbnail where appropriate
- favorite state
- open action

Users must be able to change category.

---

# 19. SCAN / IMPORT SCREEN

Provide clear actions such as:

- Scan Device
- Add Documents
- Add Photos

Show progress:

Scanning...
Analyzing...
Running OCR...
Classifying...
Checking duplicates...
Importing...

Do not freeze the UI.

Use coroutines/background processing appropriately.

---

# 20. LARGE DATASETS

Design for:

- 100 documents
- 1,000 documents
- 5,000 documents
- 10,000+ candidate images/files

Do not load every image/file into memory simultaneously.

Use:

- batching
- pagination
- streaming
- background workers
- limited concurrency

Avoid memory-heavy implementations.

---

# 21. BACKGROUND SCANNING

Use WorkManager where appropriate.

The app should eventually support:

Scan Now

and optionally periodic background checks for newly accessible candidates.

Respect Android battery, lifecycle, permission, and storage restrictions.

Do not claim unrestricted background scanning.

---

# 22. STORAGE STRUCTURE

Use logical categories:

College/
Identity/
Finance/
Office/
Vehicle/
Personal/
Other Documents/

Prefer app-private storage as the source of truth.

Do not rely on public folders as the permanent storage location.

---

# 23. USER CORRECTION

Every imported document must be re-categorizable.

Categories:

College
Identity
Finance
Office
Vehicle
Personal
Other Documents

Changing the category must update Room and the UI.

---

# 24. DELETE BEHAVIOR

Keep original and app copy separate.

"Remove from app":
→ remove the app's copy/database record.

Do not automatically delete the original.

The application is a protection/organization system, not a destructive file manager.

---

# 25. EXPORT

Eventually provide an Export/Share function using supported Android APIs.

This should allow users to save/share a copy outside the application.

Do not implement export before the core import pipeline is stable.

---

# 26. PRIVACY AND SECURITY

Documents can contain extremely sensitive information.

Requirements:

- app-private storage
- no unnecessary network upload
- no logging document contents
- no logging OCR text
- avoid unnecessary sensitive URI logging
- prefer on-device processing
- use Android-supported cryptography if a vault is later implemented
- never invent custom cryptography

---

# 27. SETTINGS

Create Settings with:

- Theme: System / Light / Dark
- Scan settings
- Category settings if needed
- Storage information
- Privacy information
- About

Persist theme using DataStore.

---

# 28. STORAGE INFORMATION

Show:

- number of imported documents
- approximate app storage used

Handle low-storage conditions gracefully.

If copying fails because storage is full, show a clear error and do not create an invalid database record.

---

# 29. ERROR HANDLING

Handle:

- permission denied
- file unavailable
- copy failure
- OCR failure
- unsupported format
- insufficient storage
- database errors
- corrupted file
- external viewer unavailable

Never silently fail.

Never register a document in Room if its private copy was not successfully completed.

Clean up partial files after failed copy operations.

---

# 30. UI QUALITY

Use Material 3 properly.

Include:

- consistent spacing
- typography hierarchy
- cards/lists
- loading states
- empty states
- error states
- confirmation dialogs
- thumbnails
- responsive layouts

Do not create a crude demo UI.

---

# 31. ACCESSIBILITY

Use:

- content descriptions
- readable text
- sufficient touch target sizes
- useful labels
- accessible navigation

Do not make emoji the only semantic representation of a category.

---

# 32. DEVELOPMENT LOOP

You must work incrementally.

For every feature:

1. Inspect current code.
2. Identify the smallest next change.
3. Implement it.
4. Build with Gradle/Android Studio.
5. Read the complete build output.
6. Fix errors.
7. Build again.
8. Run/test if an emulator/device is available.
9. Verify actual behavior.
10. Only then move to the next feature.

Do not implement many unrelated features before building.

---

# 33. NO FALSE COMPLETION

Never say a feature is complete unless it has actually been implemented and verified.

If only compilation has been verified, state:

"Build verified; runtime behavior still requires device/emulator testing."

Do not fabricate test results.

---

# 34. ERROR-DRIVEN DEVELOPMENT

When a build fails:

1. Read the exact error.
2. Identify the root cause.
3. Make the smallest reliable fix.
4. Build again.
5. Continue only after build success.

Do not randomly upgrade Gradle, Kotlin, AGP, or dependencies.

Do not replace working architecture because of one compile error.

---

# 35. FILE MODIFICATION RULE

Before changing a file:

- inspect it
- preserve working behavior
- avoid duplicate functions/classes
- avoid conflicting imports
- avoid multiple implementations of the same screen

If a file has become corrupted or contains duplicates, replace the entire file cleanly.

---

# 36. DEPENDENCY RULE

Before adding a dependency, determine whether it is necessary.

Prefer stable Android/Jetpack libraries.

Do not add unknown or unnecessary libraries.

Keep versions compatible with the existing project.

Do not upgrade AGP/Kotlin/Gradle without a technical reason.

---

# 37. CURRENT PROJECT STATE

Already completed:

- fresh Android Studio project
- Kotlin + Compose
- Material 3
- compile SDK 37
- successful Gradle build
- Welcome screen
- Get Started
- Home screen
- six basic categories
- My Documents screen
- category navigation
- basic file picker experimentation

Previous approach:

OpenDocumentTree() was used for folder scanning, but Android restricted access to Downloads on the test device.

Do not waste time repeatedly modifying the old recursive scanFolder() approach.

Current direction:

OpenMultipleDocuments()
+
supported MediaStore/Photo Picker/document APIs
+
private storage
+
Room

---

# 38. FIRST IMPLEMENTATION MILESTONE

Do not begin with AI classification.

First make this work completely:

OpenMultipleDocuments()
→ select multiple files
→ copy selected files into app-private storage
→ save metadata in Room
→ display imported documents
→ open the private copy
→ restart app
→ verify documents remain

Build and test this milestone.

Only after it works move to:

automatic image/document discovery
→ OCR
→ document detection
→ classification
→ Other Documents
→ background scanning

---

# 39. SECOND IMPLEMENTATION MILESTONE

Implement image/document analysis.

Pipeline:

Candidate image/file
→ document detection
→ OCR
→ content analysis
→ category classification
→ confidence
→ review if uncertain

Test with:

1. Aadhaar image
2. Hall ticket image
3. Payment receipt screenshot
4. Normal family photo
5. Vehicle RC
6. Unknown government document

Expected:

Aadhaar → Identity
Hall ticket → College
Receipt → Finance
Family photo → not imported
RC → Vehicle
Unknown government document → Other Documents

---

# 40. ACCEPTANCE TEST

The final app must pass:

1. Import multiple documents.
2. Copy them into app-private storage.
3. Create Room records.
4. Analyze image/document content.
5. Detect document vs non-document.
6. Classify known documents.
7. Put uncertain documents into Other Documents.
8. Allow user correction.
9. Search OCR text.
10. Open private copies.
11. Delete originals.
12. Verify app copies still work.
13. Restart application.
14. Verify database persistence.
15. Handle duplicate imports.
16. Handle unsupported/corrupt files without crashing.
17. Build a working APK.

---

# 41. FINAL APK

Before declaring the project complete:

Clean build
→ Debug APK
→ Install
→ Run on device/emulator
→ Test import
→ Test OCR
→ Test classification
→ Test private-copy persistence
→ Delete original
→ Verify app copy
→ Test search
→ Test category change
→ Restart app
→ Verify persistence

If release signing is not configured, do not call the APK production-signed.

At minimum, produce a working debug APK.

---

# 42. IMPORTANT PRODUCT PRINCIPLE

The goal is:

"Google search, but for my personal documents."

The user should not need to remember:

- which folder
- which filename
- whether it was a screenshot
- whether it was a Gallery image
- whether it was downloaded

They should be able to search:

"hall ticket"

and find the correct document because the app understands the document content.

---

# 43. EXECUTION INSTRUCTION

Read this entire specification before changing code.

Then inspect the current project.

Do not restart the project.

Do not rewrite completed functionality unnecessarily.

Do not create mock implementations for core features.

Start from the current codebase and proceed milestone-by-milestone.

After every meaningful milestone:

- build
- test
- fix
- verify
- continue

Continue until the application meets the acceptance test and a working APK can be built.
