#!/usr/bin/env bash
# Chay TRONG MAY AO. Dua code Claude da sua vao lai cay AOSP.
# Truoc khi ghi de, moi file dang co se duoc backup vao ~/aosp_backup_<thoigian>/.
#
# Cau truc goi patch (Claude se tao):
#   aosp_patch/FILES.txt     danh sach duong dan tuong doi, moi dong 1 file
#   aosp_patch/files/<path>  noi dung moi cua tung file
#
#   bash apply_to_aosp.sh <thu_muc_aosp_patch> [aosp_dir] [--dry-run]
#   bash apply_to_aosp.sh --restore <thu_muc_backup> [aosp_dir]   # hoan tac
set -euo pipefail

if [ "${1:-}" = "--restore" ]; then
    BK="${2:?thieu thu muc backup}"; AOSP="${3:-/home/vboxuser/aosp}"
    cd "$BK"
    # File moi (truoc do khong ton tai) duoc ghi trong NEW_FILES.txt -> xoa.
    if [ -f NEW_FILES.txt ]; then
        while IFS= read -r f; do [ -n "$f" ] && rm -f "$AOSP/$f" && echo "xoa  $f"; done < NEW_FILES.txt
    fi
    find . -type f ! -name NEW_FILES.txt -printf '%P\n' | while IFS= read -r f; do
        cp -p "$f" "$AOSP/$f" && echo "khoi phuc $f"
    done
    exit 0
fi

PATCH="${1:?thieu thu muc aosp_patch}"
AOSP="/home/vboxuser/aosp"; DRY=0
for a in "${@:2}"; do
    case "$a" in --dry-run) DRY=1 ;; *) AOSP="$a" ;; esac
done
[ -f "$PATCH/FILES.txt" ] || { echo "Khong thay $PATCH/FILES.txt"; exit 1; }
[ -d "$AOSP/frameworks/base" ] || { echo "Khong thay AOSP tai $AOSP"; exit 1; }

BK="$HOME/aosp_backup_$(date +%Y%m%d_%H%M%S)"
[ $DRY = 1 ] || mkdir -p "$BK"

while IFS= read -r f; do
    f="${f%$'\r'}"; [ -z "$f" ] && continue
    case "$f" in /*|*..*) echo "Bo qua duong dan khong hop le: $f"; continue ;; esac
    src="$PATCH/files/$f"; dst="$AOSP/$f"
    [ -f "$src" ] || { echo "THIEU trong goi patch: $f"; exit 1; }
    if [ -e "$dst" ]; then
        if cmp -s "$src" "$dst"; then echo "giong  $f"; continue; fi
        echo "sua   $f"
        [ $DRY = 1 ] || { mkdir -p "$BK/$(dirname "$f")"; cp -p "$dst" "$BK/$f"; }
    else
        echo "moi   $f"
        [ $DRY = 1 ] || echo "$f" >> "$BK/NEW_FILES.txt"
    fi
    [ $DRY = 1 ] || { mkdir -p "$(dirname "$dst")"; cp "$src" "$dst"; }
done < "$PATCH/FILES.txt"

if [ $DRY = 1 ]; then
    echo "(dry-run: chua ghi gi)"
else
    echo "Xong. Backup: $BK"
    echo "Hoan tac: bash $0 --restore $BK $AOSP"
fi
