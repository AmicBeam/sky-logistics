#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
wiki_dir="$repo_root/wiki"
lang_file="$repo_root/versions/26.1.2/src/main/resources/assets/skylogistics/lang/zh_cn.json"

if [[ ! -d "$wiki_dir" ]]; then
    echo "wiki directory not found: $wiki_dir" >&2
    exit 1
fi

missing=0
while IFS= read -r resource_id; do
    if ! rg -q --fixed-strings "skylogistics:$resource_id" "$wiki_dir/方块图鉴.md" "$wiki_dir/物品图鉴.md"; then
        echo "missing Wiki entry for skylogistics:$resource_id" >&2
        missing=1
    fi
done < <(
    sed -nE 's/^[[:space:]]*"(block|item)\.skylogistics\.([a-z0-9_]+)".*/\2/p' "$lang_file" |
        sort -u
)

while IFS= read -r link; do
    if [[ "$link" == *"|"* ]]; then
        target="${link#*|}"
    else
        target="$link"
    fi
    target="${target%%#*}"
    [[ -z "$target" ]] && continue
    if [[ ! -f "$wiki_dir/$target.md" ]]; then
        echo "broken Wiki link: [[$link]] (expected $target.md)" >&2
        missing=1
    fi
done < <(rg -o '\[\[[^]]+\]\]' "$wiki_dir" -g '*.md' | sed -E 's/.*\[\[([^]]+)\]\].*/\1/' | sort -u)

while IFS= read -r image_path; do
    if [[ ! -f "$wiki_dir/$image_path" ]]; then
        echo "broken Wiki image: $image_path" >&2
        missing=1
    fi
done < <(rg -o '\]\(images/[^)]+\)' "$wiki_dir" -g '*.md' | sed -E 's/.*\]\((images\/[^)]+)\).*/\1/' | sort -u)

if [[ "$missing" -ne 0 ]]; then
    exit 1
fi

echo "Wiki validation passed: all translated items/blocks are documented; page and image links resolve."
