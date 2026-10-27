# Contributing

## Report a TV

The most useful help is a report from a TV we have not tested.
Open an issue with:

- the TV or box model and its Android version
- the remote model, from the battery cover
- whether the tvask panel opens on the mic key
- the last lines of `transcripts.txt`, see the README

## Code

Keep the app small and easy to read.
It uses only the Android SDK and needs no Gradle.
The platform jar has no lambda support, so use anonymous classes.

## Commit messages

tvask follows the Go project's commit style.

- **First line:** `<area>: <summary>`. The area is the folder or file the change mainly touches (`android`, `docs`), or `all` for changes across the tree.
- **Summary:** lowercase, no full stop, about 70 characters at most. It completes the sentence "This change modifies tvask to ...".
- **Body:** after a blank line, explain why, in full sentences, wrapped at about 72 characters.
- **Issues:** end with `Fixes #12` or `Updates #12`.

```
android: keep the panel open on close system dialogs

TCL TVs send this broadcast on key presses, so the panel closed at once.

Fixes #12
```

Reference: https://go.dev/doc/contribute#commit_messages
