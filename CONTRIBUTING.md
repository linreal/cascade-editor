# Contributing to Cascade Editor

Thanks for your interest in contributing! This guide will help you get started.

## Development Setup

```bash
git clone https://github.com/linreal/cascade-editor.git
cd cascade-editor
```

Open the project in Android Studio or IntelliJ IDEA with the Kotlin Multiplatform plugin installed.

## Running Tests

```bash
./gradlew :editor:allTests
```

All tests must pass before submitting a PR.

## Code Conventions

- **`explicitApi()`** — all public declarations require explicit visibility modifiers (`public`, `internal`, `private`)
- **`@Immutable` data classes** — all state objects must be annotated with `@Immutable`
- **Unidirectional data flow** — state mutations go through the `EditorAction` sealed hierarchy with a `reduce()` override
- **`internal` by default** — only expose what consumers need. Implementation details stay `internal`
- **No mocks in tests** — reducers are pure functions, test them directly with input/output assertions

## Making Changes

1. Fork the repository and create a branch from `main`
2. Make your changes
3. Add or update tests for your changes
4. Run `./gradlew :editor:allTests` and ensure all tests pass
5. Submit a pull request

## Pull Request Guidelines

- Keep PRs focused — one feature or fix per PR
- Describe what changed and why in the PR description
- Reference related issues if applicable
- Ensure tests cover new behavior

## Adding New Features

- **New block type** — implement `CustomBlockType` + `BlockRenderer`, register via `BlockDescriptor`
- **New action** — add a subclass to the `EditorAction` sealed hierarchy with a `reduce()` override
- **New span style** — extend `SpanStyle` and update `SpanAlgorithms` accordingly

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full quick-reference table, layer interactions, and conventions.

## Reporting Issues

- Use the [bug report template](https://github.com/linreal/cascade-editor/issues/new?template=bug_report.md) for bugs
- Use the [feature request template](https://github.com/linreal/cascade-editor/issues/new?template=feature_request.md) for ideas

## License

By contributing, you agree that your contributions will be licensed under the MIT License.