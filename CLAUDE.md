# Instructions for Claude

The project rules are in [CONTRIBUTING.md](CONTRIBUTING.md): read them and apply them to every task.

Points that need particular care:

- **Before starting**: start from an up-to-date `main` and create a `feat/`, `fix/`, `refactor/`, `docs/` or `chore/` branch + kebab-case topic. If the task covers several topics, propose splitting it.
- **Never without explicit approval**: `git push`, merging a PR, creating a tag, or any action on the remote GitHub repository.
- **Never**: commit to `main`, force-push to `main`, rewrite `main`'s history.
- **Before every commit**: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :core:test :app:lintDebug :app:assembleDebug`. Do not commit if it fails.
- **Commit messages**: English, Conventional Commits format.
- **Documentation**: English. The README is a user-facing product page; developer content goes in CONTRIBUTING.md.
- **Conversation with the maintainer**: French.
- **Architecture**: workout logic (`core`) never depends on the Bluetooth layer. Unit tests never use real hardware.
- **No mention of "BODY BIKE"** anywhere in the repository. Reverse-engineering notes stay outside the repository.
