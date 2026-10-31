#!/usr/bin/env bash
# ==============================================================================
# ZORBI — Mock Purge & Production Leakage Check (P4-4 / Gate T-P4-4)
# Verifies that no test fakes or mocks leak into production code sets,
# and verifies the safety and integrity of database mock purge scripts.
# ==============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Discover Python 3 interpreter
PYTHON_BIN=""
for candidate in python3 python py "/c/Program Files/Python314/python.exe" "/c/Program Files/Python313/python.exe" "/c/Program Files/Python312/python.exe"; do
  if command -v "$candidate" > /dev/null 2>&1 || [[ -f "$candidate" ]]; then
    if "$candidate" --version > /dev/null 2>&1; then
      PYTHON_BIN="$candidate"
      break
    fi
  fi
done

if [[ -z "$PYTHON_BIN" ]]; then
  echo "================================================================="
  echo "ZORBI MOCK PURGE & LEAKAGE CHECK (T-P4-4)"
  echo "================================================================="
  echo "  [FAIL] Required Python 3 interpreter not found in environment."
  echo "================================================================="
  echo "RESULTS: FAIL (Missing Python interpreter)"
  echo "================================================================="
  exit 1
fi

SCANNER_PY="$SCRIPT_DIR/mock_scanner.py"
if [[ ! -f "$SCANNER_PY" ]]; then
  echo "================================================================="
  echo "ZORBI MOCK PURGE & LEAKAGE CHECK (T-P4-4)"
  echo "================================================================="
  echo "  [FAIL] Missing mock scanner engine: $SCANNER_PY"
  echo "================================================================="
  echo "RESULTS: FAIL (Missing scanner script)"
  echo "================================================================="
  exit 1
fi

exec "$PYTHON_BIN" "$SCANNER_PY" "$@"
