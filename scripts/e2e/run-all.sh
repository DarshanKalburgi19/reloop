#!/usr/bin/env bash
# Runs the four end-to-end scenarios against BASE_URL (default http://localhost:8080). Exit code = failures.
set -uo pipefail
dir="$(cd "$(dirname "$0")" && pwd)"
failed=0
for s in "$dir"/0*.sh; do
  bash "$s" || failed=$((failed + 1))
done
echo
if [[ $failed -eq 0 ]]; then echo "All e2e scenarios passed."; else echo "$failed scenario(s) failed."; fi
exit $failed
