package com.example.personaldocumentfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personaldocumentfinder.ui.CategoryDetailScreen
import com.example.personaldocumentfinder.ui.DocumentViewModel
import com.example.personaldocumentfinder.ui.FilesScreen
import com.example.personaldocumentfinder.ui.HomeScreen
import com.example.personaldocumentfinder.ui.ReviewImportScreen
import com.example.personaldocumentfinder.ui.SettingsScreen
import com.example.personaldocumentfinder.ui.theme.PersonalDocumentFinderTheme

sealed class Screen {
    object Welcome : Screen()
    object Home : Screen()
    data class CategoryDetail(val categoryName: String) : Screen()
    object MyDocuments : Screen()
    object ReviewImport : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: DocumentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            android.system.Os.chmod(applicationInfo.dataDir, 448) // 0700
        } catch (_: Exception) {}

        enableEdgeToEdge()

        setContent {
            val themeState by viewModel.themeState.collectAsState()
            val isDarkTheme = when (themeState) {
                "Light" -> false
                "Dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            PersonalDocumentFinderTheme(darkTheme = isDarkTheme) {
                val snackbarHostState = remember { SnackbarHostState() }
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Welcome) }
                val importState by viewModel.importState.collectAsState()
                val candidateQueue by viewModel.candidateQueue.collectAsState()

                // Intercept back gestures when on sub-screens so the app returns to Home rather than exiting
                BackHandler(enabled = currentScreen !is Screen.Home && currentScreen !is Screen.Welcome) {
                    if (currentScreen is Screen.ReviewImport) {
                        viewModel.clearCandidateQueue()
                    }
                    currentScreen = Screen.Home
                }

                // Auto navigate to ReviewImport when scan analysis completes
                if (importState is DocumentViewModel.ImportState.ReviewNeeded && currentScreen !is Screen.ReviewImport) {
                    currentScreen = Screen.ReviewImport
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->

                    when (val screen = currentScreen) {
                        is Screen.Welcome -> {
                            WelcomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                onGetStarted = { currentScreen = Screen.Home }
                            )
                        }
                        is Screen.Home -> {
                            HomeScreen(
                                viewModel = viewModel,
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier.padding(innerPadding),
                                onCategorySelected = { category ->
                                    currentScreen = Screen.CategoryDetail(category)
                                },
                                onFilesClick = { currentScreen = Screen.MyDocuments },
                                onSettingsClick = { currentScreen = Screen.Settings }
                            )
                        }
                        is Screen.CategoryDetail -> {
                            CategoryDetailScreen(
                                category = screen.categoryName,
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding),
                                onBack = { currentScreen = Screen.Home }
                            )
                        }
                        is Screen.MyDocuments -> {
                            FilesScreen(
                                viewModel = viewModel,
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier.padding(innerPadding),
                                onBack = { currentScreen = Screen.Home }
                            )
                        }
                        is Screen.ReviewImport -> {
                            ReviewImportScreen(
                                candidates = candidateQueue,
                                modifier = Modifier.padding(innerPadding),
                                onCategoryChanged = { index, newCat ->
                                    viewModel.updateCandidateCategory(index, newCat)
                                },
                                onRemoveCandidate = { index ->
                                    viewModel.removeCandidateFromQueue(index)
                                },
                                onConfirmAll = {
                                    viewModel.confirmCandidateImports(candidateQueue)
                                    currentScreen = Screen.Home
                                },
                                onCancel = {
                                    viewModel.clearCandidateQueue()
                                    currentScreen = Screen.Home
                                }
                            )
                        }
                        is Screen.Settings -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding),
                                onBack = { currentScreen = Screen.Home }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onGetStarted: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📄",
            fontSize = 72.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Personal Document Finder",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Easily find, organize, and view all your personal documents on this device.",
            fontSize = 16.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}
