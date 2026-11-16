package com.example.prowoflowdocscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.prowoflowdocscan.ui.DocumentDetailScreen
import com.example.prowoflowdocscan.ui.GalleryScreen
import com.example.prowoflowdocscan.ui.theme.ProWoFlowDocScanTheme
import com.example.prowoflowdocscan.viewmodel.DocumentViewModel
import kotlinx.serialization.Serializable

@Serializable
object DocumentGalleryList : NavKey

@Serializable
data class DocumentDetail(val documentId: String, val path: String) : NavKey

@Serializable
object SettingsRoute : NavKey

class MainActivity : ComponentActivity() {
    private val viewModel: DocumentViewModel by viewModels()

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProWoFlowDocScanTheme {
                val backStack = rememberNavBackStack(DocumentGalleryList)

                val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
                val directive = remember(windowAdaptiveInfo) {
                    calculatePaneScaffoldDirective(windowAdaptiveInfo)
                        .copy(horizontalPartitionSpacerSize = 0.dp)
                }
                val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategies = listOf(listDetailStrategy),
                    entryProvider = entryProvider {
                        entry<DocumentGalleryList>(
                            metadata = ListDetailSceneStrategy.listPane(
                                detailPlaceholder = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Select a document to preview")
                                    }
                                }
                            )
                        ) {
                            GalleryScreen(
                                viewModel = viewModel,
                                onDocumentClick = { (id, _, path) ->
                                    backStack.add(DocumentDetail(id, path))
                                }
                            ) {
                                backStack.add(SettingsRoute)
                            }
                        }

                        entry<DocumentDetail>(
                            metadata = ListDetailSceneStrategy.detailPane()
                        ) { detailKey ->
                            DocumentDetailScreen(
                                detail = detailKey,
                                viewModel = viewModel,
                                onDocumentDeleted = {
                                    backStack.removeLastOrNull()
                                },
                                onDocumentRenamed = { newId, newPath ->
                                    backStack.removeLastOrNull()
                                    backStack.add(DocumentDetail(newId, newPath))
                                }
                            )
                        }

                        entry<SettingsRoute> {
                            com.example.prowoflowdocscan.ui.SettingsScreen(
                                onBack = { backStack.removeLastOrNull() }
                            )
                        }
                    }
                )
            }
        }
    }
}
