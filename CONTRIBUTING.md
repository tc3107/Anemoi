# Contributing to Anemoi

Thanks for contributing.

## Before You Start

- Search existing issues and pull requests before opening new ones.
- For larger changes, open an issue first to align on scope.

## Development Setup

1. Fork and clone the repository.
2. Create a feature branch from `main`.
3. Configure your local Android SDK path in `local.properties`.
4. Run checks before submitting:

```bash
./gradlew test
```

## Coding Guidelines

- Keep changes focused and atomic.
- Follow existing Kotlin and Compose patterns used in the project.
- Avoid introducing unrelated refactors in the same pull request.
- Add or update tests when behavior changes.

## Pull Request Checklist

- Clear title and summary of what changed
- Linked issue (if applicable)
- Tests added/updated for behavioral changes
- `./gradlew test` passes locally

## Commit Messages

Use clear, descriptive commit messages that explain the intent of the change.

## Review Process

Maintainers may request revisions before merging. Please keep discussion in the pull request thread for traceability.
