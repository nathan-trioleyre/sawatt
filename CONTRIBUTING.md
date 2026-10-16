# Contribuer à Sawatt

## Flux de travail : GitHub Flow

- `main` est toujours stable : elle compile et ses tests passent.
- Chaque tâche se fait sur une branche courte, créée depuis `main` à jour.
- Les releases sont des tags sur `main`.
- Il n'y a pas de branche `develop`, ni de branches `release` ou `hotfix`, sauf demande explicite du mainteneur.

### Branches

- Une branche se nomme selon le type de la tâche, suivi d'un sujet court en kebab-case :
  - `feat/` : nouvelle fonctionnalité ;
  - `fix/` : correction de bug ;
  - `refactor/` : restructuration sans changement de comportement ;
  - `docs/` : documentation ;
  - `chore/` : outillage, dépendances, CI.

  Par exemple : `feat/countdown-beeps`, `fix/cadence-overflow`.
- Une branche ne traite qu'**un seul sujet**. Si une tâche déborde, on la découpe en plusieurs branches.

### Protection de `main`

- On ne committe jamais directement sur `main`.
- On ne fait jamais de force push sur `main` et on ne réécrit pas son historique.
- Tout arrive sur `main` par une pull request.
- **Rien n'est poussé, aucune PR n'est mergée et aucun tag n'est créé sans l'accord explicite du mainteneur.**

## Commits

### Avant chaque commit

Lancer le lint et les tests. On ne committe pas si quelque chose échoue.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :core:test :app:lintDebug :app:assembleDebug
```

- On ne committe jamais de secrets, de configuration locale (`local.properties`, fichiers d'IDE) ni d'artefacts de build.
- Les commits sont **petits**, avec un seul changement logique par commit.

### Messages : Conventional Commits

```
<type>(<scope optionnel>): <description>

<corps optionnel : le pourquoi, si ce n'est pas évident>

<BREAKING CHANGE: … si besoin>
```

- **Types** : `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `perf`.
- **Scopes courants** : `ble`, `workout`, `history`, `ui`, `core`, `build`.
- **Description** :
  - en anglais ;
  - à l'impératif ;
  - en minuscules ;
  - sans point final ;
  - moins de 72 caractères.
- On ajoute un **corps** quand le pourquoi n'est pas évident.
- Un **changement cassant** se signale par `!` après le type (`feat(core)!: …`) ou par une ligne `BREAKING CHANGE: …`.

Exemples :

```
feat(ble): add FTMS trainer discovery
fix(workout): keep interval timer running after reconnect
refactor(core)!: rename StatsSummary fields
```

## Pull requests

- Le titre suit le même format que les commits.
- La description indique :
  - **ce qui change** ;
  - **pourquoi** ;
  - **comment tester**.

  Le modèle `.github/pull_request_template.md` reprend ces trois rubriques.
- La CI doit passer avant le merge.
- Le merge se fait en **squash** par défaut.

## Versions et releases

- Versionnage sémantique : `vMAJOR.MINOR.PATCH`.
- Une release est un tag sur `main`, créé **uniquement à la demande du mainteneur**.
- Le changelog est généré à partir des messages de commit.

## Règles d'architecture

- Toute la communication Bluetooth passe par une **interface abstraite**.
- Une **implémentation simulée** du home trainer sert aux tests.
- La logique des séances d'intervalles ne dépend **jamais directement** de la couche Bluetooth.
- **Aucun test unitaire ne doit nécessiter de matériel réel.**
