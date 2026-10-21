---
name: rapport-analyse
description: Produit un rapport rédigé en Markdown (résultats d'analyse du tournoi, fiche joueur ou équipe) à partir des réponses de l'onglet Analyse. À utiliser pour présenter ou partager des résultats : « fais-moi un rapport sur le meilleur buteur », « synthèse du tournoi ».
---

# Rapport d'analyse rédigé

Rédige un rapport Markdown à partir des **chiffres calculés par l'application**, jamais de mémoire ni d'un calcul du modèle (principe VII).

## Source des données

1. Si un serveur MCP `statsfoot` est configuré (voir `.mcp.json`), l'utiliser.
2. Sinon, l'API REST de l'application (démarrée sur `http://localhost:8080`, sinon la lancer comme décrit au README) :
   - `GET /api/analyse/questions` : les questions du catalogue ;
   - `GET /api/analyse/{questionId}` : classement et vainqueur(s) d'une question ;
   - `GET /api/analyse?q=...` : question libre, ou nom d'un joueur / d'une équipe ;
   - `GET /api/players/{id}/report`, `GET /api/teams/{id}/report` : rapports complets.

## Procédure

1. Cadrer : quelles questions ou quels joueurs/équipes ? Sans précision, prendre les 8 questions du catalogue.
2. Récupérer les réponses (une requête par question). Statut `NO_DATA` : le dire, ne rien inventer.
3. Rédiger, dans cet ordre : titre et compétition ; un paragraphe de synthèse ; une section par question (réponse en gras, valeur et unité, règle de départage, top 5 en tableau Markdown, ex aequo mentionnés) ; une fiche pour chaque joueur/équipe demandé (totaux, meilleurs matchs, rangs).
4. Signaler partout les **données fictives** (`fictitious: true`, `fictitiousFields`) : passes décisives, duels, distance, etc. — « (fictif) » dans le tableau et une note en fin de rapport. Une valeur absente (`null`) s'écrit « n.d. », jamais 0.
5. Écrire le rapport dans un fichier `.md` (demander l'emplacement s'il n'est pas donné, par défaut `rapports/analyse-AAAA-MM-JJ.md`) et le résumer en deux phrases dans la réponse.

## Règles

- Citer les valeurs telles que renvoyées par l'API ; aucun chiffre ajouté, déduit ou arrondi autrement.
- Ne pas mélanger des données de compétitions différentes (l'API répond sur la dernière compétition importée).
- Ne pas commiter le rapport sans demande.
