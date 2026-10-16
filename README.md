# Sawatt

> *Ça watt ? Ça watt !*

Application Android (Kotlin + Jetpack Compose) d'entraînement par intervalles pour vélos d'intérieur
connectés et capteurs de puissance Bluetooth.

L'idée de départ : pendant une séance, connaître sa **puissance moyenne sur l'intervalle en cours**,
et pas seulement la puissance instantanée ou la moyenne de toute la séance. On peut ainsi suivre une
séance comme `3 × 15'` et voir sa moyenne pour chaque bloc.

## Fonctionnalités

- **Connexion vélo + cardiofréquencemètre** en Bluetooth standard :
  - service Cycling Power `0x1818/0x2A63` pour le vélo ou le capteur de puissance ;
  - service Heart Rate `0x180D/0x2A37` pour la ceinture ;
  - mémorise les appareils et se reconnecte automatiquement quand le vélo se réveille ;
  - lecture seule : Sawatt n'écrit jamais sur le vélo.
- **Séances structurées** :
  - blocs avec une durée, une puissance cible et une cadence cible facultative ;
  - séries répétées, par ex. `10' d'échauffement → 3 × [15' à 250 W + 5' de récupération] → 10' de retour au calme` ;
  - cet exemple est préinstallé ;
  - pendant la séance : cible en cours, temps restant, progression, bloc suivant, et un bip à chaque transition.
- **Statistiques par intervalle** :
  - puissance moyenne en direct depuis le début de l'intervalle, colorée selon la cible ;
  - puissance max, cadence moyenne et fréquence cardiaque moyenne de l'intervalle ;
  - en fin de séance, un tableau avec une ligne par intervalle, enregistré dans l'historique.
- **Bouton Tour (Lap)** :
  - en sortie libre, il démarre un nouvel intervalle ;
  - dans une séance structurée, il passe au bloc suivant ; le bloc écourté est marqué d'un `*`.
- **Historique** :
  - pour chaque séance : tableau des intervalles, courbe de puissance avec les cibles, temps passé par zone de puissance ;
  - synthèses par semaine / mois / année.
- **Profil** : FTP et FC max, saisies ou estimées ; unités métriques ou impériales.
- **Réglages** : bip de transition, écran toujours allumé, vélo simulé (mode démo).

## Organisation du projet

```
core/   Kotlin/JVM pur, testé unitairement
        ble/       parseurs Cycling Power / Heart Rate / advertising, calcul de cadence
        trainer/   interface TrainerConnection + SimulatedTrainer (home trainer simulé)
        ride/      vitesse virtuelle, kcal, estimations FTP et FC max, zones
        workout/   WorkoutPlan, WorkoutSession (moteur d'intervalles), statistiques
        history/   agrégation semaine/mois/année, configuration JSON
app/    Application Android
        ble/       BleManager (TrainerConnection en Bluetooth), SwitchingTrainerConnection
                   (bascule réel / simulé selon le réglage « vélo simulé »)
        workout/   WorkoutController (horloge 1 Hz, bips, sauvegarde), WorkoutService (premier plan)
        data/      dépôts en fichiers JSON (profil, réglages, séances, historique)
        ui/        écrans Compose
```

### Fonctionnement

```
Vélo ──BLE──► TrainerConnection ──► WorkoutController ──► WorkoutSession ──► Écrans
Ceinture ─BLE─┘ (BleManager ou    (horloge 1 Hz,       (moteur            (Compose)
                 SimulatedTrainer)  bips, sauvegarde)    d'intervalles)
                                                               │
                                                               ▼
                                               HistoryRepository (fichiers JSON)
```

- Toute la communication avec les capteurs passe par l'interface `TrainerConnection` (module
  `core`). `BleManager` l'implémente en Bluetooth, `SimulatedTrainer` sans matériel : les tests
  unitaires et le mode démo utilisent ce dernier.
- Le vélo envoie environ deux mesures par seconde : la puissance, et un compteur de tours de
  pédalier horodaté. La cadence est déduite de ce compteur : `60 × Δtours × 1024 / Δtemps`.
- Le moteur (`WorkoutSession`) ne dépend d'aucune horloge. `tick()` est appelé une fois par seconde
  de pédalage. À chaque appel, il :
  - fait la moyenne des mesures reçues pendant la seconde et en tire un échantillon ;
  - ajoute cet échantillon à la séance et à l'intervalle en cours ;
  - fait avancer le plan.
- La pause arrête l'horloge. Sans données depuis 3 s, la puissance retombe à 0 W.
- En fin de séance, le résumé et les statistiques par intervalle sont enregistrés dans
  `history/index.json`, et la trace seconde par seconde dans `history/<id>.samples.json`.

## Compilation

Il faut un JDK 17 ou 21 (Gradle 8.14 ne fonctionne pas avec le JDK 25) et le SDK Android (plateforme 36).
Le fichier `local.properties` (non versionné) indique l'emplacement du SDK, par ex.
`sdk.dir=/home/<vous>/Android/Sdk`.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :core:test            # tests unitaires (protocole, formules, moteur d'intervalles, historique)
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Identifiant de l'application : `app.sawatt`.

## État du projet

### Fait

- Module `core` : parseurs BLE, calcul de cadence, formules, moteur de séance par intervalles,
  agrégation de l'historique, home trainer simulé. 25 tests unitaires, qui passent, sans aucun matériel.
- Application Android :
  - connexion vélo / ceinture ;
  - sortie libre avec Tour ;
  - séances structurées (éditeur + exécution) ;
  - statistiques par intervalle et tableau de fin de séance ;
  - historique avec courbe et synthèses ;
  - profil, réglages, vélo simulé ;
  - service de premier plan.
- Compilation et lint Android sans erreur ni avertissement.

### Reste à faire

**À vérifier en priorité (rien n'a encore tourné sur un vrai appareil) :**
- Tester sur un téléphone, d'abord avec le vélo simulé, puis avec un vrai vélo.
- Sur le vélo, vérifier :
  - la disposition réelle des paquets `0x2A63` ;
  - la cadence par rapport à la console ;
  - la reconnexion quand on arrête puis reprend le pédalage ;
  - le fonctionnement écran verrouillé sur une longue séance.
- Tester avec d'autres capteurs de puissance et home-trainers Bluetooth.

**Améliorations probables :**
- Bips de compte à rebours (3-2-1) avant chaque transition.
- Cibles en % de FTP en plus des watts.
- Interface en français (elle est en anglais pour l'instant).
- Export des séances (TCX/FIT), sauvegarde/restauration des données.
- Identité visuelle : icône et couleurs Sawatt définitives.

**Plus tard :** test FTP, test VO2max, niveaux/médailles, envoi vers Strava (écrans « À venir »).
