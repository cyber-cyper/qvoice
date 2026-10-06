#!/usr/bin/env bash
# Downloads the API signature files the stubs follow (Compose 1.9.0,
# Material3 1.3.2, lifecycle 2.9.2, material-icons-core 1.7.x, activity 1.10)
# from JetBrains' Compose fork, which mirrors AndroidX's own api/current.txt
# files per release. Google Maven is unreachable from the authoring sandbox;
# GitHub is not.   Usage: bash fetch_api.sh <folder>
set -euo pipefail
OUT=${1:?usage: fetch_api.sh <folder>}
mkdir -p "$OUT"
B=https://raw.githubusercontent.com/JetBrains/compose-multiplatform-core
while read -r name tag path; do
  curl -fsS --max-time 60 -o "$OUT/$name" "$B/$tag/$path/api/current.txt"
done <<'LIST'
m3.txt v1.8.2 compose/material3/material3
foundation.txt v1.9.0 compose/foundation/foundation
layout.txt v1.9.0 compose/foundation/foundation-layout
ui.txt v1.9.0 compose/ui/ui
ui-graphics.txt v1.9.0 compose/ui/ui-graphics
ui-unit.txt v1.9.0 compose/ui/ui-unit
ui-geometry.txt v1.9.0 compose/ui/ui-geometry
ui-text.txt v1.9.0 compose/ui/ui-text
runtime.txt v1.9.0 compose/runtime/runtime
saveable.txt v1.9.0 compose/runtime/runtime-saveable
lifecycle-runtime-compose.txt v1.9.0 lifecycle/lifecycle-runtime-compose
vmcompose.txt v1.9.0 lifecycle/lifecycle-viewmodel-compose
icons.txt v1.7.3 compose/material/material-icons-core
activity.txt v1.9.0 activity/activity
activity-compose.txt v1.9.0 activity/activity-compose
LIST
echo "API files in $OUT"
