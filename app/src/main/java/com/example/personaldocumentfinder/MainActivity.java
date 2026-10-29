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

    // Adapters
    private CategoryAdapter homeCategoryAdapter;
    private DocumentAdapter homeDocAdapter;
    private DocumentAdapter homeMentionAdapter;
    private DocumentAdapter categoryDetailAdapter;
    private DocumentAdapter filesDocAdapter;
    private DocumentAdapter filesMentionAdapter;
    private CandidateAdapter reviewAdapter;

    // Home views
    private LinearLayout homeLayoutNormalContent;
    private MaterialCardView homeCardScanProgress;
    private TextView homeTvScanProgress;
    private TextView homeTvDocsSectionTitle;
    private MaterialCardView homeCardLoadingDocs;
    private TextView homeTvLoadingText;
    private MaterialCardView homeCardEmptyDocs;
    private TextView homeTvEmptyTitle;
    private TextView homeTvEmptySubtitle;
    private RecyclerView homeRvDocuments;
    private TextView homeTvMentionHeader;
    private RecyclerView homeRvMentionDocuments;
    private Button homeBtnMyDocuments;

    // Category detail views
    private TextView categoryDetailTvSubtitle;
    private LinearLayout categoryDetailLayoutLoading;
    private TextView categoryDetailTvLoadingText;
    private LinearLayout categoryDetailLayoutEmpty;
    private TextView categoryDetailTvEmptyTitle;
    private RecyclerView categoryDetailRvDocs;

    // Files views
    private LinearLayout filesLayoutProgress;
    private TextView filesTvProgress;
    private TextView filesTvCountLabel;
    private LinearLayout filesLayoutLoading;
    private TextView filesTvLoadingText;
    private LinearLayout filesLayoutEmpty;
    private TextView filesTvEmptyTitle;
    private RecyclerView filesRvDocs;
    private TextView filesTvMentionHeader;
    private RecyclerView filesRvMentionDocs;

    // Review views
    private TextView reviewTvSubtitle;
    private Button reviewBtnConfirmAll;

    // Settings views
    private TextView settingsTvStorageUsed;
    private TextView settingsTvTotalDocs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Os.chmod(getApplicationInfo().dataDir, 448); // 0700
        } catch (Exception ignored) {
        }

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
                        viewModel.importDocumentsFromUris(uris);
                    }
                });

        // Initialize adapters once
        homeCategoryAdapter = new CategoryAdapter(categoryName -> navigateTo(ScreenType.CATEGORY_DETAIL, categoryName));
        DocumentAdapter.DocumentActionListener docListener = createDocumentActionListener();
        homeDocAdapter = new DocumentAdapter(docListener);
        homeMentionAdapter = new DocumentAdapter(docListener);

        categoryDetailAdapter = new DocumentAdapter(docListener);

        filesDocAdapter = new DocumentAdapter(docListener);
        filesMentionAdapter = new DocumentAdapter(docListener);

        reviewAdapter = new CandidateAdapter(new CandidateAdapter.CandidateActionListener() {
            @Override
            public void onCategoryChanged(int position, String newCategory) {
                viewModel.updateCandidateCategory(position, newCategory);
            }

            @Override
            public void onRemoveCandidate(int position) {
                viewModel.removeCandidateFromQueue(position);
            }
        });

        // Setup Centralized Observers once
        setupObservers();

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
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null)
            return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)
                || "com.example.personaldocumentfinder.IMPORT_URI".equals(action)) {
            Uri data = intent.getData();
            if (data == null && intent.hasExtra(Intent.EXTRA_STREAM)) {
                data = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (data == null && intent.hasExtra("import_uri")) {
                String uriStr = intent.getStringExtra("import_uri");
                if (uriStr != null)
                    data = Uri.parse(uriStr);
            }
            if (data != null) {
                viewModel.importDocumentsFromUris(Collections.singletonList(data));
                navigateTo(ScreenType.MY_DOCUMENTS, null);
            }
        }
    }

    private void setupObservers() {
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

        // Import State Observer
        viewModel.getImportState().observe(this, state -> {
            if (state instanceof DocumentViewModel.ImportState.ReviewNeeded) {
                if (currentScreen != ScreenType.REVIEW_IMPORT) {
                    navigateTo(ScreenType.REVIEW_IMPORT, null);
                }
            } else if (state instanceof DocumentViewModel.ImportState.PermissionRequired) {
                showPermissionRationaleDialog();
            }

            // Update scan progress UI on Home or Files
            String progressText = "";
            boolean isProcessing = state instanceof DocumentViewModel.ImportState.Processing;
            if (isProcessing) {
                progressText = ((DocumentViewModel.ImportState.Processing) state).getProgress();
            }

            if (currentScreen == ScreenType.HOME && homeCardScanProgress != null && homeTvScanProgress != null) {
                homeCardScanProgress.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
                homeTvScanProgress.setText(progressText);
            } else if (currentScreen == ScreenType.MY_DOCUMENTS && filesLayoutProgress != null
                    && filesTvProgress != null) {
                filesLayoutProgress.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
                filesTvProgress.setText(progressText);
            }
        });

        // Category counts observer
        viewModel.getCategoryCounts().observe(this, counts -> {
            boolean loaded = Boolean.TRUE.equals(viewModel.getIsDatabaseLoaded().getValue());
            if (homeCategoryAdapter != null) {
                homeCategoryAdapter.setCategoryCounts(counts, loaded);
            }
        });

        // Database loaded / All documents / Search query / Ranked search results
        // observers
        Observer<Object> dataObserver = o -> {
            updateCurrentScreenUI();
        };

        viewModel.getIsDatabaseLoaded().observe(this, dataObserver);
        viewModel.getAllDocuments().observe(this, dataObserver);
        viewModel.getSearchQuery().observe(this, dataObserver);
        viewModel.getIsSearchLoading().observe(this, dataObserver);
        viewModel.getRankedSearchResults().observe(this, dataObserver);
        viewModel.getTotalCount().observe(this, dataObserver);
        viewModel.getCandidateQueue().observe(this, dataObserver);
    }

    private void updateCurrentScreenUI() {
        boolean isLoaded = Boolean.TRUE.equals(viewModel.getIsDatabaseLoaded().getValue());
        List<DocumentEntity> allDocs = viewModel.getAllDocuments().getValue();
        int totalCount = allDocs != null ? allDocs.size() : 0;

        switch (currentScreen) {
            case HOME:
                if (homeBtnMyDocuments != null) {
                    homeBtnMyDocuments
                            .setText(isLoaded ? "📁 My Documents (" + totalCount + ")" : "📁 My Documents (...)");
                }
                if (homeCategoryAdapter != null) {
                    homeCategoryAdapter.setCategoryCounts(viewModel.getCategoryCounts().getValue(), isLoaded);
                }
                updateHomeScreenContent(isLoaded, allDocs);
                break;

            case MY_DOCUMENTS:
                if (filesTvCountLabel != null) {
                    String q = viewModel.getSearchQuery().getValue();
                    boolean isSearching = q != null && !q.trim().isEmpty();
                    RankedSearchResults ranked = viewModel.getRankedSearchResults().getValue();
                    int totalMatches = ranked != null
                            ? (ranked.getPrimaryMatches().size() + ranked.getMentionMatches().size())
                            : 0;
                    if (isLoaded) {
                        if (isSearching) {
                            filesTvCountLabel.setText(totalMatches + " result(s) found");
                        } else {
                            filesTvCountLabel.setText(totalCount + " document(s) stored");
                        }
                    } else {
                        filesTvCountLabel.setText("— document(s) stored");
                    }
                }
                updateFilesScreenContent(isLoaded, allDocs);
                break;

            case CATEGORY_DETAIL:
                if (currentCategoryDetail != null && categoryDetailAdapter != null
                        && categoryDetailTvSubtitle != null) {
                    List<DocumentEntity> catDocs = new ArrayList<>();
                    if (allDocs != null) {
                        for (DocumentEntity d : allDocs) {
                            if (d != null && currentCategoryDetail.equalsIgnoreCase(d.getCategory())) {
                                catDocs.add(d);
                            }
                        }
                    }
                    if (!isLoaded) {
                        categoryDetailTvSubtitle.setText("— document(s)");
                        if (categoryDetailLayoutLoading != null)
                            categoryDetailLayoutLoading.setVisibility(View.VISIBLE);
                        if (categoryDetailLayoutEmpty != null)
                            categoryDetailLayoutEmpty.setVisibility(View.GONE);
                        if (categoryDetailRvDocs != null)
                            categoryDetailRvDocs.setVisibility(View.GONE);
                    } else if (catDocs.isEmpty()) {
                        categoryDetailTvSubtitle.setText("0 document(s)");
                        if (categoryDetailLayoutLoading != null)
                            categoryDetailLayoutLoading.setVisibility(View.GONE);
                        if (categoryDetailLayoutEmpty != null)
                            categoryDetailLayoutEmpty.setVisibility(View.VISIBLE);
                        if (categoryDetailRvDocs != null)
                            categoryDetailRvDocs.setVisibility(View.GONE);
                    } else {
                        categoryDetailTvSubtitle.setText(catDocs.size() + " document(s)");
                        if (categoryDetailLayoutLoading != null)
                            categoryDetailLayoutLoading.setVisibility(View.GONE);
                        if (categoryDetailLayoutEmpty != null)
                            categoryDetailLayoutEmpty.setVisibility(View.GONE);
                        if (categoryDetailRvDocs != null)
                            categoryDetailRvDocs.setVisibility(View.VISIBLE);
                        categoryDetailAdapter.setDocuments(catDocs);
                    }
                }
                break;

            case REVIEW_IMPORT: {
                List<CandidateImportItem> candidates = viewModel.getCandidateQueue().getValue();
                int reviewTotalCount = candidates != null ? candidates.size() : 0;
                int duplicateCount = 0;
                if (candidates != null) {
                    for (CandidateImportItem c : candidates) {
                        if (c != null && c.isDuplicate())
                            duplicateCount++;
                    }
                }
                int importableCount = reviewTotalCount - duplicateCount;
                if (reviewTvSubtitle != null) {
                    if (duplicateCount > 0) {
                        reviewTvSubtitle.setText(reviewTotalCount + " candidate(s) analyzed (" + duplicateCount
                                + " duplicate(s) skipped)");
                    } else {
                        reviewTvSubtitle.setText(reviewTotalCount + " candidate(s) analyzed");
                    }
                }
                if (reviewBtnConfirmAll != null) {
                    reviewBtnConfirmAll.setText("Confirm & Import Selected Documents (" + importableCount + ")");
                    reviewBtnConfirmAll.setEnabled(importableCount > 0);
                }
                if (reviewAdapter != null) {
                    reviewAdapter.setCandidates(candidates);
                }
                break;
            }

            case SETTINGS:
                if (settingsTvTotalDocs != null && settingsTvStorageUsed != null) {
                    settingsTvTotalDocs.setText(totalCount + " document(s)");
                    StorageManager.StorageStats stats = viewModel.getStorageManager().getStorageStats();
                    settingsTvStorageUsed.setText(Formatter.formatShortFileSize(this, stats.getTotalBytes()));
                }
                break;

            default:
                break;
        }
    }

    private void updateHomeScreenContent(boolean isLoaded, List<DocumentEntity> allDocs) {
        if (homeLayoutNormalContent == null)
            return;

        String q = viewModel.getSearchQuery().getValue();
        boolean isSearching = q != null && !q.trim().isEmpty();
        boolean isSearchLoading = Boolean.TRUE.equals(viewModel.getIsSearchLoading().getValue());
        RankedSearchResults ranked = viewModel.getRankedSearchResults().getValue();

        if (isSearching) {
            homeLayoutNormalContent.setVisibility(View.GONE);
            if (homeTvDocsSectionTitle != null)
                homeTvDocsSectionTitle.setText("Search Results");

            if (isSearchLoading) {
                if (homeCardLoadingDocs != null) {
                    homeCardLoadingDocs.setVisibility(View.VISIBLE);
                    homeTvLoadingText.setText("Searching documents...");
                }
                if (homeCardEmptyDocs != null)
                    homeCardEmptyDocs.setVisibility(View.GONE);
                if (homeRvDocuments != null)
                    homeRvDocuments.setVisibility(View.GONE);
                if (homeTvMentionHeader != null)
                    homeTvMentionHeader.setVisibility(View.GONE);
                if (homeRvMentionDocuments != null)
                    homeRvMentionDocuments.setVisibility(View.GONE);
            } else if (ranked == null
                    || (ranked.getPrimaryMatches().isEmpty() && ranked.getMentionMatches().isEmpty())) {
                if (homeCardLoadingDocs != null)
                    homeCardLoadingDocs.setVisibility(View.GONE);
                if (homeCardEmptyDocs != null) {
                    homeCardEmptyDocs.setVisibility(View.VISIBLE);
                    homeTvEmptyTitle.setText("No matching documents found");
                    homeTvEmptySubtitle.setText("Try adjusting search terms or scanning your device.");
                }
                if (homeRvDocuments != null)
                    homeRvDocuments.setVisibility(View.GONE);
                if (homeTvMentionHeader != null)
                    homeTvMentionHeader.setVisibility(View.GONE);
                if (homeRvMentionDocuments != null)
                    homeRvMentionDocuments.setVisibility(View.GONE);
            } else {
                if (homeCardLoadingDocs != null)
                    homeCardLoadingDocs.setVisibility(View.GONE);
                if (homeCardEmptyDocs != null)
                    homeCardEmptyDocs.setVisibility(View.GONE);

                if (homeRvDocuments != null) {
                    homeRvDocuments.setVisibility(View.VISIBLE);
                    homeDocAdapter.setDocuments(ranked.getPrimaryMatches());
                }

                if (!ranked.getMentionMatches().isEmpty()) {
                    if (homeTvMentionHeader != null) {
                        homeTvMentionHeader.setVisibility(View.VISIBLE);
                        homeTvMentionHeader.setText(ranked.getPrimaryMatches().isEmpty()
                                ? "Documents mentioning \"" + q + "\""
                                : "Other documents mentioning \"" + q + "\"");
                    }
                    if (homeRvMentionDocuments != null) {
                        homeRvMentionDocuments.setVisibility(View.VISIBLE);
                        homeMentionAdapter.setDocuments(ranked.getMentionMatches());
                    }
                } else {
                    if (homeTvMentionHeader != null)
                        homeTvMentionHeader.setVisibility(View.GONE);
                    if (homeRvMentionDocuments != null)
                        homeRvMentionDocuments.setVisibility(View.GONE);
                }
            }
        } else {
            homeLayoutNormalContent.setVisibility(View.VISIBLE);
            if (homeTvDocsSectionTitle != null)
                homeTvDocsSectionTitle.setText("Recent Documents");
            if (homeTvMentionHeader != null)
                homeTvMentionHeader.setVisibility(View.GONE);
            if (homeRvMentionDocuments != null)
                homeRvMentionDocuments.setVisibility(View.GONE);

            if (!isLoaded) {
                if (homeCardLoadingDocs != null) {
                    homeCardLoadingDocs.setVisibility(View.VISIBLE);
                    homeTvLoadingText.setText("Loading documents...");
                }
                if (homeCardEmptyDocs != null)
                    homeCardEmptyDocs.setVisibility(View.GONE);
                if (homeRvDocuments != null)
                    homeRvDocuments.setVisibility(View.GONE);
            } else if (allDocs == null || allDocs.isEmpty()) {
                if (homeCardLoadingDocs != null)
                    homeCardLoadingDocs.setVisibility(View.GONE);
                if (homeCardEmptyDocs != null) {
                    homeCardEmptyDocs.setVisibility(View.VISIBLE);
                    homeTvEmptyTitle.setText("No documents imported yet");
                    homeTvEmptySubtitle.setText(
                            "Tap 'Scan Device' above to search external folders, or 'Add Files' to select manually.");
                }
                if (homeRvDocuments != null)
                    homeRvDocuments.setVisibility(View.GONE);
            } else {
                if (homeCardLoadingDocs != null)
                    homeCardLoadingDocs.setVisibility(View.GONE);
                if (homeCardEmptyDocs != null)
                    homeCardEmptyDocs.setVisibility(View.GONE);
                if (homeRvDocuments != null) {
                    homeRvDocuments.setVisibility(View.VISIBLE);
                    List<DocumentEntity> recent = allDocs.size() > 10 ? allDocs.subList(0, 10) : allDocs;
                    homeDocAdapter.setDocuments(recent);
                }
            }
        }
    }

    private void updateFilesScreenContent(boolean isLoaded, List<DocumentEntity> allDocs) {
        if (filesRvDocs == null)
            return;

        String q = viewModel.getSearchQuery().getValue();
        boolean isSearching = q != null && !q.trim().isEmpty();
        boolean isSearchLoading = Boolean.TRUE.equals(viewModel.getIsSearchLoading().getValue());
        RankedSearchResults ranked = viewModel.getRankedSearchResults().getValue();

        if (isSearching) {
            if (filesTvMentionHeader != null)
                filesTvMentionHeader.setVisibility(View.GONE);
            if (filesRvMentionDocs != null)
                filesRvMentionDocs.setVisibility(View.GONE);

            if (isSearchLoading) {
                if (filesLayoutLoading != null) {
                    filesLayoutLoading.setVisibility(View.VISIBLE);
                    filesTvLoadingText.setText("Searching documents...");
                }
                if (filesLayoutEmpty != null)
                    filesLayoutEmpty.setVisibility(View.GONE);
                if (filesRvDocs != null)
                    filesRvDocs.setVisibility(View.GONE);
            } else if (ranked == null
                    || (ranked.getPrimaryMatches().isEmpty() && ranked.getMentionMatches().isEmpty())) {
                if (filesLayoutLoading != null)
                    filesLayoutLoading.setVisibility(View.GONE);
                if (filesLayoutEmpty != null) {
                    filesLayoutEmpty.setVisibility(View.VISIBLE);
                    filesTvEmptyTitle.setText("No matching documents found");
                }
                if (filesRvDocs != null)
                    filesRvDocs.setVisibility(View.GONE);
            } else {
                if (filesLayoutLoading != null)
                    filesLayoutLoading.setVisibility(View.GONE);
                if (filesLayoutEmpty != null)
                    filesLayoutEmpty.setVisibility(View.GONE);
                if (filesRvDocs != null) {
                    filesRvDocs.setVisibility(View.VISIBLE);
                    filesDocAdapter.setDocuments(ranked.getPrimaryMatches());
                }

                if (!ranked.getMentionMatches().isEmpty()) {
                    if (filesTvMentionHeader != null) {
                        filesTvMentionHeader.setVisibility(View.VISIBLE);
                        filesTvMentionHeader.setText(ranked.getPrimaryMatches().isEmpty()
                                ? "Documents mentioning \"" + q + "\""
                                : "Other documents mentioning \"" + q + "\"");
                    }
                    if (filesRvMentionDocs != null) {
                        filesRvMentionDocs.setVisibility(View.VISIBLE);
                        filesVirtualMentionAdapterSet(ranked.getMentionMatches());
                    }
                } else {
                    if (filesTvMentionHeader != null)
                        filesTvMentionHeader.setVisibility(View.GONE);
                    if (filesRvMentionDocs != null)
                        filesRvMentionDocs.setVisibility(View.GONE);
                }
            }
        } else {
            if (filesTvMentionHeader != null)
                filesTvMentionHeader.setVisibility(View.GONE);
            if (filesRvMentionDocs != null)
                filesRvMentionDocs.setVisibility(View.GONE);

            if (!isLoaded) {
                if (filesLayoutLoading != null) {
                    filesLayoutLoading.setVisibility(View.VISIBLE);
                    filesTvLoadingText.setText("Loading documents...");
                }
                if (filesLayoutEmpty != null)
                    filesLayoutEmpty.setVisibility(View.GONE);
                if (filesRvDocs != null)
                    filesRvDocs.setVisibility(View.GONE);
            } else if (allDocs == null || allDocs.isEmpty()) {
                if (filesLayoutLoading != null)
                    filesLayoutLoading.setVisibility(View.GONE);
                if (filesLayoutEmpty != null) {
                    filesLayoutEmpty.setVisibility(View.VISIBLE);
                    filesTvEmptyTitle.setText("No documents imported yet");
                }
                if (filesRvDocs != null)
                    filesRvDocs.setVisibility(View.GONE);
            } else {
                if (filesLayoutLoading != null)
                    filesLayoutLoading.setVisibility(View.GONE);
                if (filesLayoutEmpty != null)
                    filesLayoutEmpty.setVisibility(View.GONE);
                if (filesRvDocs != null) {
                    filesRvDocs.setVisibility(View.VISIBLE);
                    filesDocAdapter.setDocuments(allDocs);
                }
            }
        }
    }

    private void filesVirtualMentionAdapterSet(List<DocumentEntity> mentionMatches) {
        if (filesMentionAdapter != null) {
            filesMentionAdapter.setDocuments(mentionMatches);
        }
    }

    public void navigateTo(ScreenType screen, String extra) {
        this.currentScreen = screen;
        this.currentCategoryDetail = extra;
        screenContainer.removeAllViews();

        // Clear view references for inactive screens
        clearScreenViewReferences();

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
        updateCurrentScreenUI();
    }

    private void clearScreenViewReferences() {
        homeLayoutNormalContent = null;
        homeCardScanProgress = null;
        homeTvScanProgress = null;
        homeTvDocsSectionTitle = null;
        homeCardLoadingDocs = null;
        homeTvLoadingText = null;
        homeCardEmptyDocs = null;
        homeTvEmptyTitle = null;
        homeTvEmptySubtitle = null;
        homeRvDocuments = null;
        homeTvMentionHeader = null;
        homeRvMentionDocuments = null;
        homeBtnMyDocuments = null;

        categoryDetailTvSubtitle = null;
        categoryDetailLayoutLoading = null;
        categoryDetailTvLoadingText = null;
        categoryDetailLayoutEmpty = null;
        categoryDetailTvEmptyTitle = null;
        categoryDetailRvDocs = null;

        filesLayoutProgress = null;
        filesTvProgress = null;
        filesTvCountLabel = null;
        filesLayoutLoading = null;
        filesTvLoadingText = null;
        filesLayoutEmpty = null;
        filesTvEmptyTitle = null;
        filesRvDocs = null;
        filesTvMentionHeader = null;
        filesRvMentionDocs = null;

        reviewTvSubtitle = null;
        reviewBtnConfirmAll = null;

        settingsTvStorageUsed = null;
        settingsTvTotalDocs = null;
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
        homeLayoutNormalContent = view.findViewById(R.id.layout_normal_content);
        Button btnScanDevice = view.findViewById(R.id.btn_scan_device);
        Button btnAddFiles = view.findViewById(R.id.btn_add_files);
        homeBtnMyDocuments = view.findViewById(R.id.btn_my_documents);

        homeCardScanProgress = view.findViewById(R.id.card_scan_progress);
        homeTvScanProgress = view.findViewById(R.id.tv_scan_progress);
        Button btnStopScan = view.findViewById(R.id.btn_stop_scan);

        RecyclerView rvCategories = view.findViewById(R.id.rv_categories);
        homeTvDocsSectionTitle = view.findViewById(R.id.tv_documents_section_title);
        homeCardLoadingDocs = view.findViewById(R.id.card_loading_documents);
        homeTvLoadingText = view.findViewById(R.id.tv_loading_text);
        homeCardEmptyDocs = view.findViewById(R.id.card_empty_documents);
        homeTvEmptyTitle = view.findViewById(R.id.tv_empty_title);
        homeTvEmptySubtitle = view.findViewById(R.id.tv_empty_subtitle);

        homeRvDocuments = view.findViewById(R.id.rv_documents);
        homeTvMentionHeader = view.findViewById(R.id.tv_mention_header);
        homeRvMentionDocuments = view.findViewById(R.id.rv_mention_documents);

        btnSettings.setOnClickListener(v -> navigateTo(ScreenType.SETTINGS, null));

        btnScanDevice.setOnClickListener(v -> viewModel.startDeviceScan());
        btnStopScan.setOnClickListener(v -> viewModel.stopDeviceScan());

        btnAddFiles.setOnClickListener(v -> filePickerLauncher.launch(new String[] {
                "application/pdf",
                "image/*",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }));

        homeBtnMyDocuments.setOnClickListener(v -> navigateTo(ScreenType.MY_DOCUMENTS, null));

        // Categories Grid Setup
        rvCategories.setLayoutManager(new GridLayoutManager(this, 2));
        rvCategories.setAdapter(homeCategoryAdapter);

        // Document Adapters Setup
        homeRvDocuments.setLayoutManager(new LinearLayoutManager(this));
        homeRvDocuments.setAdapter(homeDocAdapter);

        homeRvMentionDocuments.setLayoutManager(new LinearLayoutManager(this));
        homeRvMentionDocuments.setAdapter(homeMentionAdapter);

        // Search text change listener
        etSearch.setText(viewModel.getSearchQuery().getValue());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                viewModel.setSearchQuery(q);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            viewModel.setSearchQuery("");
        });

        screenContainer.addView(view);
    }

    private void renderCategoryDetailScreen(final String category) {
        View view = LayoutInflater.from(this).inflate(R.layout.view_category_detail, screenContainer, false);

        Button btnBack = view.findViewById(R.id.btn_category_back);
        TextView tvTitle = view.findViewById(R.id.tv_category_title);
        categoryDetailTvSubtitle = view.findViewById(R.id.tv_category_count_subtitle);
        categoryDetailLayoutLoading = view.findViewById(R.id.layout_category_loading);
        categoryDetailTvLoadingText = view.findViewById(R.id.tv_category_loading_text);
        categoryDetailLayoutEmpty = view.findViewById(R.id.layout_category_empty);
        categoryDetailTvEmptyTitle = view.findViewById(R.id.tv_category_empty_title);
        categoryDetailRvDocs = view.findViewById(R.id.rv_category_documents);

        tvTitle.setText(category != null ? category : "");
        categoryDetailTvLoadingText.setText("Loading " + (category != null ? category : "") + " documents...");
        categoryDetailTvEmptyTitle.setText("No documents in " + (category != null ? category : ""));

        btnBack.setOnClickListener(v -> navigateTo(ScreenType.HOME, null));

        categoryDetailRvDocs.setLayoutManager(new LinearLayoutManager(this));
        categoryDetailRvDocs.setAdapter(categoryDetailAdapter);

        screenContainer.addView(view);
    }

    private void renderFilesScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.view_files, screenContainer, false);

        Button btnBack = view.findViewById(R.id.btn_files_back);
        EditText etSearch = view.findViewById(R.id.et_files_search);
        TextView btnClearSearch = view.findViewById(R.id.btn_clear_files_search);
        Button btnAddDocs = view.findViewById(R.id.btn_files_add_documents);
        filesLayoutProgress = view.findViewById(R.id.layout_files_progress);
        filesTvProgress = view.findViewById(R.id.tv_files_progress);
        filesTvCountLabel = view.findViewById(R.id.tv_files_count_label);

        filesLayoutLoading = view.findViewById(R.id.layout_files_loading);
        filesTvLoadingText = view.findViewById(R.id.tv_files_loading_text);
        filesLayoutEmpty = view.findViewById(R.id.layout_files_empty);
        filesTvEmptyTitle = view.findViewById(R.id.tv_files_empty_title);

        filesRvDocs = view.findViewById(R.id.rv_files_documents);
        filesTvMentionHeader = view.findViewById(R.id.tv_files_mention_header);
        filesRvMentionDocs = view.findViewById(R.id.rv_files_mention_documents);

        btnBack.setOnClickListener(v -> navigateTo(ScreenType.HOME, null));

        btnAddDocs.setOnClickListener(v -> filePickerLauncher.launch(new String[] {
                "application/pdf",
                "image/*",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }));

        filesRvDocs.setLayoutManager(new LinearLayoutManager(this));
        filesRvDocs.setAdapter(filesDocAdapter);

        filesRvMentionDocs.setLayoutManager(new LinearLayoutManager(this));
        filesRvMentionDocs.setAdapter(filesMentionAdapter);

        etSearch.setText(viewModel.getSearchQuery().getValue());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                viewModel.setSearchQuery(q);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            viewModel.setSearchQuery("");
        });

        screenContainer.addView(view);
    }

    private void renderReviewImportScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.view_review_import, screenContainer, false);

        Button btnCancel = view.findViewById(R.id.btn_review_cancel);
        reviewTvSubtitle = view.findViewById(R.id.tv_review_count_subtitle);
        RecyclerView rvCandidates = view.findViewById(R.id.rv_candidates);
        reviewBtnConfirmAll = view.findViewById(R.id.btn_review_confirm_all);

        btnCancel.setOnClickListener(v -> {
            viewModel.clearCandidateQueue();
            navigateTo(ScreenType.HOME, null);
        });

        rvCandidates.setLayoutManager(new LinearLayoutManager(this));
        rvCandidates.setAdapter(reviewAdapter);

        reviewBtnConfirmAll.setOnClickListener(v -> {
            List<CandidateImportItem> queue = viewModel.getCandidateQueue().getValue();
            if (queue != null && !queue.isEmpty()) {
                viewModel.confirmCandidateImports(queue);
            }
            navigateTo(ScreenType.MY_DOCUMENTS, null);
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
        settingsTvStorageUsed = view.findViewById(R.id.tv_settings_storage_used);
        settingsTvTotalDocs = view.findViewById(R.id.tv_settings_total_docs);

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
                .setMessage("Are you sure you want to remove '" + document.getEffectiveDisplayName()
                        + "' from Personal Document Finder? Your original external file will remain safe and untouched.")
                .setPositiveButton("Remove", (dialog, which) -> viewModel.deleteDocument(document))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPermissionRationaleDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Storage Access Required")
                .setMessage(
                        "To automatically discover and index your personal documents across device folders (PDFs, receipts, identity cards, hall tickets), Personal Document Finder requires All Files Access permission.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    viewModel.clearCandidateQueue();
                    Intent intent = PermissionManager.getManageStorageIntent(MainActivity.this);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> viewModel.clearCandidateQueue())
                .show();
    }
}
