package tech.clyde.mixtapes.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.clyde.mixtapes.core.chapters.ChapterFilter
import tech.clyde.mixtapes.core.chapters.ChapterParser
import tech.clyde.mixtapes.core.collection.CollectionName
import tech.clyde.mixtapes.core.collection.CollectionWriter
import tech.clyde.mixtapes.core.match.Matcher
import tech.clyde.mixtapes.core.model.Chapter
import tech.clyde.mixtapes.core.model.MatchResult
import tech.clyde.mixtapes.core.model.RomFile
import tech.clyde.mixtapes.core.search.ArchiveSearch
import tech.clyde.mixtapes.core.search.MissingGame
import tech.clyde.mixtapes.core.youtube.VideoMetadata
import tech.clyde.mixtapes.llm.LlmClient
import tech.clyde.mixtapes.saf.CollectionFileWriter
import tech.clyde.mixtapes.saf.DirPrefs
import tech.clyde.mixtapes.saf.RomScanner
import tech.clyde.mixtapes.saf.TreePathGuesser
import tech.clyde.mixtapes.youtube.TranscriptClient
import tech.clyde.mixtapes.youtube.YouTubeClient

enum class WorkPhase { FETCHING, TRANSCRIBING, EXTRACTING, SCANNING, MATCHING }

enum class WizardError {
    INVALID_URL, NETWORK, EXTRACTION, NO_CHAPTERS, EMPTY_LIBRARY, WRITE_FAILED,
    NO_TRANSCRIPT, NO_API_KEY, LLM_ERROR,
}

sealed interface WizardStep {
    data object Setup : WizardStep
    data object Input : WizardStep
    data class Working(val phase: WorkPhase, val progress: Float? = null) : WizardStep
    data object Review : WizardStep
    data class Done(
        val fileName: String,
        val collectionName: String,
        val gameCount: Int,
        val missing: List<MissingGame> = emptyList(),
    ) : WizardStep
    data class Error(
        val error: WizardError,
        val detail: String? = null,
        val canRetryWithTranscript: Boolean = false,
    ) : WizardStep
}

data class ReviewRow(
    val chapter: Chapter,
    val result: MatchResult,
    val selected: RomFile?,
    val included: Boolean,
) {
    val confident: Boolean get() = result is MatchResult.Auto
}

data class WizardState(
    val step: WizardStep = WizardStep.Setup,
    val esDePath: String? = null,
    val romsPath: String? = null,
    val esDePicked: Boolean = false,
    val romsPicked: Boolean = false,
    val writeAbsolutePaths: Boolean = false,
    val useTranscript: Boolean = false,
    val llmConfigured: Boolean = false,
    val llmBaseUrl: String = "",
    val llmModel: String = "",
    val sharedUrl: String = "",
    val collectionName: String = "",
    val rows: List<ReviewRow> = emptyList(),
    val showOverwritePrompt: Boolean = false,
) {
    val includedCount: Int get() = rows.count { it.included && it.selected != null }
}

class WizardViewModel(application: Application) : AndroidViewModel(application) {

    private val dirPrefs = DirPrefs(application)
    private val scanner = RomScanner(application.contentResolver)
    private val fileWriter = CollectionFileWriter(application.contentResolver)
    private val youTube = YouTubeClient()
    private val transcriptClient = TranscriptClient()
    private val llmClient = LlmClient()

    private val _state = MutableStateFlow(WizardState())
    val state: StateFlow<WizardState> = _state.asStateFlow()

    /** ROM scan cached per app session; a rescan requires relaunching the app. */
    private var cachedRoms: List<RomFile>? = null

    /**
     * Last successful fetch, so the NO_CHAPTERS error can retry via transcript
     * without hitting YouTube again. Cleared by the pasted-text path, which has
     * no video to transcribe.
     */
    private var lastFetch: Pair<String, VideoMetadata>? = null

    init {
        refreshSetup()
    }

    // ---- Setup ----

    fun refreshSetup() {
        val ready = dirPrefs.isReady()
        _state.update {
            it.copy(
                step = if (ready) WizardStep.Input else WizardStep.Setup,
                esDePicked = dirPrefs.esDeTreeUri != null,
                romsPicked = dirPrefs.romsTreeUri != null,
                esDePath = dirPrefs.esDeTreeUri?.let(TreePathGuesser::guessPath),
                romsPath = dirPrefs.romsTreeUri?.let(TreePathGuesser::guessPath),
                writeAbsolutePaths = dirPrefs.writeAbsolutePaths,
                llmConfigured = dirPrefs.llmConfigured(),
                llmBaseUrl = dirPrefs.llmBaseUrl,
                llmModel = dirPrefs.llmModel,
            )
        }
    }

    /** A YouTube link arrived via the share sheet. */
    fun onSharedText(text: String) {
        val url = Regex("""https?://\S+""").findAll(text)
            .map { it.value }
            .firstOrNull { YouTubeClient.videoId(it) != null }
            ?: return
        _state.update { state ->
            when (state.step) {
                WizardStep.Input, is WizardStep.Done, is WizardStep.Error ->
                    state.copy(step = WizardStep.Input, sharedUrl = url)
                else -> state.copy(sharedUrl = url)
            }
        }
    }

    fun setWriteAbsolutePaths(enabled: Boolean) {
        dirPrefs.writeAbsolutePaths = enabled
        _state.update { it.copy(writeAbsolutePaths = enabled) }
    }

    fun changeFolders() {
        _state.update { it.copy(step = WizardStep.Setup) }
    }

    fun setUseTranscript(enabled: Boolean) {
        _state.update { it.copy(useTranscript = enabled) }
    }

    fun setLlmApiKey(key: String) {
        dirPrefs.llmApiKey = key
        _state.update { it.copy(llmConfigured = dirPrefs.llmConfigured()) }
    }

    fun setLlmBaseUrl(url: String) {
        dirPrefs.llmBaseUrl = url
        _state.update { it.copy(llmBaseUrl = dirPrefs.llmBaseUrl) }
    }

    fun setLlmModel(model: String) {
        dirPrefs.llmModel = model
        _state.update { it.copy(llmModel = dirPrefs.llmModel) }
    }

    fun onEsDePicked(uri: Uri) {
        dirPrefs.takePersistable(uri, write = true)
        dirPrefs.esDeTreeUri = uri
        refreshSetupPaths()
    }

    fun onRomsPicked(uri: Uri) {
        dirPrefs.takePersistable(uri, write = false)
        dirPrefs.romsTreeUri = uri
        cachedRoms = null
        refreshSetupPaths()
    }

    private fun refreshSetupPaths() {
        _state.update {
            it.copy(
                esDePicked = dirPrefs.esDeTreeUri != null,
                romsPicked = dirPrefs.romsTreeUri != null,
                esDePath = dirPrefs.esDeTreeUri?.let(TreePathGuesser::guessPath),
                romsPath = dirPrefs.romsTreeUri?.let(TreePathGuesser::guessPath),
            )
        }
    }

    fun continueFromSetup() {
        if (dirPrefs.isReady()) _state.update { it.copy(step = WizardStep.Input) }
    }

    // ---- Input / pipeline ----

    fun makeMixtapeFromUrl(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(step = WizardStep.Working(WorkPhase.FETCHING)) }
            val trimmed = url.trim()
            when (val fetched = youTube.fetch(trimmed)) {
                is YouTubeClient.FetchResult.Failure -> {
                    val error = when (fetched.error) {
                        YouTubeClient.FetchError.INVALID_URL -> WizardError.INVALID_URL
                        YouTubeClient.FetchError.NETWORK -> WizardError.NETWORK
                        YouTubeClient.FetchError.EXTRACTION -> WizardError.EXTRACTION
                    }
                    _state.update { it.copy(step = WizardStep.Error(error)) }
                }
                is YouTubeClient.FetchResult.Success -> {
                    val videoId = YouTubeClient.videoId(trimmed)
                    lastFetch = videoId?.let { it to fetched.metadata }
                    val defaultName = CollectionName.fromVideoTitle(fetched.metadata.title)
                    if (state.value.useTranscript && videoId != null) {
                        runTranscriptPipeline(videoId, fetched.metadata, defaultName)
                    } else {
                        runChapterPipeline(fetched.metadata.description, defaultName)
                    }
                }
            }
        }
    }

    fun makeMixtapeFromPastedText(text: String) {
        lastFetch = null
        viewModelScope.launch { runChapterPipeline(description = text, defaultName = "mixtape") }
    }

    /** From the NO_CHAPTERS error screen: rerun the last fetched video through the transcript path. */
    fun retryWithTranscript() {
        val (videoId, metadata) = lastFetch ?: run {
            backToInput()
            return
        }
        viewModelScope.launch {
            runTranscriptPipeline(videoId, metadata, CollectionName.fromVideoTitle(metadata.title))
        }
    }

    private suspend fun runChapterPipeline(description: String, defaultName: String) {
        val chapters = ChapterFilter.markSkipped(ChapterParser.parse(description))
        if (chapters.isEmpty()) {
            _state.update {
                it.copy(
                    step = WizardStep.Error(
                        WizardError.NO_CHAPTERS,
                        // Offered even without an API key; the retry then explains itself
                        // with a NO_API_KEY error pointing at Settings.
                        canRetryWithTranscript = lastFetch != null,
                    ),
                )
            }
            return
        }
        scanMatchReview(chapters, defaultName)
    }

    private suspend fun runTranscriptPipeline(videoId: String, metadata: VideoMetadata, defaultName: String) {
        if (!dirPrefs.llmConfigured()) {
            _state.update { it.copy(step = WizardStep.Error(WizardError.NO_API_KEY)) }
            return
        }

        _state.update { it.copy(step = WizardStep.Working(WorkPhase.TRANSCRIBING)) }
        val transcript = when (val fetched = transcriptClient.fetch(videoId, metadata.captionTracks)) {
            is TranscriptClient.Result.Failure -> {
                val error = when (fetched.error) {
                    TranscriptClient.Error.NETWORK -> WizardError.NETWORK
                    TranscriptClient.Error.NO_TRACKS,
                    TranscriptClient.Error.EMPTY,
                    TranscriptClient.Error.PARSE,
                    -> WizardError.NO_TRANSCRIPT
                }
                _state.update { it.copy(step = WizardStep.Error(error)) }
                return
            }
            is TranscriptClient.Result.Success -> fetched.plainText
        }

        _state.update { it.copy(step = WizardStep.Working(WorkPhase.EXTRACTING)) }
        val config = LlmClient.Config(
            baseUrl = dirPrefs.llmBaseUrl,
            apiKey = dirPrefs.llmApiKey ?: return,
            model = dirPrefs.llmModel,
        )
        val titles = when (val result = llmClient.extractGameTitles(metadata.title, transcript, config)) {
            is LlmClient.Result.Failure -> {
                val detail = when (result.error) {
                    LlmClient.Error.CONFIG ->
                        "The AI settings look invalid — check the base URL and API key in Settings."
                    LlmClient.Error.NETWORK -> "Couldn't reach the AI endpoint."
                    LlmClient.Error.HTTP -> result.detail
                    LlmClient.Error.PARSE -> "The model's response couldn't be read."
                    LlmClient.Error.EMPTY -> "The model found no games in the transcript."
                }
                _state.update { it.copy(step = WizardStep.Error(WizardError.LLM_ERROR, detail)) }
                return
            }
            is LlmClient.Result.Success -> result.titles
        }

        // ChapterFilter as a safety net: the model may still emit "Intro"/"Honorable
        // Mentions"-style entries despite the prompt. Timestamps don't exist here and
        // nothing downstream reads Chapter.seconds.
        val chapters = ChapterFilter.markSkipped(titles.map { Chapter(title = it, seconds = 0) })
        scanMatchReview(chapters, defaultName)
    }

    private suspend fun scanMatchReview(chapters: List<Chapter>, defaultName: String) {
        val romsUri = dirPrefs.romsTreeUri
        if (romsUri == null) {
            _state.update { it.copy(step = WizardStep.Setup) }
            return
        }
        _state.update { it.copy(step = WizardStep.Working(WorkPhase.SCANNING)) }
        val roms = cachedRoms ?: scanner.scan(romsUri) { scanned, total ->
            val progress = if (total == 0) null else scanned.toFloat() / total
            _state.update { it.copy(step = WizardStep.Working(WorkPhase.SCANNING, progress)) }
        }.also { cachedRoms = it }

        if (roms.isEmpty()) {
            _state.update { it.copy(step = WizardStep.Error(WizardError.EMPTY_LIBRARY)) }
            return
        }

        _state.update { it.copy(step = WizardStep.Working(WorkPhase.MATCHING)) }
        val matches = Matcher.match(chapters.map { it.title }, roms)
        val rows = chapters.zip(matches) { chapter, match ->
            val auto = match.result as? MatchResult.Auto
            ReviewRow(
                chapter = chapter,
                result = match.result,
                selected = auto?.rom,
                included = auto != null && !chapter.skipped,
            )
        }
        _state.update {
            it.copy(step = WizardStep.Review, collectionName = defaultName, rows = rows)
        }
    }

    /** All scanned ROMs, for the search-everything picker on the review screen. */
    fun allRoms(): List<RomFile> = cachedRoms.orEmpty()

    // ---- Review ----

    fun setCollectionName(name: String) {
        _state.update { it.copy(collectionName = name) }
    }

    fun setRowIncluded(index: Int, included: Boolean) {
        updateRow(index) { it.copy(included = included && it.selected != null) }
    }

    fun pickRomForRow(index: Int, rom: RomFile) {
        updateRow(index) { it.copy(selected = rom, included = true) }
    }

    private fun updateRow(index: Int, transform: (ReviewRow) -> ReviewRow) {
        _state.update { state ->
            state.copy(
                rows = state.rows.mapIndexed { i, row -> if (i == index) transform(row) else row },
            )
        }
    }

    // ---- Write ----

    fun writeCollection(overwrite: Boolean = false) {
        val current = _state.value
        val esDeUri = dirPrefs.esDeTreeUri ?: run {
            _state.update { it.copy(step = WizardStep.Setup) }
            return
        }
        val name = CollectionName.fromVideoTitle(current.collectionName)
        val selectedRoms = current.rows.filter { it.included }.mapNotNull { it.selected }
        if (selectedRoms.isEmpty()) return
        val missing = current.rows
            .filter { !it.chapter.skipped && it.selected == null }
            .map { ArchiveSearch.forChapterTitle(it.chapter.title) }

        viewModelScope.launch {
            _state.update { it.copy(showOverwritePrompt = false) }
            val absoluteRoot =
                if (dirPrefs.writeAbsolutePaths) dirPrefs.romsTreeUri?.let(TreePathGuesser::guessPath) else null
            val contents = CollectionWriter.render(selectedRoms, absoluteRomsRoot = absoluteRoot)
            when (val result = fileWriter.write(esDeUri, name, contents, overwrite)) {
                is CollectionFileWriter.WriteResult.AlreadyExists ->
                    _state.update { it.copy(showOverwritePrompt = true) }
                is CollectionFileWriter.WriteResult.Error ->
                    _state.update {
                        it.copy(step = WizardStep.Error(WizardError.WRITE_FAILED, result.message))
                    }
                is CollectionFileWriter.WriteResult.Written ->
                    _state.update {
                        it.copy(
                            step = WizardStep.Done(result.fileName, name, selectedRoms.size, missing),
                            showOverwritePrompt = false,
                        )
                    }
            }
        }
    }

    fun dismissOverwritePrompt() {
        _state.update { it.copy(showOverwritePrompt = false) }
    }

    // ---- Navigation ----

    fun backToInput() {
        _state.update {
            it.copy(
                step = WizardStep.Input,
                rows = emptyList(),
                collectionName = "",
                showOverwritePrompt = false,
            )
        }
    }
}
