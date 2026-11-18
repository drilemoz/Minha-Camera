#!/usr/bin/env bash
set -euo pipefail

folder=${1:?Informe o diretório do lote verificado}
repo=${GITHUB_REPOSITORY:?GITHUB_REPOSITORY não definido}
commit=${GITHUB_SHA:?GITHUB_SHA não definido}
if [[ "$repo" != 'drilemoz/Minha-Camera' || ${GITHUB_REF:-} != refs/heads/main ]]; then
    echo 'Publicação restrita à branch main do repositório oficial.' >&2
    exit 1
fi

head_ref=$(git ls-remote --exit-code --heads origin refs/heads/main)
latest_commit=${head_ref%%$'\t'*}
if [[ "$commit" != "$latest_commit" ]]; then
    echo "Build de revisão anterior ignorado. A main já aponta para $latest_commit."
    exit 0
fi

python3 scripts/prepare_github_release.py "$folder"
(cd "$folder" && sha256sum --check SHA256SUMS)
version=$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["identity"][2])' "$folder/apk-report.json")
tag="v$version"
universal="$folder/MinhaCamera-$version-universal.apk"
summary=${GITHUB_STEP_SUMMARY:-/dev/null}
json="$folder/published-release.json"

if draft=$(gh release view "$tag" --repo "$repo" --json isDraft --jq '.isDraft'); then
    if [[ "$draft" != false ]]; then
        echo "Existe um rascunho para $tag. Revise-o no GitHub antes de repetir." >&2
        exit 1
    fi
    gh api "repos/$repo/releases/tags/$tag" > "$json"
    python3 scripts/prepare_github_release.py "$folder" --verify-published "$json" --existing
    printf '## Atualizações do aplicativo\n\n[%s já publicada](https://github.com/%s/releases/tag/%s). Nenhum APK foi substituído.\n' "$tag" "$repo" "$tag" >> "$summary"
    exit 0
fi

if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null; then
    echo "Tag $tag existe sem Release confirmada. Revise a tag antes de repetir." >&2
    exit 1
else
    rc=$?
    test "$rc" -eq 2
fi

# With assets attached, gh creates a draft, uploads all files, then publishes.
gh release create "$tag" "$universal" \
    --repo "$repo" --target "$commit" --title "Minha Câmera $version" --notes-file "$folder/notes.md"

for attempt in 1 2 3 4; do
    if gh api "repos/$repo/releases/tags/$tag" > "$json" &&
        python3 scripts/prepare_github_release.py "$folder" --verify-published "$json"; then
        printf '## Atualizações do aplicativo\n\n[%s publicada](https://github.com/%s/releases/tag/%s) com um único APK universal assinado.\n\n[APK universal](https://github.com/%s/releases/download/%s/MinhaCamera-%s-universal.apk)\n' "$tag" "$repo" "$tag" "$repo" "$tag" "$version" >> "$summary"
        exit 0
    fi
    if [[ "$attempt" != 4 ]]; then sleep 3; fi
done

echo "Não foi possível confirmar o APK universal da Release $tag. Confira a última divergência informada acima." >&2
exit 1
