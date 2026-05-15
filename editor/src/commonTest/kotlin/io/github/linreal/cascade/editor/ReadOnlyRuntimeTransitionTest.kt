package io.github.linreal.cascade.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.IndentForward
import io.github.linreal.cascade.editor.action.OpenSlashCommand
import io.github.linreal.cascade.editor.action.StartDrag
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.indentation.DefaultIndentationActions
import io.github.linreal.cascade.editor.indentation.IndentationState
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.richtext.DefaultFormattingActions
import io.github.linreal.cascade.editor.richtext.DefaultLinkActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.LinkActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.LinkValidationResult
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import io.github.linreal.cascade.editor.ui.LinkPopupController
import io.github.linreal.cascade.editor.ui.applyReadOnlyTransitionCleanup
import io.github.linreal.cascade.editor.ui.createToolbarSlashInsertAction
import io.github.linreal.cascade.editor.ui.renderers.createTodoCheckedChangeAction
import io.github.linreal.cascade.editor.ui.visibleSelection
import io.github.linreal.cascade.editor.ui.visibleText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadOnlyRuntimeTransitionTest {

    private val blockId = BlockId("block")
    private val otherBlockId = BlockId("other")
    private val block = textBlock(blockId, "Hello")
    private val otherBlock = textBlock(otherBlockId, "Other")
    private val linkTarget = LinkTarget(blockId, 0, 5)

    @Test
    fun `read-only transition closes active slash session without changing blocks or focus`() {
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(block)).copy(
                focusedBlockId = blockId,
                slashCommandState = OpenSlashCommand(blockId, SlashQueryRange(0, 1)).reduce(
                    EditorState.withBlocks(listOf(block))
                ).slashCommandState,
            )
        )
        val originalBlocks = holder.state.blocks

        applyReadOnlyTransitionCleanup(
            policy = EditorInteractionPolicy.ReadOnly,
            stateHolder = holder,
            linkPopupController = popupController(),
        )

        assertNull(holder.state.slashCommandState)
        assertEquals(originalBlocks, holder.state.blocks)
        assertEquals(blockId, holder.state.focusedBlockId)
    }

    @Test
    fun `read-only transition cancels active drag without changing blocks`() {
        val dragging = StartDrag(blockId, touchOffsetY = 0f)
            .reduce(EditorState.withBlocks(listOf(block, otherBlock)))
        val holder = EditorStateHolder(dragging)
        val originalBlocks = holder.state.blocks

        applyReadOnlyTransitionCleanup(
            policy = EditorInteractionPolicy.ReadOnly,
            stateHolder = holder,
            linkPopupController = popupController(),
        )

        assertNull(holder.state.dragState)
        assertEquals(originalBlocks, holder.state.blocks)
    }

    @Test
    fun `read-only transition clears block selection without changing blocks`() {
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(block, otherBlock)).copy(
                selectedBlockIds = setOf(blockId, otherBlockId),
            )
        )
        val originalBlocks = holder.state.blocks

        applyReadOnlyTransitionCleanup(
            policy = EditorInteractionPolicy.ReadOnly,
            stateHolder = holder,
            linkPopupController = popupController(),
        )

        assertTrue(holder.state.selectedBlockIds.isEmpty())
        assertEquals(originalBlocks, holder.state.blocks)
    }

    @Test
    fun `read-only transition dismisses link popup without applying or removing link`() {
        val linkActions = RecordingLinkActions()
        val controller = popupController(
            linkActions = linkActions,
            policyProvider = { EditorInteractionPolicy.Editable },
        )
        controller.open()
        assertNotNull(controller.session)

        applyReadOnlyTransitionCleanup(
            policy = EditorInteractionPolicy.ReadOnly,
            stateHolder = EditorStateHolder(EditorState.withBlocks(listOf(block))),
            linkPopupController = controller,
        )

        assertNull(controller.session)
        assertTrue(linkActions.applyCalls.isEmpty())
        assertTrue(linkActions.removeCalls.isEmpty())
    }

    @Test
    fun `captured link popup controller reads latest read-only policy before opening`() {
        var policy = EditorInteractionPolicy.Editable
        val controller = popupController(policyProvider = { policy })

        policy = EditorInteractionPolicy.ReadOnly
        controller.open()

        assertNull(controller.session)
    }

    @Test
    fun `editable transition leaves transient editor UI state intact`() {
        val dragging = StartDrag(blockId, touchOffsetY = 0f)
            .reduce(EditorState.withBlocks(listOf(block, otherBlock)))
            .copy(
                selectedBlockIds = setOf(otherBlockId),
                slashCommandState = OpenSlashCommand(blockId, SlashQueryRange(0, 1)).reduce(
                    EditorState.withBlocks(listOf(block, otherBlock))
                ).slashCommandState,
            )
        val holder = EditorStateHolder(dragging)

        applyReadOnlyTransitionCleanup(
            policy = EditorInteractionPolicy.Editable,
            stateHolder = holder,
            linkPopupController = popupController(),
        )

        assertNotNull(holder.state.slashCommandState)
        assertNotNull(holder.state.dragState)
        assertEquals(setOf(otherBlockId), holder.state.selectedBlockIds)
    }

    @Test
    fun `captured formatting action reads latest read-only policy before mutating`() {
        var policy = EditorInteractionPolicy.Editable
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val dispatchedActions = mutableListOf<EditorAction>()
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(block)).copy(focusedBlockId = blockId)
        )
        textStates.getOrCreate(blockId, "Hello").edit {
            selection = TextRange(1, 6)
        }
        spanStates.getOrCreate(blockId, emptyList(), textLength = 5)
        val actions = DefaultFormattingActions(
            stateHolder = holder,
            textStates = textStates,
            spanActionDispatcher = SpanActionDispatcher(
                dispatchFn = dispatchedActions::add,
                textStates = textStates,
                spanStates = spanStates,
            ),
            policyProvider = { policy },
        )

        policy = EditorInteractionPolicy.ReadOnly
        actions.applyStyle(SpanStyle.Bold)

        assertTrue(dispatchedActions.isEmpty())
        assertTrue(spanStates.getSpans(blockId).isEmpty())
    }

    @Test
    fun `captured indentation action reads latest read-only policy before mutating`() {
        var policy = EditorInteractionPolicy.Editable
        val dispatchedActions = mutableListOf<EditorAction>()
        val actions = DefaultIndentationActions(
            stateProvider = {
                IndentationState(
                    canIndentForward = true,
                    canIndentBackward = false,
                    targetBlockIds = listOf(blockId),
                )
            },
            dispatchAction = dispatchedActions::add,
            policyProvider = { policy },
        )

        policy = EditorInteractionPolicy.ReadOnly
        actions.indentForward()

        assertTrue(dispatchedActions.isEmpty())
    }

    @Test
    fun `captured link action reads latest read-only policy before mutating`() {
        var policy = EditorInteractionPolicy.Editable
        val delegate = RecordingLinkActions()
        val actions = DefaultLinkActions(
            stateProvider = { enabledLinkState() },
            delegate = delegate,
            policyProvider = { policy },
        )

        policy = EditorInteractionPolicy.ReadOnly
        val result = actions.applyLink(linkTarget, "example.com", "Example")
        actions.removeLink(linkTarget)

        assertEquals(LinkValidationResult.Valid("https://example.com"), result)
        assertTrue(delegate.applyCalls.isEmpty())
        assertTrue(delegate.removeCalls.isEmpty())
    }

    @Test
    fun `captured toolbar slash action reads latest disabled state before mutating text`() {
        val textStates = BlockTextStates()
        val textFieldState = textStates.getOrCreate(blockId, "abc", initialCursorPosition = 1)
        var slashEnabled = true
        val action = createToolbarSlashInsertAction(
            slashEnabledProvider = { slashEnabled },
            formattingState = mutableStateOf(
                FormattingState(
                    styles = emptyMap(),
                    canFormat = false,
                    focusedBlockId = blockId,
                    selectionCollapsed = true,
                )
            ),
            textStates = textStates,
        )

        slashEnabled = false
        action()

        assertEquals("abc", textFieldState.visibleText())
        assertEquals(TextRange(1), textFieldState.visibleSelection())
    }

    @Test
    fun `captured todo checkbox action reads latest read-only policy before mutating`() {
        val callbacks = RecordingBlockCallbacks()
        var policy = EditorInteractionPolicy.Editable
        val action = createTodoCheckedChangeAction(
            blockId = blockId,
            callbacksProvider = { callbacks },
            policyProvider = { policy },
        )

        policy = EditorInteractionPolicy.ReadOnly
        action(true)

        assertTrue(callbacks.actions.isEmpty())
    }

    @Test
    fun `transitioning back to editable lets captured policy-aware actions mutate again`() {
        var policy = EditorInteractionPolicy.ReadOnly
        val dispatchedActions = mutableListOf<EditorAction>()
        val actions = DefaultIndentationActions(
            stateProvider = {
                IndentationState(
                    canIndentForward = true,
                    canIndentBackward = false,
                    targetBlockIds = listOf(blockId),
                )
            },
            dispatchAction = dispatchedActions::add,
            policyProvider = { policy },
        )

        actions.indentForward()
        policy = EditorInteractionPolicy.Editable
        actions.indentForward()

        assertEquals<List<EditorAction>>(listOf(IndentForward), dispatchedActions)
    }

    private fun popupController(
        linkActions: RecordingLinkActions = RecordingLinkActions(),
        policyProvider: () -> EditorInteractionPolicy = { EditorInteractionPolicy.Editable },
    ): LinkPopupController {
        return LinkPopupController(
            linkActions = linkActions,
            stateHolder = EditorStateHolder(EditorState.withBlocks(listOf(block))),
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
            linkStateProvider = { enabledLinkState() },
            policyProvider = policyProvider,
        )
    }

    private fun enabledLinkState(): LinkState {
        return LinkState(
            canLink = true,
            focusedBlockId = blockId,
            target = linkTarget,
            targetText = "Hello",
            selectionCollapsed = false,
            existingUrl = null,
            existingLinkRange = null,
            existingLinkText = null,
            isInsideLink = false,
            intersectsLink = false,
        )
    }

    private fun textBlock(id: BlockId, text: String): Block {
        return Block(
            id = id,
            type = BlockType.Paragraph,
            content = BlockContent.Text(text),
        )
    }

    private data class ApplyCall(
        val target: LinkTarget,
        val url: String,
        val title: String?,
    )

    private class RecordingLinkActions : LinkActions {
        val applyCalls = mutableListOf<ApplyCall>()
        val removeCalls = mutableListOf<LinkTarget>()

        override fun applyLink(
            target: LinkTarget,
            url: String,
            title: String?,
        ): LinkValidationResult {
            applyCalls += ApplyCall(target, url, title)
            return LinkValidationResult.Valid("https://example.com")
        }

        override fun removeLink(target: LinkTarget) {
            removeCalls += target
        }
    }

    private class RecordingBlockCallbacks : BlockCallbacks {
        val actions = mutableListOf<EditorAction>()

        override fun dispatch(action: EditorAction) {
            actions += action
        }

        override fun onFocus(blockId: BlockId) = Unit

        override fun onEnter(blockId: BlockId, cursorPosition: Int) = Unit

        override fun onBackspaceAtStart(blockId: BlockId) = Unit

        override fun onDeleteAtEnd(blockId: BlockId) = Unit

        override fun onClick(blockId: BlockId) = Unit

        override fun onLongClick(blockId: BlockId) = Unit

        override fun onDragStart(blockId: BlockId, touchOffsetY: Float) = Unit

        override fun onSlashCommand(
            blockId: BlockId,
            queryRange: SlashQueryRange,
            initialQuery: String,
        ) = Unit
    }
}
