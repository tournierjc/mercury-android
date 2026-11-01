---
name: phone-bridge
description: Lire le téléphone Android de l'utilisateur (agenda, contacts, SMS, messages d'apps) via le pont Mercury Android, en lecture seule.
version: 0.1.0
category: device
categories:
  - device
  - android
intents:
  - mon agenda
  - mon calendrier
  - qu'est-ce que j'ai demain
  - mes rendez-vous
  - qui m'a envoyé un message
  - mes messages
  - mes sms
  - le numéro de
  - les coordonnées de
  - what's on my calendar
  - my schedule
  - who texted me
  - my messages
  - battery
  - batterie du téléphone
tags:
  - android
  - calendar
  - contacts
  - sms
  - device
allowed-tools:
  - fetch_url
---

# Pont téléphone (Mercury Android)

Le téléphone expose un serveur HTTP **lecture seule**, joignable seulement sur le
tailnet. Toutes les routes sont des `GET` et exigent le token.

```bash
PHONE_URL="<url affichée dans l'app : Pont appareil → URL>"
PHONE_TOKEN="<token affiché dans l'app : Pont appareil → Afficher>"
```

`fetch_url` de Mercury n'envoie pas d'en-têtes : passe le token en paramètre.
Ce passage en paramètre n'est accepté que depuis le tailnet (ou la boucle locale) :
si le pont écoute sur un réseau local ordinaire, les appels sont refusés — demande à
l'utilisateur de lancer Tailscale des deux côtés.

## Routes

| But | Appel |
|---|---|
| Vérifier que le pont répond | `GET $PHONE_URL/health` (pas de token, répond juste `{ok:true}`) |
| Batterie, réseau, fuseau, heure | `GET $PHONE_URL/device/status?token=$PHONE_TOKEN` |
| Agenda | `GET $PHONE_URL/calendar?from=<ISO8601>&to=<ISO8601>&limit=20&token=$PHONE_TOKEN` |
| Contacts | `GET $PHONE_URL/contacts?q=<nom>&limit=10&token=$PHONE_TOKEN` |
| SMS reçus | `GET $PHONE_URL/sms?limit=20&token=$PHONE_TOKEN` |
| SMS + messages d'apps (WhatsApp, Signal…) | `GET $PHONE_URL/messages?limit=15&token=$PHONE_TOKEN` |

`from`/`to` sont en ISO 8601 avec fuseau, par exemple `2026-10-25T00:00:00+01:00`.
Sans `from`, le pont prend aujourd'hui 00:00 ; sans `to`, +7 jours.

## Comment s'en servir

1. Question d'agenda (« qu'est-ce que j'ai demain ? ») → `/calendar` sur la journée
   demandée, puis réponds en liste courte avec les heures.
2. Question sur une personne (« le numéro de Paul ») → `/contacts?q=Paul`.
3. Question sur des messages → `/messages` ou `/sms`, **seulement si l'utilisateur
   demande explicitement** de regarder ses messages.
4. État du téléphone (batterie faible, pas de réseau) → `/device/status`, utile quand
   une réponse semble ne pas partir.

## Règles

- Lecture seule : aucune route n'écrit, ne supprime ni n'envoie.
- Ne lis pas les messages sans demande explicite. Résume, ne recopie pas des
  conversations entières, et ne mélange pas deux fils.
- Les messages d'apps viennent des **notifications** : contenu parfois tronqué,
  pièces jointes invisibles, exhaustivité non garantie — dis-le si ça compte.
- N'affiche jamais le token dans une réponse.
- Codes d'erreur : `401` token invalide ou révoqué (l'utilisateur doit le recopier
  depuis l'app), `403` permission non accordée sur le téléphone, `429` trop de
  requêtes (attends une minute), timeout → pont arrêté, l'utilisateur doit relancer
  « Pont actif » dans l'app.
