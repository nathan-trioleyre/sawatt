# Instructions pour Claude

Les règles du projet sont dans [CONTRIBUTING.md](CONTRIBUTING.md) : lis-les et applique-les à chaque tâche.

Points qui demandent une attention particulière :

- **Avant de commencer** : partir de `main` à jour et créer une branche `feat/`, `fix/`, `refactor/`, `docs/` ou `chore/` + sujet en kebab-case. Si la tâche couvre plusieurs sujets, propose de la découper.
- **Jamais sans accord explicite** : `git push`, merge de PR, création de tag, ou toute action sur le dépôt GitHub distant.
- **Jamais** : commit sur `main`, force push sur `main`, réécriture de l'historique de `main`.
- **Avant chaque commit** : `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :core:test :app:lintDebug :app:assembleDebug`. Ne committe pas si ça échoue.
- **Messages de commit** : en anglais, au format Conventional Commits.
- **Documentation** : en français.
- **Architecture** : la logique de séance (`core`) ne dépend jamais de la couche Bluetooth. Les tests unitaires n'utilisent jamais de matériel réel.
- **Aucune mention de « BODY BIKE »** dans le dépôt. Les notes de rétro-ingénierie restent hors du dépôt.
