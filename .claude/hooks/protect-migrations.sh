#!/bin/sh
# Hook Claude Code PreToolUse (Edit|Write|MultiEdit) : refuse toute modification d'une migration Flyway
# déjà écrite (backend/src/main/resources/db/migration/V{n}__*.sql). Créer une nouvelle migration reste permis.
# Constitution, principe I : une migration appliquée ne se modifie pas, on en ajoute une suivante.
# Entrée : le JSON de l'appel d'outil sur stdin. Sortie 2 = appel refusé (message sur stderr).

input=$(cat)

# Chemin du fichier visé (file_path), barres obliques inverses JSON ramenées à des « / »
path=$(printf '%s' "$input" \
    | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' \
    | head -n 1 \
    | sed -e 's/^"file_path"[[:space:]]*:[[:space:]]*"//' -e 's/"$//' \
    | tr -s '\\' '/')

[ -z "$path" ] && exit 0

case "$path" in
    */db/migration/V[0-9]*__*.sql)
        if [ -f "$path" ]; then
            echo "Migration Flyway déjà écrite : $(basename "$path") ne doit pas être modifiée (constitution, principe I). Ajoutez une nouvelle migration V{n+1}__*.sql et recopiez-la dans donneesStats.sql." >&2
            exit 2
        fi
        ;;
esac
exit 0
