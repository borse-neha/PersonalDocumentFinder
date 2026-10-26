package com.example.personaldocumentfinder;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.system.Os;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.personaldocumentfinder.data.DocumentEntity;
import com.example.personaldocumentfinder.domain.CandidateImportItem;
import com.example.personaldocumentfinder.domain.PermissionManager;
import com.example.personaldocumentfinder.domain.RankedSearchResults;
import com.example.personaldocumentfinder.domain.StorageManager;
import com.example.personaldocumentfinder.ui.CandidateAdapter;
import com.example.personaldocumentfinder.ui.CategoryAdapter;
import com.example.personaldocumentfinder.ui.DocumentAdapter;
import com.example.personaldocumentfinder.ui.DocumentViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public enum ScreenType {
        WELCOME,
        HOME,
        CATEGORY_DETAIL,
        MY_DOCUMENTS,
        REVIEW_IMPORT,
        SETTINGS
    }

    private DocumentViewModel viewModel;
    private CoordinatorLayout coordinatorLayout;
    private FrameLayout screenContainer;

    private ScreenType currentScreen = ScreenType.WELCOME;
    private String currentCategoryDetail = null;

    private ActivityResultLauncher<String[]> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Os.chmod(getApplicationInfo().dataDir, 448); // 0700
        } catch (Exception ignored) {}

        setContentView(R.layout.activity_main);

        coordinatorLayout = findViewById(R.id.main_coordinator);
        screenContainer = findViewById(R.id.screen_container);

        ViewCompat.setOnApplyWindowInsetsListener(coordinatorLayout, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(DocumentViewModel.class);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        viewModel.scanAndAnalyzeDocuments(uris);
                    }
                }
        );

        // Theme Observer
        viewModel.getThemeState().observe(this, theme -> {
            if ("Light".equalsIgnoreCase(theme)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if ("Dark".equalsIgnoreCase(theme)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
        });

        // Snackbar Status Message Observer
        viewModel.getStatusMessage().observe(this, msg -> {
            if (msg != null && !msg.trim().isEmpty()) {
                Snackbar.make(coordinatorLayout, msg, Snackbar.LENGTH_SHORT).show();
                viewModel.clearStatusMessage();
            }
        });

        // Import State Observer (for Auto navigation to ReviewImport and Permission Dialog)
        viewModel.getImportState().observe(this, state -> {
            if (state instanceof DocumentViewModel.ImportState.ReviewNeeded) {
                if (currentScreen != ScreenType.REVIEW_IMPORT) {
                    navigateTo(ScreenType.REVIEW_IMPORT, null);
                }
            } else if (state instanceof DocumentViewModel.ImportState.PermissionRequired) {
                showPermissionRationaleDialog();
            }
        });

        // Back gesture handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentScreen != ScreenType.HOME && currentScreen != ScreenType.WELCOME) {
                    if (currentScreen == ScreenType.REVIEW_IMPORT) {
                        viewModel.clearCandidateQueue();
                    }
                    navigateTo(ScreenType.HOME, null);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        navigateTo(ScreenType.WELCOME, null);
    }

    public void navigateTo(ScreenType screen, String extra) {
        this.currentScreen = screen;
        this.currentCategoryDetail = extra;
        screenContainer.removeAllViews();

        switch (screen) {
            case WELCOME:
                renderWelcomeScreen();
                break;
            case HOME:
                renderHomeScreen();
                break;
            case CATEGORY_DETAIL:
                renderCategoryDetailScreen(extra);
                break;
            case MY_DOCUMENTS:
                renderFilesScreen();
                break;
            case REVIEW_IMPORT:
                renderReviewImportScreen();
                break;
            case SETTINGS:
                renderSettingsScreen();
                break;
        }
    }

    private void renderWelcomeScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.view_welcome, screenContainer, false);
        Button btnGetStarted = view.findViewById(R.id.btn_get_started);
        btnGetStarted.setOnClickListener(v -> navigateTo(ScreenType.HOME, null));
        screenContainer.addView(view);
    }

    private void renderHomeScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.view_home, screenContainer, false);

        Button btnSettings = view.findViewById(R.id.btn_settings);
        EditText etSearch = view.findViewById(R.id.et_search);
        TextView btnClearSearch = view.findViewById(R.id.btn_clear_search);
        LinearLayout layoutNormalContent = view.findViewById(R.id.layout_normal_content);
        Button btnScanDevice = view.findViewById(R.id.btn_scan_device);
        Button btnAddFiles = view.findViewById(R.id.btn_add_files);
        Button btnMyDocuments = view.findViewById(R.id.btn_my_documents);

        MaterialCardView cardScanProgress = view.findViewById(R.id.card_scan_progress);
        TextView tvScanProgress = view.findViewById(R.id.tv_scan_progress);
        Button btnStopScan = view.findViewById(R.id.btn_stop_scan);

        RecyclerView rvCategories = view.findViewById(R.id.rv_categories);
        TextView tvDocsSectionTitle = view.findViewById(R.id.tv_documents_section_title);
        MaterialCardView cardLoadingDocs = view.findViewById(R.id.card_loading_documents);
        TextView tvLoadingText = view.findViewById(R.id.tv_loading_text);
        MaterialCardView cardEmptyDocs = view.findViewById(R.id.card_empty_documents);
        TextView tvEmptyTitle = view.findViewById(R.id.tv_empty_title);
        TextView tvEmptySubtitle = view.findViewById(R.id.tv_empty_subtitle);

        RecyclerView rvDocuments = view.findViewById(R.id.rv_documents);
        TextView tvMentionHeader = view.findViewById(R.id.tv_mention_header);
        RecyclerView rvMentionDocuments = view.findViewById(R.id.rv_mention_documents);

        btnSettings.setOnClickListener(v -> navigateTo(ScreenType.SETTINGS, null));

        btnScanDevice.setOnClickListener(v -> viewModel.startDeviceScan());
        btnStopScan.setOnClickListener(v -> viewModel.stopDeviceScan());

        btnAddFiles.setOnClickListener(v -> filePickerLauncher.launch(new String[]{
                "application/pdf",
                "image/*",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }));

        btnMyDocuments.setOnClickListener(v -> navigateTo(ScreenType.MY_DOCUMENTS, null));

        // Categories Grid Setup
        CategoryAdapter categoryAdapter = new CategoryAdapter(categoryName -> navigateTo(ScreenType.CATEGORY_DETAIL, categoryName));
        rvCategories.setLayoutManager(new GridLayoutManager(this, 2));
        rvCategories.setAdapter(categoryAdapter);

        // Document Adapters Setup
        DocumentAdapter.DocumentActionListener docListener = createDocumentActionListener();
        DocumentAdapter docAdapter = new DocumentAdapter(docListener);
        rvDocuments.setLayoutManager(new LinearLayoutManager(this));
        rvDocuments.setAdapter(docAdapter);

        DocumentAdapter mentionAdapter = new DocumentAdapter(docListener);
        rvMentionDocuments.setLayoutManager(new LinearLayoutManager(this));
        rvMentionDocuments.setAdapter(mentionAdapter);

        // Search text change listener
        etSearch.setText(viewModel.getSearchQuery().getValue());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                viewModel.setSearchQuery(q);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            viewModel.setSearchQuery("");
        });

        // Observers for Home state
        viewModel.getCategoryCounts().observe(this, counts -> {
            boolean loaded = Boolean.TRUE.equals(viewModel.getIsDatabaseLoaded().getValue());
            categoryAdapter.setCategoryCounts(counts, loaded);
        });

        viewModel.getIsDatabaseLoaded().observe(this, loaded -> {
            categoryAdapter.setCategoryCounts(viewModel.getCategoryCounts().getValue(), Boolean.TRUE.equals(loaded));
            List<DocumentEntity> allDocs = viewModel.getAllDocuments().getValue();
            int count = allDocs != null ? allDocs.size() : 0;
            btnMyDocuments.setText(Boolean.TRUE.equals(loaded) ? "📁 My Documents (" + count + ")" : "📁 My Documents (...)");
        });

        viewModel.getAllDocuments().observe(this, allDocs -> {
            boolean loaded = Boolean.TRUE.equals(viewModel.getIsDatabaseLoaded().getValue());
            int count = allDocs != null ? allDocs.size() : 0;
            btnMyDocuments.setText(loaded ? "📁 My Documents (" + count + ")" : "📁 My Documents (...)");
            categoryAdapter.setCategoryCounts(viewModel.getCategoryCounts().getValue(), loaded);
        });

        viewModel.getImportState().observe(this, state -> {
            if (state instanceof DocumentViewModel.ImportState.Processing) {
                cardScanProgress.setVisibility(View.VISIBLE);
                tvScanProgress.setText(((DocumentViewModel.ImportState.Processing) state).getProgress());
            } else {
                cardScanProgress.setVisibility(View.GONE);
            }
        });

        Observer<Object> contentObserver = o -> {
            String q = viewModel.getSearchQuery().getValue();
            boolean isSearching = q != null && !q.trim().isEmpty();
            boolean isLoaded = Boolean.TRUE.equals(viewModel.getIsDatabaseLoaded().getValue());
            boolean isSearchLoading = Boolean.TRUE.equals(viewModel.getIsSearchLoading().getValue());
            RankedSearchResults ranked = viewModel.getRankedSearchResults().getValue();
            List<DocumentEntity> allDocs = viewModel.getAllDocuments().getValue();

            if (isSearching) {
                layoutNormalContent.setVisibility(View.GONE);
                tvDocsSectionTitle.setText("Search Results");

                if (isSearchLoading) {
                    cardLoadingDocs.setVisibility(View.VISIBLE);
                    tvLoadingText.setText("Searching documents...");
                    cardEmptyDocs.setVisibility(View.GONE);
                    rvDocuments.setVisibility(View.GONE);
                    tvMentionHeader.setVisibility(View.GONE);
                    rvMentionDocuments.setVisibility(View.GONE);
                } else if (ranked == null || (ranked.getPrimaryMatches().isEmpty() && ranked.getMentionMatches().isEmpty())) {
                    cardLoadingDocs.setVisibility(View.GONE);
                    cardEmptyDocs.setVisibility(View.VISIBLE);
                    tvEmptyTitle.setText("No matching documents found");
                    tvEmptySubtitle.setText("Try adjusting search terms or scanning your device.");
                    rvDocuments.setVisibility(View.GONE);
                    tvMentionHeader.setVisibility(View.GONE);
                    rvMentionDocuments.setVisibility(View.GONE);
                } else {
                    cardLoadingDocs.setVisibility(View.GONE);
                    cardEmptyDocs.setVisibility(View.GONE);

                    rvDocuments.setVisibility(View.VISIBLE);
                    docAdapter.setDocuments(ranked.getPrimaryMatches());

                    if (!ranked.getMentionMatches().isEmpty()) {
                        tvMentionHeader.setVisibility(View.VISIBLE);
                        tvMentionHeader.setText(ranked.getPrimaryMatches().isEmpty()
                                ? "Documents mentioning \"" + q + "\""
                                : "Other documents mentioning \"" + q + "\"");
                        rvMentionDocuments.setVisibility(View.VISIBLE);
                        mentionAdapter.setDocuments(ranked.getMentionMatches());
                    } else {
                        tvMentionHeader.setVisibility(View.GONE);
                        rvMentionDocuments.setVisibility(View.GONE);
                    }
                }
            } else {
                layoutNormalContent.setVisibility(View.VISIBLE);
                tvDocsSectionTitle.setText("Recent Documents");
                tvMentionHeader.setVisibility(View.GONE);
                rvMentionDocuments.setVisibility(View.GONE);

                if (!isLoaded) {
                    cardLoadingDocs.setVisibility(View.VISIBLE);
                    tvLoadingText.setText("Loading documents...");
                    cardEmptyDocs.setVisibility(View.GONE);
                    rvDocuments.setVisibility(View.GONE);
                } else if (allDocs == null || allDocs.isEmpty()) {
                    cardLoadingDocs.setVisibility(View.GONE);
                    cardEmptyDocs.setVisibility(View.VISIBLE);
                    tvEmptyTitle.setText("No documents imported yet");
                    tvEmptySubtitle.setText("Tap 'Scan Device' above to search external folders, or 'Add Files' to select manually.");
                    rvDocuments.setVisibility(View.GONE);
                } else {
                    cardLoadingDocs.setVisibility(View.GONE);
                    cardEmptyDocs.setVisibility(View.GONE);
                    rvDocuments.setVisibility(View.VISIBLE);

                    List<DocumentEntity> recent = allDocs.size() > 10 ? allDocs.subList(0, 10) : allDocs;
                    docAdapter.setDocuments(recent);
                }
            }
        };

        viewModel.getSearchQuery().observe(this, contentObserver);
        viewModel.getIsSearchLoading().observe(this, contentObserver);
        viewModel.getRankedSearchResults().observe(this, contentObserver);
        viewModel.getAllDocuments().observe(this, contentObserver);
        viewModel.getIsDatabaseLoaded().observe(this, contentObserver);

        screenContainer.addView(view);
    }

    private void renderCategoryDetailScreen(final String category) {
        View view = LayoutInflater.from(this).inflate(R.layout.view_category_detail, screenContainer, false);

        Button btnBack = view.findViewById(R.id.btn_category_back);
        TextView tvTitle = view.findViewById(R.id.tv_category_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_category_count_subtitle);
        LinearLayout layoutLoading = view.findViewById(R.id.layout_category_loading);
        TextView tvLoadingText = view.findViewById(R.id.tv_category_loading_text);
        LinearLayout layoutEmpty = view.findViewById(R.id.layout_category_empty);
        TextView tvEmptyTitle = view.findViewById(R.id.tv_category_empty_title);
        RecyclerView rvDocs = view.findViewById(R.id.rv_category_documents);

        tvTitle.setText(category != null ? category : "");
        tvLoadingText.setText("Loading " + (category != null ? category : "") + " documents...");
        tvEmptyTitle.setText("No documents in " + (category != null ? category : ""));

        btnBack.setOnClickListener(v -> navigateTo(ScreenType.HOME, null));

        DocumentAdapter adapter = new DocumentAdapter(createDocumentActionListener());
        rvDocs.setLayoutManager(new LinearLayoutManager(this));
        rvDocs.setAdapter(adapter);

        viewModel.getCategoryDocuments(category).observe(this, docs -> {
            boolean isLoaded = Boolean.TRUE.equals(viewModel.getIsDatabaseLoaded().getValue());
            if (!isLoaded) {
                tvSubtitle.setText("— document(s)");
                layoutLoading.setVisibility(View.VISIBLE);
                layoutEmpty.setVisibility(View.GONE);
                rvDocs.setVisibility(View.GONE);
            } else if (docs == null || docs.isEmpty()) {
                tvSubtitle.setText("0 document(s)");
                layoutLoading.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
                rvDocs.setVisibility(View.GONE);
            } else {
                tvSubtitle.setText(docs.size() + " document(s)");
                layoutLoading.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.GONE);
                rvDocs.setVisibility(View.VISIBLE);
                adapter.setDocuments(docs);
            }
        });

        screenContainer.addView(view);
    }

    private void renderFilesScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.view_files, screenContainer, false);

        Button btnBack = view.findViewById(R.id.btn_files_back);
        EditText etSearch = view.findViewById(R.id.et_files_search);
        TextView btnClearSearch = view.findViewById(R.id.btn_clear_files_search);
        Button btnAddDocs = view.findViewById(R.id.btn_files_add_documents);
        LinearLayout layoutProgress = view.findViewById(R.id.layout_files_progress);
        TextView tvProgress = view.findViewById(R.id.tv_files_progress);
        TextView tvCountLabel = view.findViewById(R.id.tv_files_count_label);

        LinearLayout layoutLoading = view.findViewById(R.id.layout_files_loading);
        TextView tvLoadingText = view.findViewById(R.id.tv_files_loading_text);
        LinearLayout layoutEmpty = view.findViewById(R.id.layout_files_empty);
        TextView tvEmptyTitle = view.findViewById(R.id.tv_files_empty_title);

        RecyclerView rvDocs = view.findViewById(R.id.rv_files_documents);
        TextView tvMentionHeader = view.findViewById(R.id.tv_files_mention_header);
        RecyclerView rvMentionDocs = view.findViewById(R.id.rv_files_mention_documents);

        btnBack.setOnClickListener(v -> navigateTo(ScreenType.HOME, null));

        btnAddDocs.setOnClickListener(v -> filePickerLauncher.launch(new String[]{
                "application/pdf",
                "image/*",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }));

        DocumentAdapter.DocumentActionListener docListener = createDocumentActionListener();
        DocumentAdapter docAdapter = new DocumentAdapter(docListener);
        rvDocs.setLayoutManager(new LinearLayoutManager(this));
        rvDocs.setAdapter(docAdapter);

        DocumentAdapter mentionAdapter = new DocumentAdapter(docListener);
        rvMentionDocs.setLayoutManager(new LinearLayoutManager(this));
        rvMentionDocs.setAdapter(mentionAdapter);

        etSearch.setText(viewModel.getSearchQuery().getValue());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                viewModel.setSearchQuery(q);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            viewModel.setSearchQuery("");
        });

        viewModel.getImportState().observe(this, state -> {
            if (state instanceof DocumentViewModel.ImportState.Processing) {
                layoutProgress.setVisibility(View.VISIBLE);
                tvProgress.setText(((DocumentViewModel.ImportState.Processing) state).getProgress());
            } else {
                layoutProgress.setVisibility(View.GONE);
            }
        });

        Observer<Object> filesObserver = o -> {
            String q = viewModel.getSearchQuery().getValue();
            boolean isSearching = q != null && !q.trim().isEmpty();
            boolean isLoaded = Boolean.TRUE.equals(viewModel.getIsDatabaseLoaded().getValue());
            boolean isSearchLoading = Boolean.TRUE.equals(viewModel.getIsSearchLoading().getValue());
            RankedSearchResults ranked = viewModel.getRankedSearchResults().getValue();
            List<DocumentEntity> allDocs = viewModel.getAllDocuments().getValue();

            int totalMatches = (ranked != null) ? (ranked.getPrimaryMatches().size() + ranked.getMentionMatches().size()) : 0;
            if (isLoaded) {
                if (isSearching) {
                    tvCountLabel.setText(totalMatches + " result(s) found");
                } else {
                    int count = allDocs != null ? allDocs.size() : 0;
                    tvCountLabel.setText(count + " document(s) stored");
                }
            } else {
                tvCountLabel.setText("— document(s) stored");
            }

            if (isSearching) {
                if (isSearchLoading) {
                    layoutLoading.setVisibility(View.VISIBLE);
                    tvLoadingText.setText("Searching documents...");
                    layoutEmpty.setVisibility(View.GONE);
                    rvDocs.setVisibility(View.GONE);
                    tvMentionHeader.setVisibility(View.GONE);
                    rvMentionDocs.setVisibility(View.GONE);
                } else if (ranked == null || (ranked.getPrimaryMatches().isEmpty() && ranked.getMentionMatches().isEmpty())) {
                    layoutLoading.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    tvEmptyTitle.setText("No matching documents found");
                    rvDocs.setVisibility(View.GONE);
                    tvMentionHeader.setVisibility(View.GONE);
                    rvMentionDocs.setVisibility(View.GONE);
                } else {
                    layoutLoading.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.GONE);
                    rvDocs.setVisibility(View.VISIBLE);
                    docAdapter.setDocuments(ranked.getPrimaryMatches());

                    if (!ranked.getMentionMatches().isEmpty()) {
                        tvMentionHeader.setVisibility(View.VISIBLE);
                        tvMentionHeader.setText(ranked.getPrimaryMatches().isEmpty()
                                ? "Documents mentioning \"" + q + "\""
                                : "Other documents mentioning \"" + q + "\"");
                        rvMentionDocs.setVisibility(View.VISIBLE);
                        mentionAdapter.setDocuments(ranked.getMentionMatches());
                    } else {
                        tvMentionHeader.setVisibility(View.GONE);
                        rvMentionDocs.setVisibility(View.GONE);
                    }
                }
            } else {
                tvMentionHeader.setVisibility(View.GONE);
                rvMentionDocs.setVisibility(View.GONE);

                if (!isLoaded) {
                    layoutLoading.setVisibility(View.VISIBLE);
                    tvLoadingText.setText("Loading documents...");
                    layoutEmpty.setVisibility(View.GONE);
                    rvDocs.setVisibility(View.GONE);
                } else if (allDocs == null || allDocs.isEmpty()) {
                    layoutLoading.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    tvEmptyTitle.setText("No documents imported yet");
                    rvDocs.setVisibility(View.GONE);
                } else {
                    layoutLoading.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.GONE);
                    rvDocs.setVisibility(View.VISIBLE);
                    docAdapter.setDocuments(allDocs);
                }
            }
        };

        viewModel.getSearchQuery().observe(this, filesObserver);
        viewModel.getIsSearchLoading().observe(this, filesObserver);
        viewModel.getRankedSearchResults().observe(this, filesObserver);
        viewModel.getAllDocuments().observe(this, filesObserver);
        viewModel.getIsDatabaseLoaded().observe(this, filesObserver);

        screenContainer.addView(view);
    }

    private void renderReviewImportScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.view_review_import, screenContainer, false);

        Button btnCancel = view.findViewById(R.id.btn_review_cancel);
        TextView tvSubtitle = view.findViewById(R.id.tv_review_count_subtitle);
        RecyclerView rvCandidates = view.findViewById(R.id.rv_candidates);
        Button btnConfirmAll = view.findViewById(R.id.btn_review_confirm_all);

        btnCancel.setOnClickListener(v -> {
            viewModel.clearCandidateQueue();
            navigateTo(ScreenType.HOME, null);
        });

        CandidateAdapter adapter = new CandidateAdapter(new CandidateAdapter.CandidateActionListener() {
            @Override
            public void onCategoryChanged(int position, String newCategory) {
                viewModel.updateCandidateCategory(position, newCategory);
            }

            @Override
            public void onRemoveCandidate(int position) {
                viewModel.removeCandidateFromQueue(position);
            }
        });

        rvCandidates.setLayoutManager(new LinearLayoutManager(this));
        rvCandidates.setAdapter(adapter);

        viewModel.getCandidateQueue().observe(this, candidates -> {
            int count = candidates != null ? candidates.size() : 0;
            tvSubtitle.setText(count + " candidate(s) analyzed");
            btnConfirmAll.setText("Confirm & Import Selected Documents (" + count + ")");
            btnConfirmAll.setEnabled(count > 0);
            adapter.setCandidates(candidates);
        });

        btnConfirmAll.setOnClickListener(v -> {
            List<CandidateImportItem> queue = viewModel.getCandidateQueue().getValue();
            if (queue != null && !queue.isEmpty()) {
                viewModel.confirmCandidateImports(queue);
            }
            navigateTo(ScreenType.HOME, null);
        });

        screenContainer.addView(view);
    }

    private void renderSettingsScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.view_settings, screenContainer, false);

        Button btnBack = view.findViewById(R.id.btn_settings_back);
        ChipGroup chipGroupTheme = view.findViewById(R.id.chip_group_theme);
        Chip chipSystem = view.findViewById(R.id.chip_theme_system);
        Chip chipLight = view.findViewById(R.id.chip_theme_light);
        Chip chipDark = view.findViewById(R.id.chip_theme_dark);
        TextView tvStorageUsed = view.findViewById(R.id.tv_settings_storage_used);
        TextView tvTotalDocs = view.findViewById(R.id.tv_settings_total_docs);

        btnBack.setOnClickListener(v -> navigateTo(ScreenType.HOME, null));

        // Sync theme selection
        String currentTheme = viewModel.getThemeState().getValue();
        if ("Light".equalsIgnoreCase(currentTheme)) {
            chipLight.setChecked(true);
        } else if ("Dark".equalsIgnoreCase(currentTheme)) {
            chipDark.setChecked(true);
        } else {
            chipSystem.setChecked(true);
        }

        chipGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_theme_light) {
                viewModel.setTheme("Light");
            } else if (checkedId == R.id.chip_theme_dark) {
                viewModel.setTheme("Dark");
            } else {
                viewModel.setTheme("System");
            }
        });

        // Storage stats
        StorageManager.StorageStats stats = viewModel.getStorageManager().getStorageStats();
        tvStorageUsed.setText(Formatter.formatShortFileSize(this, stats.getTotalBytes()));

        viewModel.getTotalCount().observe(this, count -> {
            int c = count != null ? count : 0;
            tvTotalDocs.setText(c + " document(s)");
            StorageManager.StorageStats s = viewModel.getStorageManager().getStorageStats();
            tvStorageUsed.setText(Formatter.formatShortFileSize(this, s.getTotalBytes()));
        });

        screenContainer.addView(view);
    }

    private DocumentAdapter.DocumentActionListener createDocumentActionListener() {
        return new DocumentAdapter.DocumentActionListener() {
            @Override
            public void onOpen(DocumentEntity document) {
                viewModel.openDocument(MainActivity.this, document);
            }

            @Override
            public void onRename(DocumentEntity document) {
                showRenameDialog(document);
            }

            @Override
            public void onChangeCategory(DocumentEntity document) {
                showCategoryMoveDialog(document);
            }

            @Override
            public void onToggleFavorite(DocumentEntity document) {
                viewModel.toggleFavorite(document);
            }

            @Override
            public void onDelete(DocumentEntity document) {
                showDeleteConfirmDialog(document);
            }
        };
    }

    private void showRenameDialog(final DocumentEntity document) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rename_document, null);
        final TextInputLayout til = dialogView.findViewById(R.id.til_rename);
        final TextInputEditText et = dialogView.findViewById(R.id.et_rename);

        et.setText(document.getEffectiveDisplayName());
        if (et.getText() != null) {
            et.setSelection(et.getText().length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Rename Document")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveBtn.setOnClickListener(v -> {
                String newName = et.getText() != null ? et.getText().toString().trim() : "";
                if (newName.isEmpty()) {
                    til.setError("Name cannot be empty.");
                } else {
                    til.setError(null);
                    viewModel.renameDocument(document, newName);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }

    private void showCategoryMoveDialog(final DocumentEntity document) {
        final String[] categories = CandidateAdapter.ALL_CATEGORIES.toArray(new String[0]);
        int checkedItem = -1;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(document.getCategory())) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Move Category")
                .setSingleChoiceItems(categories, checkedItem, (dialog, which) -> {
                    if (which >= 0 && which < categories.length) {
                        viewModel.updateDocumentCategory(document, categories[which]);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmDialog(final DocumentEntity document) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove Document")
                .setMessage("Are you sure you want to remove '" + document.getEffectiveDisplayName() + "' from Personal Document Finder? Your original external file will remain safe and untouched.")
                .setPositiveButton("Remove", (dialog, which) -> viewModel.deleteDocument(document))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPermissionRationaleDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Storage Access Required")
                .setMessage("To automatically discover and index your personal documents across device folders (PDFs, receipts, identity cards, hall tickets), Personal Document Finder requires All Files Access permission.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    viewModel.clearCandidateQueue();
                    Intent intent = PermissionManager.getManageStorageIntent(MainActivity.this);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> viewModel.clearCandidateQueue())
                .show();
    }
}
