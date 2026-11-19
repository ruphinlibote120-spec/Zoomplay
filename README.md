# Zoomplay

Lecteur de musique mobile : il lit les fichiers audio de ton téléphone.

## Fonctions

- Bibliothèque avec recherche et tri, import de fichiers MP3, M4A, WAV, OGG, FLAC (titre, artiste, album et pochette lus dans le fichier)
- Playlists, favoris, file d'attente, lecture aléatoire, répétition, vitesse de lecture, minuteur de sommeil
- Amplificateur de volume (100 à 300 %) et égaliseur à 5 bandes avec préréglages
- Barre de lecture toujours visible, reprise de la dernière écoute à l'ouverture
- Commandes vocales : « Zoom Play music », « Zoom Next », « Zoom Prev », « Zoom Pause », avec un bouton micro pour activer ou désactiver
- Installable sur l'écran d'accueil (icône Zoomplay) et utilisable hors ligne

## Limites de la version web

- La bibliothèque est enregistrée dans le navigateur de l'appareil, elle ne se synchronise pas.
- Une page web ne peut pas parcourir les fichiers du téléphone ni écouter le micro en arrière-plan, écran éteint. Pour cela, il faut une application Android native (étape suivante du projet).
- Les commandes vocales dépendent du micro et de la reconnaissance vocale du navigateur.

## Utiliser l'appli

Ouvre la page du site (GitHub Pages) dans Chrome sur Android, puis menu ⋮ → **Ajouter à l'écran d'accueil**.

En local : `python3 -m http.server` dans ce dossier, puis ouvre http://localhost:8000.

Les trois morceaux d'exemple sont synthétisés par l'appli, sans droit d'auteur.
