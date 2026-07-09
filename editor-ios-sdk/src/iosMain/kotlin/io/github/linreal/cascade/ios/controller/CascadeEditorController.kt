@file:OptIn(ExperimentalObjCName::class, ExperimentalCascadeHtmlApi::class)

package io.github.linreal.cascade.ios.controller

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.linreal.cascade.editor.action.ClearFocus
import io.github.linreal.cascade.editor.action.ClearSelection
import io.github.linreal.cascade.editor.action.DeleteSelectedOrFocused
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.ExperimentalCascadeHtmlApi
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.loadFromHtml
import io.github.linreal.cascade.editor.htmlserialization.toHtml
import io.github.linreal.cascade.editor.registry.BlockDescriptor
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.serialization.DocumentDecodeWarning
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.serialization.resolveDocumentBlocks
import io.github.linreal.cascade.editor.serialization.toJson
import io.github.linreal.cascade.editor.slash.BuiltInBlockSlashBehavior
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandSpec
import io.github.linreal.cascade.editor.slash.SlashCommandAction
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandRegistry
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.slash.SlashQueryTextPolicy
import io.github.linreal.cascade.editor.slash.builtInBlockSlashCommandId
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.ui.createEditorRegistry
import io.github.linreal.cascade.ios.block.CascadeCustomBlockRegistration
import io.github.linreal.cascade.ios.block.CascadeCustomBlockSlashBehavior
import io.github.linreal.cascade.ios.block.NativeCustomBlockCodec
import io.github.linreal.cascade.ios.block.NativeCustomBlockRenderer
import io.github.linreal.cascade.ios.block.NativeCustomBlockType
import io.github.linreal.cascade.ios.block.buildInsertableBlock
import io.github.linreal.cascade.ios.localization.CascadeEditorLocalization
import io.github.linreal.cascade.ios.localization.toEditorBlockStrings
import io.github.linreal.cascade.ios.localization.toEditorStrings
import io.github.linreal.cascade.ios.slash.CascadeSlashCommand
import io.github.linreal.cascade.ios.slash.CascadeSlashCommandContext
import io.github.linreal.cascade.ios.slash.CascadeSlashCommandResult
import io.github.linreal.cascade.ios.model.CascadeRichTextBlock
import io.github.linreal.cascade.ios.model.CascadeRichTextSnapshot
import io.github.linreal.cascade.ios.model.CascadeRichTextSpan
import io.github.linreal.cascade.ios.model.CascadeSpanKind
import io.github.linreal.cascade.ios.model.parseJsonObjectPayloadSafely
import io.github.linreal.cascade.ios.toolbar.CascadeToolbarState
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync

private const val MAIN_THREAD_ERROR: String = "CascadeEditorController must be used on the main thread"

internal class CascadeToolbarActions internal constructor(
    internal val toggleBold: () -> Unit,
    internal val toggleItalic: () -> Unit,
    internal val toggleUnderline: () -> Unit,
    internal val toggleStrikeThrough: () -> Unit,
    internal val toggleInlineCode: () -> Unit,
    internal val toggleHighlight: (Long) -> Unit,
    internal val indentForward: () -> Unit,
    internal val indentBackward: () -> Unit,
    internal val applyLink: (String, String?) -> Unit,
    internal val removeLink: () -> Unit,
)

@ObjCName("CascadeEditorController", exact = true)
public class CascadeEditorController public constructor(
    initialJson: String?,
    configuration: CascadeEditorConfiguration,
) {
    public constructor() : this(initialJson = null, configuration = CascadeEditorConfiguration())

    public constructor(initialJson: String?) : this(
        initialJson = initialJson,
        configuration = CascadeEditorConfiguration(),
    )

    internal val stateHolder: EditorStateHolder = EditorStateHolder()
    internal val textStates: BlockTextStates = BlockTextStates()
    internal val spanStates: BlockSpanStates = BlockSpanStates()

    // Block/slash registries owned for the controller's lifetime so registrations
    // made before or after the first mount are visible to the hosted editor.
    // These are the exact instances passed into CascadeEditor by makeViewController.
    // Kept internal so raw :editor registry types never surface in Swift signatures.
    internal val registry: BlockRegistry = createEditorRegistry()
    internal val slashRegistry: SlashCommandRegistry = SlashCommandRegistry()

    // Type ids owned by built-in block types. A native custom-block registration is
    // rejected when it collides with one of these: the document decoder resolves
    // these ids to built-in types before the native codec runs, so allowing an
    // overwrite of the built-in descriptor/renderer would desynchronize decode from
    // render. Captured from the seeded registry (so it tracks whatever built-ins
    // createEditorRegistry() provides) plus the `heading_` family the decoder treats
    // as built-in regardless of level.
    private val reservedBuiltInTypeIds: Set<String> =
        registry.getAllDescriptors().mapTo(mutableSetOf()) { descriptor -> descriptor.typeId }

    // Native custom-block registrations keyed by type id. The codec reads this map
    // live so registrations made before or after the JSON load path is invoked are
    // both visible; the JSON load/export paths use it to decode/encode registered
    // ids as renderable NativeCustomBlockType rather than UnknownBlockType.
    internal val nativeRegistrations: MutableMap<String, CascadeCustomBlockRegistration> = mutableMapOf()
    internal val nativeBlockCodec: NativeCustomBlockCodec =
        NativeCustomBlockCodec { typeId -> typeId in nativeRegistrations }

    internal val configurationSnapshot: MutableState<CascadeEditorConfiguration> = mutableStateOf(configuration)

    // Localization resolved onto :editor's string types at setLocalization time
    // (snapshot semantics) and observed by the hosted editor as state, so runtime
    // updates recompose the mounted tree the same way configuration changes do.
    internal val resolvedStrings: MutableState<CascadeEditorStrings> =
        mutableStateOf(CascadeEditorStrings.default())
    internal val resolvedBlockStrings: MutableState<CascadeEditorBlockStrings> =
        mutableStateOf(CascadeEditorBlockStrings.default())
    // Identity of the Compose host that currently owns this controller's bridge
    // state. makeViewController() can be called more than once, and UIKit/SwiftUI
    // transitions overlap the old and new hosts; only the owning host may publish
    // toolbar actions/state or clear the mounted flags on dispose, so a stale
    // host tearing down cannot disable the editor the user is looking at.
    internal var mountOwner: Any? = null
    internal var mounted: Boolean = false
    internal var toolbarActions: CascadeToolbarActions? = null
    private var currentConfiguration: CascadeEditorConfiguration = configuration
    private var currentToolbarState: CascadeToolbarState = CascadeToolbarState.Empty

    public val configuration: CascadeEditorConfiguration
        get() = onMainThread(
            fallback = { CascadeEditorConfiguration() },
            block = { currentConfiguration },
        )

    public val selectedBlockCount: Int
        get() = onMainThread(
            fallback = { 0 },
            block = { stateHolder.state.selectedBlockIds.size },
        )

    /**
     * Whether any block is currently selected. Reads the same selection set as
     * [selectedBlockCount], so the two are always consistent.
     */
    public val hasSelection: Boolean
        get() = onMainThread(
            fallback = { false },
            block = { stateHolder.state.hasSelection },
        )

    public val toolbarState: CascadeToolbarState
        get() = onMainThread(
            fallback = { CascadeToolbarState.Empty },
            block = { currentToolbarState },
        )

    public val canUndo: Boolean
        get() = onMainThread(
            fallback = { false },
            block = { mounted && stateHolder.canUndo },
        )

    public val canRedo: Boolean
        get() = onMainThread(
            fallback = { false },
            block = { mounted && stateHolder.canRedo },
        )

    public var onDocumentChanged: (() -> Unit)? = null
    public var onStateChanged: (() -> Unit)? = null
    public var onInternalError: ((String) -> Unit)? = null
    public var onOpenLink: ((String) -> Unit)? = null
    public var onToolbarStateChanged: ((CascadeToolbarState) -> Unit)? = null

    init {
        if (initialJson != null) {
            loadJson(initialJson)
        }
    }

    /**
     * Loads a JSON document, replacing the current content and clearing history.
     *
     * When the document fails to parse ([CascadeDocumentLoadResult.success] is
     * false) the currently loaded document, runtime state, and undo history are
     * left untouched, so a corrupt payload can be rejected without losing what
     * the user is editing.
     */
    public fun loadJson(json: String): CascadeDocumentLoadResult = onMainThread(
        fallback = { failedLoadResult(MAIN_THREAD_ERROR) },
    ) {
        // The core load API is intentionally a hard replacement, including on
        // parse failure. Preflight here so the native SDK can reject malformed
        // host input without destroying the currently edited document.
        val preflight = DocumentSchema.decodeFromStringWithReport(json, typeCodec = nativeBlockCodec)
        if (preflight.warnings.any { warning -> warning is DocumentDecodeWarning.DocumentParseFailed }) {
            return@onMainThread CascadeDocumentLoadResult(
                success = false,
                warningMessages = preflight.warnings.map { warning -> warning.message() },
            )
        }
        val result = stateHolder.loadFromJson(json, textStates, spanStates, typeCodec = nativeBlockCodec)
        val success = result.warnings.none { warning ->
            warning is DocumentDecodeWarning.DocumentParseFailed
        }
        // Parse failures return during preflight, so only a successful load reaches
        // the hard-replacement path and becomes a document change.
        if (success) {
            notifyDocumentAndStateChangedFromPublicMutation()
        }
        CascadeDocumentLoadResult(
            success = success,
            warningMessages = result.warnings.map { warning -> warning.message() },
        )
    }

    public fun loadHtml(html: String): CascadeHtmlLoadResult = onMainThread(
        fallback = { failedLoadResult(MAIN_THREAD_ERROR) },
    ) {
        val result = stateHolder.loadFromHtml(html, textStates, spanStates, HtmlProfile.Default)
        notifyDocumentAndStateChangedFromPublicMutation()
        CascadeDocumentLoadResult(
            success = result.warnings.isSuccessfulHtmlLoad(),
            warningMessages = result.warnings.map { warning -> warning.message() },
        )
    }

    /**
     * Exports the current document as canonical JSON.
     *
     * Safe to call from any thread: off-main calls hop synchronously onto the
     * main queue so the export always reflects the real document instead of an
     * empty fallback a caller could persist by mistake. Do not block the main
     * thread waiting for an off-main export — the synchronous hop would deadlock.
     */
    public fun exportJson(): String = onMainThreadSync {
        stateHolder.toJson(textStates, spanStates, typeCodec = nativeBlockCodec)
    }

    /**
     * Exports the current document as HTML. Same threading contract as
     * [exportJson]: callable from any thread via a synchronous main-queue hop.
     */
    public fun exportHtml(): String = onMainThreadSync {
        stateHolder.toHtml(textStates, spanStates, HtmlProfile.Default)
    }

    /**
     * Document-ordered plain text: the text of every text-bearing block joined
     * with newlines. Non-text blocks (dividers, custom blocks) contribute no
     * lines — use [exportRichText] for the full typed per-block runs. Same
     * threading contract as [exportJson].
     */
    public fun exportPlainText(): String = onMainThreadSync {
        currentDocumentBlocks()
            .mapNotNull { block -> (block.content as? BlockContent.Text)?.text }
            .joinToString(separator = "\n")
    }

    /**
     * Exports per-block rich-text runs (text plus span styling) in document
     * order, including non-text blocks as empty typed runs. Same threading
     * contract as [exportJson].
     */
    public fun exportRichText(): CascadeRichTextSnapshot = onMainThreadSync {
        CascadeRichTextSnapshot(
            blocks = stateHolder.state.blocks.map { block ->
                val text = textStates.getVisibleText(block.id)
                    ?: (block.content as? BlockContent.Text)?.text
                    ?: ""
                val spans = if (block.type.supportsSpans) {
                    resolveCurrentSpans(block.id, block.content, text.length)
                        .mapNotNull { span -> span.toCascadeRichTextSpan() }
                } else {
                    emptyList()
                }

                CascadeRichTextBlock(
                    blockId = block.id.value,
                    typeId = block.type.typeId,
                    text = text,
                    spans = spans,
                )
            },
        )
    }

    public fun reset(toJson: String): CascadeDocumentLoadResult = loadJson(toJson)

    public fun clearFocus(): Unit = onMainThread(
        fallback = {},
    ) {
        dispatchStateAction(ClearFocus)
    }

    public fun clearSelection(): Unit = onMainThread(
        fallback = {},
    ) {
        dispatchStateAction(ClearSelection)
    }

    public fun deleteSelectedOrFocused(): Unit = onMainThread(
        fallback = {},
    ) {
        if (currentConfiguration.readOnly) return@onMainThread

        val before = stateHolder.state
        if (mounted) {
            stateHolder.dispatchStructuralAction(DeleteSelectedOrFocused, textStates, spanStates)
        } else {
            stateHolder.dispatch(DeleteSelectedOrFocused)
        }
        cleanupRuntimeForCurrentBlocks()
        if (stateHolder.state != before) {
            notifyDocumentAndStateChangedFromPublicMutation()
        }
    }

    public fun undo(): Unit = performMountedEditorCommand("undo") {
        stateHolder.undo()
    }

    public fun redo(): Unit = performMountedEditorCommand("redo") {
        stateHolder.redo()
    }

    public fun toggleBold(): Unit = performToolbarAction("toggleBold") { actions ->
        actions.toggleBold()
    }

    public fun toggleItalic(): Unit = performToolbarAction("toggleItalic") { actions ->
        actions.toggleItalic()
    }

    public fun toggleUnderline(): Unit = performToolbarAction("toggleUnderline") { actions ->
        actions.toggleUnderline()
    }

    public fun toggleStrikeThrough(): Unit = performToolbarAction("toggleStrikeThrough") { actions ->
        actions.toggleStrikeThrough()
    }

    public fun toggleInlineCode(): Unit = performToolbarAction("toggleInlineCode") { actions ->
        actions.toggleInlineCode()
    }

    public fun toggleHighlight(argb: Long): Unit = performToolbarAction("toggleHighlight") { actions ->
        actions.toggleHighlight(argb)
    }

    public fun indentForward(): Unit = performToolbarAction("indentForward") { actions ->
        actions.indentForward()
    }

    public fun indentBackward(): Unit = performToolbarAction("indentBackward") { actions ->
        actions.indentBackward()
    }

    public fun applyLink(
        url: String,
        title: String?,
    ): Unit = performToolbarAction("applyLink") { actions ->
        actions.applyLink(url, title)
    }

    public fun removeLink(): Unit = performToolbarAction("removeLink") { actions ->
        actions.removeLink()
    }

    public fun updateConfiguration(value: CascadeEditorConfiguration): Unit = onMainThread(
        fallback = {},
    ) {
        updateConfigurationOnMain(value)
    }

    public fun setReadOnly(value: Boolean): Unit = updateConfiguration {
        copy(readOnly = value)
    }

    public fun setDarkMode(value: Boolean): Unit = updateConfiguration {
        copy(isDark = value)
    }

    public fun setToolbarMode(value: CascadeToolbarMode): Unit = updateConfiguration {
        copy(toolbarMode = value)
    }

    public fun setSlashCommandsEnabled(value: Boolean): Unit = updateConfiguration {
        copy(slashCommandsEnabled = value)
    }

    /**
     * Applies localized strings to the editor's built-in UI chrome and to block
     * names/descriptions/keywords in the slash menu. Fields left unset keep the
     * built-in English defaults.
     *
     * Values are resolved immediately (mutating the passed bags afterwards has no
     * effect until this is called again) and take effect whether called before or
     * after the editor is mounted.
     */
    public fun setLocalization(localization: CascadeEditorLocalization): Unit = onMainThread(
        fallback = {},
    ) {
        resolvedStrings.value = localization.toEditorStrings()
        resolvedBlockStrings.value = localization.toEditorBlockStrings()
    }

    /**
     * Registers a native custom block type. Adds its slash-menu descriptor and a
     * renderer that hosts the native view into the owned registry, and makes its
     * type id decode/encode as a renderable block on the JSON paths.
     *
     * Re-registering an id replaces the previous registration (last-registration
     * wins) and reports a non-fatal warning through [onInternalError]. Takes effect
     * whether called before or after the editor is mounted.
     */
    public fun registerBlock(registration: CascadeCustomBlockRegistration): Unit = onMainThread(
        fallback = {},
    ) {
        val typeId = registration.typeId
        if (isReservedBuiltInTypeId(typeId)) {
            reportInternalError(
                "Custom block type '$typeId' is reserved by a built-in block type and was not registered."
            )
            return@onMainThread
        }
        if (typeId in nativeRegistrations) {
            reportInternalError(
                "Custom block type '$typeId' is already registered; overriding the previous registration."
            )
        }
        val defaultPayload = parseJsonObjectPayloadSafely(registration.defaultPayloadJson)
        if (defaultPayload.errorMessage != null) {
            reportInternalError(
                "Custom block '$typeId' default payload ignored: ${defaultPayload.errorMessage}"
            )
        }
        val descriptor = BlockDescriptor(
            typeId = typeId,
            displayName = registration.displayName,
            description = registration.description,
            keywords = registration.keywords,
            slash = BuiltInSlashCommandSpec(behavior = registration.slashBehavior.toBuiltInBehavior()),
            factory = { id ->
                Block(id, NativeCustomBlockType(typeId), BlockContent.Custom(typeId, defaultPayload.data))
            },
        )
        registry.register(descriptor, NativeCustomBlockRenderer(registration, this))
        nativeRegistrations[typeId] = registration
    }

    /**
     * Registers a native slash command into the owned slash registry. Built-in
     * commands stay available alongside it, and registration takes effect whether
     * called before or after the editor is mounted.
     *
     * Registering an id that is already occupied — by a prior native command or a
     * built-in one — overrides the previous entry (last registration wins) and
     * reports a non-fatal warning through [onInternalError]; the registration is
     * still kept. The command's synchronous handler is wrapped so it runs inside the
     * editor's suspending slash-execution contract.
     */
    public fun registerSlashCommand(command: CascadeSlashCommand): Unit = onMainThread(
        fallback = {},
    ) {
        if (isSlashCommandIdRegistered(command.id)) {
            reportInternalError(
                "Slash command '${command.id}' overrides an existing registration."
            )
        }
        slashRegistry.register(
            SlashCommandAction(
                id = SlashCommandId(command.id),
                title = command.title,
                description = command.description,
                keywords = command.keywords,
                // Native handlers own their query text (they edit it via the context),
                // so the token is left in place rather than removed before execution.
                queryTextPolicy = SlashQueryTextPolicy.KeepText,
                onExecute = {
                    val context = CascadeSlashCommandContext(
                        editor = editor,
                        anchorBlockId = anchorBlockId,
                        buildBlock = { typeId, payloadJson ->
                            buildInsertableBlock(typeId, payloadJson, registry) { id ->
                                id in nativeRegistrations
                            }
                        },
                        reportError = ::reportInternalError,
                    )
                    command.handler(context).toSlashCommandResult(command.id)
                },
            ),
        )
    }

    internal fun updateToolbarStateFromHost(value: CascadeToolbarState): Unit {
        if (currentToolbarState == value) return
        currentToolbarState = value
        invokeCallback("onToolbarStateChanged", onToolbarStateChanged, value)
    }

    internal fun notifyStateChangedFromHost(): Unit {
        invokeCallback("onStateChanged", onStateChanged)
    }

    internal fun notifyDocumentChangedFromHost(): Unit {
        invokeCallback("onDocumentChanged", onDocumentChanged)
    }

    /**
     * Resolves the authoritative document blocks (live text/span state folded in)
     * without JSON encoding. The hosted change observer compares consecutive
     * results by structural equality: a cheap content-change signal that catches
     * text/span edits which never alter [EditorStateHolder]'s [state] identity.
     */
    internal fun currentDocumentBlocks(): List<Block> =
        stateHolder.resolveDocumentBlocks(textStates, spanStates)

    /**
     * Slash command ids currently occupied by built-in block commands, derived from
     * the owned block registry. Used to detect collisions when registering native
     * commands (built-ins are merged into the mounted registry at composition time,
     * so they are not otherwise visible in [slashRegistry]).
     */
    internal fun builtInSlashCommandIds(): Set<SlashCommandId> =
        registry.getAllDescriptors()
            .filter { descriptor -> descriptor.slash != null }
            .mapTo(mutableSetOf()) { descriptor -> builtInBlockSlashCommandId(descriptor.typeId) }

    private fun isReservedBuiltInTypeId(typeId: String): Boolean =
        typeId in reservedBuiltInTypeIds || typeId.startsWith("heading_")

    private fun isSlashCommandIdRegistered(id: String): Boolean {
        val slashId = SlashCommandId(id)
        if (slashRegistry.getRootItems().any { item -> item.id == slashId }) return true
        return slashId in builtInSlashCommandIds()
    }

    private fun CascadeSlashCommandResult.toSlashCommandResult(commandId: String): SlashCommandResult =
        when (kind) {
            CascadeSlashCommandResult.Kind.Done -> SlashCommandResult.Done
            CascadeSlashCommandResult.Kind.KeepOpen -> SlashCommandResult.KeepOpen
            CascadeSlashCommandResult.Kind.Failure -> {
                failureMessage?.let { message ->
                    reportInternalError("Slash command '$commandId' failed: $message")
                }
                SlashCommandResult.Failure(failureMessage)
            }
        }

    internal inline fun <T> onMainThread(
        fallback: () -> T,
        block: () -> T,
    ): T {
        if (!NSThread.isMainThread) {
            reportInternalError(MAIN_THREAD_ERROR)
            return fallback()
        }
        return block()
    }

    /**
     * Runs a read-only [block] on the main thread and returns its result,
     * hopping synchronously onto the main queue when called from another
     * thread. Used by the export methods: they must never hand back a
     * valid-looking empty document to an off-main caller (e.g. a background
     * autosave) that would then persist it. Mutating methods keep the
     * report-and-fallback [onMainThread] guard.
     */
    internal fun <T> onMainThreadSync(block: () -> T): T {
        if (NSThread.isMainThread) return block()
        var result: T? = null
        dispatch_sync(dispatch_get_main_queue()) {
            result = block()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun performMountedEditorCommand(
        label: String,
        command: () -> Unit,
    ) {
        onMainThread(fallback = {}) {
            if (!mounted) return@onMainThread

            try {
                command()
            } catch (throwable: Throwable) {
                reportInternalError(
                    "CascadeEditorController command $label failed: " +
                        (throwable.message ?: throwable.toString())
                )
            }
        }
    }

    private fun performToolbarAction(
        label: String,
        action: (CascadeToolbarActions) -> Unit,
    ) {
        onMainThread(fallback = {}) {
            val actions = toolbarActions ?: return@onMainThread
            if (!mounted) return@onMainThread

            try {
                action(actions)
            } catch (throwable: Throwable) {
                reportInternalError(
                    "CascadeEditorController toolbar action $label failed: " +
                        (throwable.message ?: throwable.toString())
                )
            }
        }
    }

    private fun resolveCurrentSpans(
        blockId: BlockId,
        content: BlockContent,
        textLength: Int,
    ): List<TextSpan> {
        val runtime = spanStates.get(blockId)?.value
        if (runtime != null) return runtime.clampToTextLength(textLength)
        val snapshot = content as? BlockContent.Text ?: return emptyList()
        return snapshot.spans.clampToTextLength(textLength)
    }

    private fun TextSpan.toCascadeRichTextSpan(): CascadeRichTextSpan? = when (val spanStyle = style) {
        SpanStyle.Bold -> CascadeRichTextSpan(start, end, CascadeSpanKind.bold)
        SpanStyle.Italic -> CascadeRichTextSpan(start, end, CascadeSpanKind.italic)
        SpanStyle.Underline -> CascadeRichTextSpan(start, end, CascadeSpanKind.underline)
        SpanStyle.StrikeThrough -> CascadeRichTextSpan(start, end, CascadeSpanKind.strikeThrough)
        SpanStyle.InlineCode -> CascadeRichTextSpan(start, end, CascadeSpanKind.inlineCode)
        is SpanStyle.Highlight -> CascadeRichTextSpan(
            start = start,
            end = end,
            kind = CascadeSpanKind.highlight,
            argb = spanStyle.colorArgb,
        )

        is SpanStyle.Link -> CascadeRichTextSpan(
            start = start,
            end = end,
            kind = CascadeSpanKind.link,
            url = spanStyle.url,
        )

        is SpanStyle.Custom -> null
    }

    private fun dispatchStateAction(action: io.github.linreal.cascade.editor.action.EditorAction) {
        val before = stateHolder.state
        stateHolder.dispatch(action)
        if (stateHolder.state != before) {
            notifyStateChangedFromPublicMutation()
        }
    }

    private fun cleanupRuntimeForCurrentBlocks() {
        val existingBlockIds = stateHolder.state.blocks.mapTo(mutableSetOf()) { block -> block.id }
        textStates.cleanup(existingBlockIds)
        spanStates.cleanup(existingBlockIds)
    }

    private fun notifyDocumentAndStateChanged() {
        invokeCallback("onDocumentChanged", onDocumentChanged)
        invokeCallback("onStateChanged", onStateChanged)
    }

    private fun notifyDocumentAndStateChangedFromPublicMutation() {
        if (!mounted) {
            notifyDocumentAndStateChanged()
        }
    }

    private fun notifyStateChangedFromPublicMutation() {
        if (!mounted) {
            invokeCallback("onStateChanged", onStateChanged)
        }
    }

    private fun updateConfiguration(transform: CascadeEditorConfiguration.() -> CascadeEditorConfiguration) {
        onMainThread(fallback = {}) {
            updateConfigurationOnMain(currentConfiguration.transform())
        }
    }

    private fun updateConfigurationOnMain(value: CascadeEditorConfiguration) {
        if (currentConfiguration == value) return
        currentConfiguration = value
        configurationSnapshot.value = value
        invokeCallback("onStateChanged", onStateChanged)
    }

    private fun List<TextSpan>.clampToTextLength(textLength: Int): List<TextSpan> {
        return mapNotNull { span ->
            val start = span.start.coerceIn(0, textLength)
            val end = span.end.coerceIn(0, textLength)
            if (end <= start) {
                null
            } else if (start == span.start && end == span.end) {
                span
            } else {
                TextSpan(start = start, end = end, style = span.style)
            }
        }
    }

    internal fun invokeCallback(
        label: String,
        callback: (() -> Unit)?,
    ): Unit {
        if (callback == null) return
        try {
            callback()
        } catch (throwable: Throwable) {
            reportInternalError(
                "CascadeEditorController callback $label failed: ${throwable.message ?: throwable.toString()}"
            )
        }
    }

    internal fun <T> invokeCallback(
        label: String,
        callback: ((T) -> Unit)?,
        value: T,
    ): Unit {
        if (callback == null) return
        try {
            callback(value)
        } catch (throwable: Throwable) {
            reportInternalError(
                "CascadeEditorController callback $label failed: ${throwable.message ?: throwable.toString()}"
            )
        }
    }

    internal fun reportInternalError(message: String): Unit {
        // Callbacks are contractually delivered on the main thread. When a misuse is
        // detected off the main thread (the onMainThread guard), marshal the report
        // onto the main queue so a Swift onInternalError handler can safely touch UI.
        if (NSThread.isMainThread) {
            deliverInternalError(message)
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                deliverInternalError(message)
            }
        }
    }

    private fun deliverInternalError(message: String) {
        try {
            onInternalError?.invoke(message)
        } catch (_: Throwable) {
            // Host error-reporting callbacks must never escape into Swift/Obj-C.
        }
    }

    private fun failedLoadResult(message: String): CascadeDocumentLoadResult {
        return CascadeDocumentLoadResult(success = false, warningMessages = listOf(message))
    }

    private fun DocumentDecodeWarning.message(): String = when (this) {
        is DocumentDecodeWarning.DocumentParseFailed -> "Document parse failed: $reason"
        is DocumentDecodeWarning.DuplicateIdRegenerated ->
            "Duplicate block id '$originalId' at block $blockIndex was replaced with '$newId'."

        is DocumentDecodeWarning.MissingIdRegenerated -> {
            "Missing block id at block $blockIndex was replaced."
        }

        is DocumentDecodeWarning.InvalidBlockAttributeParam ->
            "Invalid block attribute at block $blockIndex: $param; using $fallback."

        is DocumentDecodeWarning.InvalidBlockTypeParam ->
            "Invalid '$param' for block type '$typeId' at block $blockIndex; using $fallback."

        is DocumentDecodeWarning.MalformedBlockSkipped ->
            "Malformed block at index $blockIndex was skipped: $reason."

        is DocumentDecodeWarning.UnknownBlockTypePreserved ->
            "Unknown block type '$typeId' at block $blockIndex was preserved."

        is DocumentDecodeWarning.UnknownContentKind ->
            "Unknown content kind '$kind' at block $blockIndex was preserved as custom content."

        is DocumentDecodeWarning.UnsupportedCustomDataDropped ->
            "Unsupported custom value '$key' at block $blockIndex was dropped: $valueType."
    }

    private fun HtmlDecodeWarning.message(): String = when (this) {
        is HtmlDecodeWarning.BlockInInlineContext ->
            "HTML block tag '$tag' at offset $charOffset was flattened in inline content."

        is HtmlDecodeWarning.DecoderException ->
            "HTML decoder failed for '${tag ?: "unknown"}' at offset $charOffset: $message."

        is HtmlDecodeWarning.DroppedAttribute ->
            "HTML attribute '$attr' on '$tag' at offset $charOffset was dropped: $reason."

        is HtmlDecodeWarning.DroppedContent ->
            "HTML content at offset $charOffset was dropped: $reason."

        is HtmlDecodeWarning.InputLimitExceeded ->
            "HTML input exceeded limit $limit with $actual characters."

        is HtmlDecodeWarning.InvalidAttribute ->
            "HTML attribute '$attr' on '$tag' at offset $charOffset was invalid: $reason."

        is HtmlDecodeWarning.MismatchedNesting ->
            "HTML nesting at offset $charOffset expected '$expected' but found '$found'."

        is HtmlDecodeWarning.StrayClosingTag ->
            "HTML closing tag '$tag' at offset $charOffset had no matching open tag."

        is HtmlDecodeWarning.UnclosedTag ->
            "HTML tag '$tag' at offset $charOffset was not closed."

        is HtmlDecodeWarning.UnknownAttribute ->
            "HTML attribute '$attr' on '$tag' at offset $charOffset was ignored."

        is HtmlDecodeWarning.UnknownTag ->
            "HTML tag '$tag' at offset $charOffset was ignored."
    }
}

internal fun List<HtmlDecodeWarning>.isSuccessfulHtmlLoad(): Boolean {
    return none { warning ->
        warning is HtmlDecodeWarning.InputLimitExceeded ||
            warning is HtmlDecodeWarning.DecoderException
    }
}

private fun CascadeCustomBlockSlashBehavior.toBuiltInBehavior(): BuiltInBlockSlashBehavior = when (this) {
    CascadeCustomBlockSlashBehavior.insert -> BuiltInBlockSlashBehavior.AlwaysInsert
    CascadeCustomBlockSlashBehavior.convertInPlace -> BuiltInBlockSlashBehavior.ConvertInPlace
}
