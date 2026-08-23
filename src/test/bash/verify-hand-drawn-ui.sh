#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "$0")/../../.." && pwd)
static_dir="$root_dir/src/main/resources/static"
base_css="$static_dir/css/base.css"

require() {
  local pattern=$1
  local file=$2
  if ! rg -q --fixed-strings -- "$pattern" "$file"; then
    echo "Missing expected hand-drawn contract: $pattern in $file" >&2
    exit 1
  fi
}

require_regex() {
  local pattern=$1
  local file=$2
  if ! rg -q -- "$pattern" "$file"; then
    echo "Missing expected hand-drawn token: $pattern in $file" >&2
    exit 1
  fi
}

require_regex '--color-bg:[[:space:]]*#fdfbf7' "$base_css"
require_regex '--color-fg:[[:space:]]*#2d2d2d' "$base_css"
require_regex '--color-accent:[[:space:]]*#ff4d4d' "$base_css"
require_regex '--color-secondary:[[:space:]]*#2d5da1' "$base_css"
require_regex '--radius-wobbly:[[:space:]]*255px 15px 225px 15px' "$base_css"
require "family=Kalam" "$base_css"
require "family=Patrick+Hand" "$base_css"
require 'background-color: var(--color-bg)' "$static_dir/css/login.css"
require 'background: var(--color-bg)' "$static_dir/draw.html"
require 'border-radius: var(--radius-wobbly)' "$static_dir/activities-list.html"
require 'clip-path: polygon(' "$static_dir/admin.html"
require 'background-color: var(--color-muted)' "$static_dir/admin.html"

echo "Hand-drawn static UI contract verified."
