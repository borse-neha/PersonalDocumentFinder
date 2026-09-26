package com.example.personaldocumentfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
                                onConfirmAll = { deleteOriginals ->
                                    viewModel.confirmCandidateImports(candidateQueue, deleteOriginals)
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
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PERSONAL DOCUMENT\nFINDER",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Find, organize, and search your documents quickly with on-device privacy.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onGetStarted) {
            Text(
                text = "Get Started",
                fontSize = 16.sp
            )
        }
    }
}
