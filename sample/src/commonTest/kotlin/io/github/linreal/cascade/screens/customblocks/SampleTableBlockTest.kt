package io.github.linreal.cascade.screens.customblocks

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.serialization.toJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SampleTableBlockTest {

    @Test
    fun `valid payload parsing`() {
        val content = BlockContent.Custom(
            typeId = "table",
            data = mapOf(
                "rows" to listOf(
                    listOf("Column A", "Column B"),
                    listOf("Value A", "Value B"),
                ),
                "headerRow" to true,
            ),
        )

        val model = SampleTableModel.fromContent(content)

        assertEquals(
            SampleTableModel(
                rows = listOf(
                    listOf("Column A", "Column B"),
                    listOf("Value A", "Value B"),
                ),
                headerRow = true,
            ),
            model,
        )
        assertEquals(2, model.rowCount)
        assertEquals(2, model.columnCount)
        assertEquals("Value B", model.valueAt(row = 1, column = 1))
    }

    @Test
    fun `malformed content falls back to default table`() {
        assertEquals(
            SampleTableModel.default(),
            SampleTableModel.fromContent(BlockContent.Text("not a table")),
        )
        assertEquals(
            SampleTableModel.default(),
            SampleTableModel.fromContent(
                BlockContent.Custom(
                    typeId = "table",
                    data = mapOf(
                        "rows" to "not rows",
                        "headerRow" to false,
                    ),
                )
            ),
        )
        assertEquals(
            SampleTableModel.default(),
            SampleTableModel.fromContent(
                BlockContent.Custom(
                    typeId = "chart",
                    data = mapOf("rows" to listOf(listOf("A"))),
                )
            ),
        )
    }

    @Test
    fun `ragged rows are padded to the widest row`() {
        val content = BlockContent.Custom(
            typeId = "table",
            data = mapOf(
                "rows" to listOf(
                    listOf("A"),
                    listOf("B", "C", "D"),
                    listOf("E", "F"),
                ),
                "headerRow" to false,
            ),
        )

        val model = SampleTableModel.fromContent(content)

        assertEquals(
            SampleTableModel(
                rows = listOf(
                    listOf("A", "", ""),
                    listOf("B", "C", "D"),
                    listOf("E", "F", ""),
                ),
                headerRow = false,
            ),
            model,
        )
        assertEquals(3, model.columnCount)
    }

    @Test
    fun `editCell changes only the targeted cell`() {
        val model = SampleTableModel(
            rows = listOf(
                listOf("A1", "B1"),
                listOf("A2", "B2"),
            ),
            headerRow = false,
        )

        val edited = model.editCell(row = 1, column = 0, value = "A2 edited")

        assertEquals(
            SampleTableModel(
                rows = listOf(
                    listOf("A1", "B1"),
                    listOf("A2 edited", "B2"),
                ),
                headerRow = false,
            ),
            edited,
        )
        assertEquals("A1", edited.valueAt(row = 0, column = 0))
        assertEquals("B2", edited.valueAt(row = 1, column = 1))
        assertEquals(edited, edited.editCell(row = 3, column = 0, value = "ignored"))
        assertEquals(edited, edited.editCell(row = 0, column = 3, value = "ignored"))
    }

    @Test
    fun `row add delete and final-row delete no-op`() {
        val model = SampleTableModel(
            rows = listOf(
                listOf("A", "B"),
                listOf("C", "D"),
            ),
            headerRow = true,
        )

        val added = model.addRow(afterRow = 0)

        assertEquals(
            listOf(
                listOf("A", "B"),
                listOf("", ""),
                listOf("C", "D"),
            ),
            added.rows,
        )
        assertEquals(model, added.deleteRow(1))

        val singleRow = SampleTableModel(rows = listOf(listOf("only")), headerRow = false)
        assertEquals(singleRow, singleRow.deleteRow(0))
    }

    @Test
    fun `column add delete and final-column delete no-op`() {
        val model = SampleTableModel(
            rows = listOf(
                listOf("A", "B"),
                listOf("C", "D"),
            ),
            headerRow = true,
        )

        val added = model.addColumn(afterColumn = 0)

        assertEquals(
            listOf(
                listOf("A", "", "B"),
                listOf("C", "", "D"),
            ),
            added.rows,
        )
        assertEquals(model, added.deleteColumn(1))

        val singleColumn = SampleTableModel(rows = listOf(listOf("only")), headerRow = false)
        assertEquals(singleColumn, singleColumn.deleteColumn(0))
    }

    @Test
    fun `row and column inserts coerce indexes`() {
        val model = SampleTableModel(
            rows = listOf(
                listOf("A", "B"),
                listOf("C", "D"),
            ),
            headerRow = false,
        )

        assertEquals(
            listOf(
                listOf("", ""),
                listOf("A", "B"),
                listOf("C", "D"),
            ),
            model.addRow(afterRow = -1).rows,
        )
        assertEquals(
            listOf(
                listOf("A", "B"),
                listOf("C", "D"),
                listOf("", ""),
            ),
            model.addRow(afterRow = 99).rows,
        )
        assertEquals(
            listOf(
                listOf("", "A", "B"),
                listOf("", "C", "D"),
            ),
            model.addColumn(afterColumn = -1).rows,
        )
        assertEquals(
            listOf(
                listOf("A", "B", ""),
                listOf("C", "D", ""),
            ),
            model.addColumn(afterColumn = 99).rows,
        )
    }

    @Test
    fun `header row toggle`() {
        val model = SampleTableModel.default()

        assertEquals(false, model.withHeaderRow(false).headerRow)
        assertEquals(true, model.withHeaderRow(false).withHeaderRow(true).headerRow)
    }

    @Test
    fun `toContent output shape`() {
        val rows = listOf(
            listOf("A", "B"),
            listOf("C", "D"),
        )
        val model = SampleTableModel(rows = rows, headerRow = false)

        val content = model.toContent()

        assertEquals("table", content.typeId)
        assertEquals(rows, content.data["rows"])
        assertEquals(false, content.data["headerRow"])
        assertEquals(setOf("rows", "headerRow"), content.data.keys)
    }

    @Test
    fun `public JSON round trip preserves table model`() {
        val expectedModel = SampleTableModel(
            rows = listOf(
                listOf("H1", "H2"),
                listOf("x", "y"),
            ),
            headerRow = false,
        )
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(expectedModel.toBlock()))
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val json = holder.toJson(textStates, spanStates)
        val freshHolder = EditorStateHolder()
        freshHolder.loadFromJson(json, textStates, spanStates)

        val loadedBlock = freshHolder.state.blocks.first()
        assertEquals("table", loadedBlock.type.typeId)
        val customContent = assertIs<BlockContent.Custom>(loadedBlock.content)
        assertEquals("table", customContent.typeId)
        assertEquals(false, customContent.data["headerRow"])
        assertEquals(expectedModel, SampleTableModel.fromBlock(loadedBlock))
    }

    @Test
    fun `sample table block uses table type and content`() {
        val id = BlockId("sample-table")

        val block = sampleTableBlock(id)

        assertEquals(id, block.id)
        assertEquals(TableBlockType, block.type)
        assertEquals(SampleTableModel.default().toContent(), block.content)
    }
}
