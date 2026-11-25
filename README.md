# Will of D

Navigateur Android sécurisé et anti-publicité, avec téléchargement de vidéos et de chapitres complets vers Rio Poneglyph.

Édité par **Thousand Labs Games**. Application à usage personnel : aucun compte, aucune publicité, aucun pistage, aucun serveur.

Dépôt officiel : https://github.com/ThousandLabs-Games/Will-of-D

## Installation

1. Ouvrez la page [Releases](https://github.com/ThousandLabs-Games/Will-of-D/releases) depuis le téléphone.
2. Téléchargez le fichier `Will-of-D-x.y.z.apk` de la dernière version.
3. Ouvrez-le et autorisez l'installation si Android le demande.

Les mises à jour suivantes se font depuis l'application : Réglages → Mises à jour.

## Fonctionnalités

| Domaine | Contenu |
|---|---|
| Navigation | Onglets, onglets privés, favoris, historique, mode téléphone / ordinateur, recherche dans la page, masquage d'éléments |
| Protection | Publicités et pisteurs (EasyList, EasyPrivacy, Peter Lowe, liste intégrée), redirections non sollicitées, fenêtres surgissantes, calques invisibles, ouverture d'autres applications, HTTPS automatique, anti-pistage |
| Téléchargement | Vidéos (liens directs, flux HLS chiffrés AES-128 compris), chapitres complets au format Rio Poneglyph, continuité des suites |
| Repli progressif | Téléchargement classique, récupération depuis la page, déblocage ciblé, puis constat clair |
| File | En série ou jusqu'à 4 en parallèle, notifications de progression, reprise après interruption |
| Rio Poneglyph | Zone privée invisible, échange local à sens unique réservé à la même signature, liste synchronisée |
| Sécurité | Verrouillage facultatif par code ou empreinte, jamais au simple fait de quitter |

## Échange avec Rio Poneglyph

Voir [FORMAT_PAGES.md](https://github.com/ThousandLabs-Games/Rio-Poneglyph/blob/main/FORMAT_PAGES.md) dans le dépôt de Rio Poneglyph. Les deux applications doivent être signées avec la même clé : les secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD` sont partagés au niveau de l'organisation.

Rio Poneglyph : https://github.com/ThousandLabs-Games/Rio-Poneglyph
