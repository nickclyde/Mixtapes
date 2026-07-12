package tech.clyde.mixtapes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.clyde.mixtapes.ui.WizardStep
import tech.clyde.mixtapes.ui.WizardViewModel
import tech.clyde.mixtapes.ui.screens.DoneScreen
import tech.clyde.mixtapes.ui.screens.ErrorScreen
import tech.clyde.mixtapes.ui.screens.InputScreen
import tech.clyde.mixtapes.ui.screens.ReviewScreen
import tech.clyde.mixtapes.ui.screens.SetupScreen
import tech.clyde.mixtapes.ui.screens.WorkingScreen
import tech.clyde.mixtapes.ui.theme.MixtapesTheme

class MainActivity : ComponentActivity() {
    private val wizardViewModel: WizardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSendIntent(intent)
        setContent {
            MixtapesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WizardApp(wizardViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(wizardViewModel::onSharedText)
        }
    }
}

@Composable
private fun WizardApp(viewModel: WizardViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    BackHandler(enabled = state.step is WizardStep.Review || state.step is WizardStep.Error) {
        viewModel.backToInput()
    }

    androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
        when (val step = state.step) {
            WizardStep.Setup -> SetupScreen(
                state = state,
                onEsDePicked = viewModel::onEsDePicked,
                onRomsPicked = viewModel::onRomsPicked,
                onWriteAbsolutePathsChange = viewModel::setWriteAbsolutePaths,
                onLlmApiKeyChange = viewModel::setLlmApiKey,
                onLlmBaseUrlChange = viewModel::setLlmBaseUrl,
                onLlmModelChange = viewModel::setLlmModel,
                onContinue = viewModel::continueFromSetup,
            )
            WizardStep.Input -> InputScreen(
                initialUrl = state.sharedUrl,
                useTranscript = state.useTranscript,
                llmConfigured = state.llmConfigured,
                onUseTranscriptChange = viewModel::setUseTranscript,
                onSubmitUrl = viewModel::makeMixtapeFromUrl,
                onSubmitPastedText = viewModel::makeMixtapeFromPastedText,
                onChangeFolders = viewModel::changeFolders,
            )
            is WizardStep.Working -> WorkingScreen(phase = step.phase, progress = step.progress)
            WizardStep.Review -> ReviewScreen(
                state = state,
                allRoms = viewModel.allRoms(),
                onNameChange = viewModel::setCollectionName,
                onRowIncluded = viewModel::setRowIncluded,
                onPickRom = viewModel::pickRomForRow,
                onWrite = { viewModel.writeCollection() },
                onConfirmOverwrite = { viewModel.writeCollection(overwrite = true) },
                onDismissOverwrite = viewModel::dismissOverwritePrompt,
            )
            is WizardStep.Done -> DoneScreen(
                fileName = step.fileName,
                collectionName = step.collectionName,
                gameCount = step.gameCount,
                missing = step.missing,
                onMakeAnother = viewModel::backToInput,
            )
            is WizardStep.Error -> ErrorScreen(
                error = step.error,
                detail = step.detail,
                onBackToInput = viewModel::backToInput,
                onRetryTranscript = if (step.canRetryWithTranscript) viewModel::retryWithTranscript else null,
            )
        }
    }
}
