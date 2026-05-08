package io.github.linreal.cascade.editor.ui

/**
 * Controls editor-owned slash command palette behavior.
 *
 * [Default] enables the built-in slash palette: typing `/` in a text block opens
 * the popup with built-in and custom commands from [io.github.linreal.cascade.editor.slash.SlashCommandRegistry].
 * [None] disables the feature entirely so `/` inserts as a literal character with
 * no popup, no key interception, and no associated state churn.
 *
 * Disabling is implemented by skipping construction of the slash text observer
 * in text blocks, so no `OpenSlashCommand` is ever dispatched and downstream
 * popup/key-handling paths stay inert without scattered conditionals.
 */
public sealed interface SlashCommandSlot {
    public data object Default : SlashCommandSlot
    public data object None : SlashCommandSlot
}
