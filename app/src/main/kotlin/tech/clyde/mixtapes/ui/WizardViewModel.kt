package tech.clyde.mixtapes.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.clyde.mixtapes.article.ArticleClient
import tech.clyde.mixtapes.core.article.ArticleHtmlExtractor
import tech.clyde.mixtapes.core.chapters.ChapterFilter
import tech.clyde.mixtapes.core.chapters.ChapterParser
import tech.clyde.mixtapes.core.collection.CollectionCfgParser
import tech.clyde.mixtapes.core.collection.CollectionName
import tech.clyde.mixtapes.core.collection.CollectionWriter
import tech.clyde.mixtapes.core.collection.MixtapeInfo
import tech.clyde.mixtapes.core.collection.SourceType
import tech.clyde.mixtapes.core.llm.SourceKind
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
import tech.clyde.mixtapes.saf.MixtapesIndexStore
import tech.clyde.mixtapes.saf.RomScanner
import tech.clyde.mixtapes.saf.TreePathGuesser
import tech.clyde.mixtapes.youtube.TranscriptClient
import tech.clyde.mixtapes.youtube.YouTubeClient

enum class WorkPhase { FETCHING, TRANSCRIBING, EXTRACTING, SCANNING, MATCHING }

enum class WizardError {
    INVALID_URL, NETWORK, EXTRACTION, NO_CHAPTERS, EMPTY_LIBRARY, WRITE_FAILED,
    READ_FAILED, NO_TRANSCRIPT, NO_API_KEY, LLM_ERROR, ARTICLE_HTTP,
    ARTICLE_UNSUPPORTED, ARTICLE_TOO_LARGE, ARTICLE_UNREADABLE,
}

sealed interface WizardStep {
    data object Setup : WizardStep
    data object Home : WizardStep
    data object Edit : WizardStep
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

/** An existing collection opened for editing. */
data class EditorState(
    val originalFileName: String,
    /** Editable display name; sanitized on save. */
    val name: String,
    val entries: List<CollectionCfgParser.Entry>,
    /** ROM scan in flight for the add-game picker. */
    val scanning: Boolean = false,
    val showOverwritePrompt: Boolean = false,
) {
    val gameCount: Int get() = entries.size
}

/** One row of the home screen's library list. */
data class HomeCollection(
    val fileName: String,
    val displayName: String,
    val gameCount: Int,
    val sourceUrl: String? = null,
    val sourceTitle: String? = null,
    val sourceType: SourceType? = null,
)

data class ReviewRow(
    val chapter: Chapter,
    val result: MatchResult,
    val selected: RomFile?,
    val included: Boolean,
    /** The user chose this ROM by hand; a system-filter rematch must not undo it. */
    val userPicked: Boolean = false,
) {
    val confident: Boolean get() = result is MatchResult.Auto
}

/** Which system the mixtape is scoped to. Manual choices beat LLM detection. */
sealed interface SystemChoice {
    /** Default: apply the LLM-detected system when there is one. */
    data object Auto : SystemChoice

    /** Explicit "no filter" — overrides a detected system. */
    data object All : SystemChoice

    /** A specific ROM system directory, verbatim (e.g. "snes"). */
    data class Specific(val system: String) : SystemChoice
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
    val systemChoice: SystemChoice = SystemChoice.Auto,
    /** Canonical SystemHint id the LLM detected for the current source, if any. */
    val detectedSystem: String? = null,
    /** Root-level system directory names of the ROM library, for the pickers. */
    val availableSystems: List<String> = emptyList(),
    val homeCollections: List<HomeCollection> = emptyList(),
    val homeLoading: Boolean = false,
    /** Home-screen row (fileName) awaiting delete confirmation. */
    val pendingDelete: String? = null,
    val editor: EditorState? = null,
) {
    val includedCount: Int get() = rows.count { it.included && it.selected != null }

    /** The system matching is scoped to right now, or null for the whole library. */
    val activeSystemFilter: String?
        get() = when (val choice = systemChoice) {
            SystemChoice.Auto -> detectedSystem
            SystemChoice.All -> null
            is SystemChoice.Specific -> choice.system
        }
}

class WizardViewModel(application: Application) : AndroidViewModel(application) {

    private val dirPrefs = DirPrefs(application)
    private val scanner = RomScanner(application.contentResolver)
    private val fileWriter = CollectionFileWriter(application.contentResolver)
    private val indexStore = MixtapesIndexStore(fileWriter)
    private val youTube = YouTubeClient()
    private val articleClient = ArticleClient()
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
    private var lastYouTubeFetch: Pair<String, VideoMetadata>? = null

    /** Provenance for the collection currently being built. Pasted text has none. */
    private var currentSource: MixtapeInfo? = null

    init {
        refreshSetup()
        refreshAvailableSystems()
    }

    // ---- Setup ----

    fun refreshSetup() {
        val ready = dirPrefs.isReady()
        _state.update {
            it.copy(
                step = if (ready) WizardStep.Home else WizardStep.Setup,
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
        if (ready) loadHome()
    }

    /** The first shared HTTPS URL is accepted; dispatch happens after submission. */
    fun onSharedText(text: String) {
        val url = Regex("""https://\S+""").findAll(text)
            .map { it.value }
            .map { it.trimEnd('.', ',', ';', ')', ']', '}') }
            .firstOrNull()
            ?: return
        _state.update { state ->
            when (state.step) {
                WizardStep.Home, WizardStep.Input, is WizardStep.Done, is WizardStep.Error ->
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

    /** Input-screen system selection; takes effect when the next pipeline matches. */
    fun setSystemChoice(choice: SystemChoice) {
        _state.update { it.copy(systemChoice = choice) }
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
        refreshAvailableSystems()
    }

    /**
     * Populates the system dropdown. The cached scan is authoritative (only
     * dirs that actually held ROMs); before any scan, a single root child
     * query lists the directories. Failures (revoked permission) leave the
     * list empty — the pickers degrade to Auto/All.
     */
    private fun refreshAvailableSystems() {
        val cached = cachedRoms
        if (cached != null) {
            _state.update { it.copy(availableSystems = cached.map { rom -> rom.system }.distinct().sorted()) }
            return
        }
        val romsUri = dirPrefs.romsTreeUri ?: return
        viewModelScope.launch {
            val systems = runCatching { scanner.listSystems(romsUri) }.getOrDefault(emptyList())
            _state.update { it.copy(availableSystems = systems) }
        }
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
        if (!dirPrefs.isReady()) return
        _state.update { it.copy(step = WizardStep.Home) }
        loadHome()
    }

    // ---- Home ----

    /** Rebuilds the library list from the collections dir + metadata index. */
    fun loadHome() {
        val esDeUri = dirPrefs.esDeTreeUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(homeLoading = true) }
            val files = runCatching { fileWriter.listCollections(esDeUri) }.getOrDefault(emptyList())
            val index = runCatching { indexStore.load(esDeUri) }.getOrDefault(emptyMap())
            val absoluteRoot = dirPrefs.romsTreeUri?.let(TreePathGuesser::guessPath)
            val items = files.mapNotNull { file ->
                val displayName = CollectionName.fromFileName(file.fileName) ?: return@mapNotNull null
                val contents = fileWriter.readFile(esDeUri, file.fileName) ?: ""
                val info = index[file.fileName]
                HomeCollection(
                    fileName = file.fileName,
                    displayName = displayName,
                    // Opaque lines count too — ES-DE sees them as entries.
                    gameCount = CollectionCfgParser.parse(contents, absoluteRoot).size,
                    sourceUrl = info?.sourceUrl,
                    sourceTitle = info?.sourceTitle,
                    sourceType = info?.sourceType,
                )
            }.sortedBy { it.displayName.lowercase() }
            _state.update { it.copy(homeCollections = items, homeLoading = false) }
        }
    }

    /** Home's "New mixtape" button. */
    fun startCreate() {
        clearSourceState()
        _state.update { it.copy(step = WizardStep.Input) }
    }

    fun requestDelete(fileName: String) {
        _state.update { it.copy(pendingDelete = fileName) }
    }

    fun dismissDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val fileName = _state.value.pendingDelete ?: return
        val esDeUri = dirPrefs.esDeTreeUri ?: return
        viewModelScope.launch {
            fileWriter.deleteFile(esDeUri, fileName)
            indexStore.remove(esDeUri, fileName)
            _state.update { it.copy(pendingDelete = null) }
            loadHome()
        }
    }

    // ---- Edit ----

    fun openEditor(fileName: String) {
        val esDeUri = dirPrefs.esDeTreeUri ?: return
        viewModelScope.launch {
            val contents = fileWriter.readFile(esDeUri, fileName)
            if (contents == null) {
                _state.update { it.copy(step = WizardStep.Error(WizardError.READ_FAILED, fileName)) }
                return@launch
            }
            val absoluteRoot = dirPrefs.romsTreeUri?.let(TreePathGuesser::guessPath)
            _state.update {
                it.copy(
                    step = WizardStep.Edit,
                    editor = EditorState(
                        originalFileName = fileName,
                        name = CollectionName.fromFileName(fileName) ?: fileName,
                        entries = CollectionCfgParser.parse(contents, absoluteRoot),
                    ),
                )
            }
        }
    }

    fun setEditName(name: String) = updateEditor { it.copy(name = name) }

    fun removeEntry(index: Int) = updateEditor {
        it.copy(entries = it.entries.filterIndexed { i, _ -> i != index })
    }

    fun addGame(rom: RomFile) = updateEditor {
        // rawLine only matters for opaque round-trips; games re-render from the ROM.
        it.copy(entries = it.entries + CollectionCfgParser.Entry.Game(rom, rawLine = ""))
    }

    /**
     * The editor opens instantly from the cfg alone; the library scan runs
     * only when the add-game picker first needs it, then stays cached for the
     * session like the wizard's.
     */
    fun ensureRomsForPicker() {
        if (cachedRoms != null || _state.value.editor?.scanning == true) return
        val romsUri = dirPrefs.romsTreeUri ?: return
        viewModelScope.launch {
            updateEditor { it.copy(scanning = true) }
            runCatching { scanner.scan(romsUri) }.getOrNull()?.let { cachedRoms = it }
            updateEditor { it.copy(scanning = false) }
        }
    }

    fun saveEditor(overwrite: Boolean = false) {
        val editor = _state.value.editor ?: return
        val esDeUri = dirPrefs.esDeTreeUri ?: return
        if (editor.entries.isEmpty()) return
        val name = CollectionName.fromVideoTitle(editor.name)
        val newFileName = fileWriter.fileName(name)
        val renamed = newFileName != editor.originalFileName

        viewModelScope.launch {
            updateEditor { it.copy(showOverwritePrompt = false) }
            if (renamed && !overwrite &&
                withContext(Dispatchers.IO) { fileWriter.exists(esDeUri, name) }
            ) {
                updateEditor { it.copy(showOverwritePrompt = true) }
                return@launch
            }
            val absoluteRoot =
                if (dirPrefs.writeAbsolutePaths) dirPrefs.romsTreeUri?.let(TreePathGuesser::guessPath) else null
            val contents = CollectionWriter.renderEntries(editor.entries, absoluteRoot)
            when (val result = fileWriter.write(esDeUri, name, contents, overwrite = true)) {
                is CollectionFileWriter.WriteResult.Error ->
                    _state.update {
                        it.copy(step = WizardStep.Error(WizardError.WRITE_FAILED, result.message))
                    }
                // Unreachable with overwrite = true, but the type demands it.
                CollectionFileWriter.WriteResult.AlreadyExists -> Unit
                is CollectionFileWriter.WriteResult.Written -> {
                    if (renamed) {
                        fileWriter.deleteFile(esDeUri, editor.originalFileName)
                        indexStore.move(esDeUri, editor.originalFileName, newFileName)
                    }
                    backToHome()
                }
            }
        }
    }

    fun dismissEditorOverwrite() = updateEditor { it.copy(showOverwritePrompt = false) }

    private fun updateEditor(transform: (EditorState) -> EditorState) {
        _state.update { state ->
            state.editor?.let { state.copy(editor = transform(it)) } ?: state
        }
    }

    // ---- Input / pipeline ----

    fun makeMixtapeFromUrl(url: String) {
        clearSourceState()
        viewModelScope.launch {
            _state.update { it.copy(step = WizardStep.Working(WorkPhase.FETCHING), detectedSystem = null) }
            val trimmed = url.trim()
            if (!YouTubeClient.isYouTubeUrl(trimmed)) {
                if (trimmed.startsWith("https://", ignoreCase = true) && !dirPrefs.llmConfigured()) {
                    _state.update { it.copy(step = WizardStep.Error(WizardError.NO_API_KEY)) }
                    return@launch
                }
                runArticlePipeline(trimmed)
                return@launch
            }
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
                    lastYouTubeFetch = videoId?.let { it to fetched.metadata }
                    currentSource = videoId?.let {
                        MixtapeInfo(
                            sourceUrl = "https://www.youtube.com/watch?v=$it",
                            sourceTitle = fetched.metadata.title,
                            sourceType = SourceType.YOUTUBE,
                        )
                    }
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
        clearSourceState()
        _state.update { it.copy(detectedSystem = null) }
        viewModelScope.launch {
            val chapters = ChapterFilter.markSkipped(ChapterParser.parse(text))
            if (chapters.isNotEmpty()) {
                scanMatchReview(chapters, "mixtape")
            } else {
                runAiPipeline(
                    sourceTitle = "Pasted game list",
                    sourceKind = SourceKind.PASTED_TEXT,
                    content = text,
                    defaultName = "mixtape",
                )
            }
        }
    }

    /** From the NO_CHAPTERS error screen: rerun the last fetched video through the transcript path. */
    fun retryWithTranscript() {
        val (videoId, metadata) = lastYouTubeFetch ?: run {
            backToInput()
            return
        }
        _state.update { it.copy(detectedSystem = null) }
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
                        canRetryWithTranscript = lastYouTubeFetch != null,
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

        runAiPipeline(
            sourceTitle = metadata.title,
            sourceKind = SourceKind.TRANSCRIPT,
            content = transcript,
            defaultName = defaultName,
        )
    }

    private suspend fun runArticlePipeline(url: String) {
        when (val fetched = articleClient.fetch(url)) {
            is ArticleClient.FetchResult.Failure -> {
                val error = when (fetched.error) {
                    ArticleClient.FetchError.INVALID_URL -> WizardError.INVALID_URL
                    ArticleClient.FetchError.NETWORK -> WizardError.NETWORK
                    ArticleClient.FetchError.HTTP -> WizardError.ARTICLE_HTTP
                    ArticleClient.FetchError.UNSUPPORTED_CONTENT -> WizardError.ARTICLE_UNSUPPORTED
                    ArticleClient.FetchError.TOO_LARGE -> WizardError.ARTICLE_TOO_LARGE
                    ArticleClient.FetchError.UNREADABLE -> WizardError.ARTICLE_UNREADABLE
                }
                _state.update { it.copy(step = WizardStep.Error(error, fetched.detail)) }
                return
            }
            is ArticleClient.FetchResult.Success -> {
                val extracted = withContext(Dispatchers.Default) {
                    ArticleHtmlExtractor.extract(fetched.page.html, fetched.page.title)
                }
                val article = when (extracted) {
                    ArticleHtmlExtractor.Result.InsufficientContent -> {
                        _state.update { it.copy(step = WizardStep.Error(WizardError.ARTICLE_UNREADABLE)) }
                        return
                    }
                    is ArticleHtmlExtractor.Result.Success -> extracted.article
                }
                val title = article.title.ifBlank { fetched.page.title }.ifBlank { "mixtape" }
                currentSource = MixtapeInfo(
                    sourceUrl = fetched.page.finalUrl,
                    sourceTitle = title.takeIf { it != "mixtape" },
                    sourceType = SourceType.ARTICLE,
                )
                runAiPipeline(
                    sourceTitle = title,
                    sourceKind = SourceKind.ARTICLE,
                    content = article.content,
                    defaultName = CollectionName.fromVideoTitle(title),
                )
            }
        }
    }

    private suspend fun runAiPipeline(
        sourceTitle: String,
        sourceKind: SourceKind,
        content: String,
        defaultName: String,
    ) {
        if (!dirPrefs.llmConfigured()) {
            _state.update { it.copy(step = WizardStep.Error(WizardError.NO_API_KEY)) }
            return
        }
        _state.update { it.copy(step = WizardStep.Working(WorkPhase.EXTRACTING)) }
        val config = LlmClient.Config(
            baseUrl = dirPrefs.llmBaseUrl,
            apiKey = dirPrefs.llmApiKey ?: return,
            model = dirPrefs.llmModel,
        )
        val extraction = when (
            val result = llmClient.extractGameTitles(sourceTitle, sourceKind, content, config)
        ) {
            is LlmClient.Result.Failure -> {
                val detail = when (result.error) {
                    LlmClient.Error.CONFIG ->
                        "The AI settings look invalid — check the base URL and API key in Settings."
                    LlmClient.Error.NETWORK -> "Couldn't reach the AI endpoint."
                    LlmClient.Error.HTTP -> result.detail
                    LlmClient.Error.PARSE -> "The model's response couldn't be read."
                    LlmClient.Error.EMPTY -> "The model found no games in the source."
                }
                _state.update { it.copy(step = WizardStep.Error(WizardError.LLM_ERROR, detail)) }
                return
            }
            is LlmClient.Result.Success -> result
        }

        // Auto-applied via activeSystemFilter while systemChoice is Auto; a manual
        // choice on the Input screen simply never reads it.
        _state.update { it.copy(detectedSystem = extraction.detectedSystem) }

        // ChapterFilter as a safety net: the model may still emit "Intro"/"Honorable
        // Mentions"-style entries despite the prompt. Timestamps don't exist here and
        // nothing downstream reads Chapter.seconds.
        val chapters = ChapterFilter.markSkipped(extraction.titles.map { Chapter(title = it, seconds = 0) })
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

        _state.update {
            it.copy(
                step = WizardStep.Working(WorkPhase.MATCHING),
                availableSystems = roms.map { rom -> rom.system }.distinct().sorted(),
            )
        }
        val matches = Matcher.match(chapters.map { it.title }, roms, state.value.activeSystemFilter)
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
        updateRow(index) { it.copy(selected = rom, included = true, userPicked = true) }
    }

    /** Review-screen chip: change the system scope and rematch in place. */
    fun applySystemFilter(choice: SystemChoice) {
        _state.update { it.copy(systemChoice = choice) }
        rematchPreservingPicks()
    }

    /**
     * Re-runs matching against the cached library under the current filter.
     * Hand-picked rows keep their pick (even one outside the new filter — the
     * pick is the escape hatch); other rows are rebuilt, preserving a manual
     * uncheck when the auto pick didn't change.
     */
    private fun rematchPreservingPicks() {
        val roms = cachedRoms ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val snapshot = _state.value
            val matches = Matcher.match(
                snapshot.rows.map { it.chapter.title },
                roms,
                snapshot.activeSystemFilter,
            )
            _state.update { state ->
                if (state.rows.size != matches.size) return@update state
                state.copy(
                    rows = state.rows.zip(matches) { old, match ->
                        val auto = match.result as? MatchResult.Auto
                        when {
                            old.userPicked -> old.copy(result = match.result)
                            auto?.rom == old.selected -> old.copy(result = match.result)
                            else -> ReviewRow(
                                chapter = old.chapter,
                                result = match.result,
                                selected = auto?.rom,
                                included = auto != null && !old.chapter.skipped,
                            )
                        }
                    },
                )
            }
        }
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

        val source = currentSource
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
                is CollectionFileWriter.WriteResult.Written -> {
                    // Best-effort: the pasted-text path has no URL, and an
                    // index failure must never fail the collection itself.
                    source?.let { provenance ->
                        indexStore.put(
                            esDeUri,
                            result.fileName,
                            MixtapeInfo(
                                sourceUrl = provenance.sourceUrl,
                                sourceTitle = provenance.sourceTitle,
                                sourceType = provenance.sourceType,
                                createdAt = java.time.Instant.now().toString(),
                            ),
                        )
                    }
                    _state.update {
                        it.copy(
                            step = WizardStep.Done(result.fileName, name, selectedRoms.size, missing),
                            showOverwritePrompt = false,
                        )
                    }
                }
            }
        }
    }

    fun dismissOverwritePrompt() {
        _state.update { it.copy(showOverwritePrompt = false) }
    }

    // ---- Navigation ----

    fun backToInput() {
        clearSourceState()
        _state.update {
            it.copy(
                step = WizardStep.Input,
                rows = emptyList(),
                collectionName = "",
                showOverwritePrompt = false,
                // A system scope is per-video; don't leak it into the next one.
                systemChoice = SystemChoice.Auto,
                detectedSystem = null,
            )
        }
    }

    fun backToHome() {
        clearSourceState()
        _state.update {
            it.copy(
                step = WizardStep.Home,
                rows = emptyList(),
                collectionName = "",
                showOverwritePrompt = false,
                systemChoice = SystemChoice.Auto,
                detectedSystem = null,
                pendingDelete = null,
                editor = null,
            )
        }
        loadHome()
    }

    private fun clearSourceState() {
        lastYouTubeFetch = null
        currentSource = null
    }
}
