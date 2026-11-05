#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$project_dir/target"
classes_dir=$(mktemp -d "$project_dir/target/core-check.XXXXXX")
trap 'rm -rf "$classes_dir"' EXIT

if command -v javac >/dev/null 2>&1; then
  compiler=javac
else
  compiler='java com.sun.tools.javac.Main'
fi

$compiler -d "$classes_dir" \
  "$project_dir/src/main/java/io/flowforge/engine/DirectedAcyclicGraph.java" \
  "$project_dir/src/main/java/io/flowforge/engine/BackoffPolicy.java" \
  "$project_dir/scripts/CoreSmokeCheck.java"

java -cp "$classes_dir" io.flowforge.engine.CoreSmokeCheck
