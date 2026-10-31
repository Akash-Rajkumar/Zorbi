#!/usr/bin/env bash
# ==============================================================================
# ZORBI — Pre-Merge QA Gate Runner (P4-4 / Gate T-P4-4)
# Comprehensive pre-merge validation runner enforcing code hygiene,
# mock absence, fixture integrity, whitespace checks, and test suites.
# ==============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

OVERALL_STATUS=0
SUMMARY_LINES=()

log_summary() {
  SUMMARY_LINES+=("$1")
}

echo "================================================================="
echo "ZORBI PRE-MERGE QA VERIFICATION RUNNER"
echo "Repository: Zorbi"
echo "Working Directory: $REPO_ROOT"
echo "================================================================="

# ------------------------------------------------------------------------------
# 1. Mock Purge and Leakage Check
# ------------------------------------------------------------------------------
echo ""
echo "[Step 1/6] Running Mock Purge & Production Leakage Check..."
if bash "$REPO_ROOT/scripts/mock_purge_check.sh"; then
  log_summary "  [PASS] Step 1: Mock Purge & Leakage Check"
else
  log_summary "  [FAIL] Step 1: Mock Purge & Leakage Check"
  OVERALL_STATUS=1
fi

# ------------------------------------------------------------------------------
# 2. Git Whitespace and Formatting Check
# ------------------------------------------------------------------------------
echo ""
echo "[Step 2/6] Checking Git Whitespace and Formatting..."
if git diff --check > /dev/null 2>&1; then
  echo "  [PASS] git diff --check clean (no whitespace or conflict marker errors)."
  log_summary "  [PASS] Step 2: Git Whitespace & Format Check"
else
  echo "  [FAIL] git diff --check detected formatting/whitespace errors:"
  git diff --check
  log_summary "  [FAIL] Step 2: Git Whitespace & Format Check"
  OVERALL_STATUS=1
fi

# ------------------------------------------------------------------------------
# 3. Forbidden and Protected File Safeguards
# ------------------------------------------------------------------------------
echo ""
echo "[Step 3/6] Checking Protected Files and Secret Boundaries..."
SAFEGUARD_ERRORS=0

# Ensure P4-1-final-diff.txt is not staged
if git diff --cached --name-only | grep -E '^P4-1-final-diff\.txt$' > /dev/null 2>&1; then
  echo "  [FAIL] P4-1-final-diff.txt is staged. It must remain untracked."
  SAFEGUARD_ERRORS=$((SAFEGUARD_ERRORS + 1))
fi

# Ensure no build bytecode or caches are staged
if git diff --cached --name-only | grep -E '(__pycache__|\.pyc$|\.class$|\.hprof$)' > /dev/null 2>&1; then
  echo "  [FAIL] Staged build bytecode or cache artifacts detected:"
  git diff --cached --name-only | grep -E '(__pycache__|\.pyc$|\.class$|\.hprof$)'
  SAFEGUARD_ERRORS=$((SAFEGUARD_ERRORS + 1))
fi

# Ensure no secret files are staged
if git diff --cached --name-only | grep -E '(\.env$|\.env\.local$|^local\.properties$)' > /dev/null 2>&1; then
  echo "  [FAIL] Potential secrets or local environment files staged:"
  git diff --cached --name-only | grep -E '(\.env$|\.env\.local$|^local\.properties$)'
  SAFEGUARD_ERRORS=$((SAFEGUARD_ERRORS + 1))
fi

if [[ $SAFEGUARD_ERRORS -eq 0 ]]; then
  echo "  [PASS] Protected files and staging boundaries clean."
  log_summary "  [PASS] Step 3: Protected File & Secret Safeguards"
else
  log_summary "  [FAIL] Step 3: Protected File & Secret Safeguards"
  OVERALL_STATUS=1
fi

# ------------------------------------------------------------------------------
# 4. Python Tools & Fixture Verification (P4-3 Fixture Suite)
# ------------------------------------------------------------------------------
echo ""
echo "[Step 4/6] Running Python Tool & Fixture Verification (P4-3 Fixture Suite)..."
PYTHON_BIN=""
for candidate in python3 python py "/c/Program Files/Python314/python.exe" "/c/Program Files/Python313/python.exe" "/c/Program Files/Python312/python.exe"; do
  if command -v "$candidate" > /dev/null 2>&1 || [[ -f "$candidate" ]]; then
    if "$candidate" --version > /dev/null 2>&1; then
      PYTHON_BIN="$candidate"
      break
    fi
  fi
done

FIXTURES_TEST_SCRIPT="$REPO_ROOT/tools/test_fixtures.py"
if [[ -z "$PYTHON_BIN" ]]; then
  echo "  [FAIL] Required Python 3 interpreter not found in environment."
  log_summary "  [FAIL] Step 4: Python Tools & Fixture Suite (Python interpreter missing)"
  OVERALL_STATUS=1
elif [[ ! -f "$FIXTURES_TEST_SCRIPT" ]]; then
  echo "  [FAIL] Required fixture test file '$FIXTURES_TEST_SCRIPT' not found."
  log_summary "  [FAIL] Step 4: Python Tools & Fixture Suite (test_fixtures.py missing)"
  OVERALL_STATUS=1
else
  echo "  Using Python interpreter: $PYTHON_BIN"
  if "$PYTHON_BIN" "$FIXTURES_TEST_SCRIPT"; then
    echo "  [PASS] Python tools and 5 fixture validations passed."
    log_summary "  [PASS] Step 4: Python Tools & Fixture Suite (P4-3)"
  else
    echo "  [FAIL] Python test_fixtures.py failed."
    log_summary "  [FAIL] Step 4: Python Tools & Fixture Suite (P4-3)"
    OVERALL_STATUS=1
  fi
fi

# ------------------------------------------------------------------------------
# 5. P4-4 QA Regression Test Suite
# ------------------------------------------------------------------------------
echo ""
echo "[Step 5/6] Running P4-4 QA Regression Test Suite..."
QA_TEST_SCRIPT="$REPO_ROOT/scripts/test_p4_4_qa.py"
if [[ -z "$PYTHON_BIN" ]]; then
  echo "  [FAIL] Required Python 3 interpreter not found in environment."
  log_summary "  [FAIL] Step 5: P4-4 QA Regression Suite (Python interpreter missing)"
  OVERALL_STATUS=1
elif [[ ! -f "$QA_TEST_SCRIPT" ]]; then
  echo "  [FAIL] Required QA regression test file '$QA_TEST_SCRIPT' not found."
  log_summary "  [FAIL] Step 5: P4-4 QA Regression Suite (test_p4_4_qa.py missing)"
  OVERALL_STATUS=1
else
  echo "  Executing: $PYTHON_BIN $QA_TEST_SCRIPT -v"
  if "$PYTHON_BIN" "$QA_TEST_SCRIPT" -v; then
    echo "  [PASS] All P4-4 QA regression tests passed."
    log_summary "  [PASS] Step 5: P4-4 QA Regression Suite"
  else
    echo "  [FAIL] P4-4 QA regression tests failed."
    log_summary "  [FAIL] Step 5: P4-4 QA Regression Suite"
    OVERALL_STATUS=1
  fi
fi

# ------------------------------------------------------------------------------
# 6. JVM / Gradle Unit Tests
# ------------------------------------------------------------------------------
echo ""
echo "[Step 6/6] Checking JVM Unit Test Environment..."
if command -v java > /dev/null 2>&1 || [[ -n "${JAVA_HOME:-}" ]]; then
  echo "  Executing ./gradlew :fakes:testDebugUnitTest..."
  if ./gradlew :fakes:testDebugUnitTest; then
    echo "  [PASS] Gradle :fakes:testDebugUnitTest passed."
    log_summary "  [PASS] Step 6: JVM Unit Tests (:fakes)"
  else
    echo "  [FAIL] Gradle :fakes:testDebugUnitTest failed."
    log_summary "  [FAIL] Step 6: JVM Unit Tests (:fakes)"
    OVERALL_STATUS=1
  fi
else
  echo "  [INFO] JDK / java not configured in local environment."
  echo "         JVM tests are explicitly deferred to GitHub Actions CI."
  log_summary "  [DEFERRED] Step 6: JVM Unit Tests (deferred to GitHub Actions CI)"
fi

# ------------------------------------------------------------------------------
# Final Pre-Merge QA Summary
# ------------------------------------------------------------------------------
echo ""
echo "================================================================="
echo "PRE-MERGE QA VERIFICATION SUMMARY"
echo "================================================================="
for line in "${SUMMARY_LINES[@]}"; do
  echo "$line"
done
echo "================================================================="

if [[ $OVERALL_STATUS -eq 0 ]]; then
  echo "OVERALL VERDICT: READY FOR MERGE (Exit Code: 0)"
  echo "================================================================="
  exit 0
else
  echo "OVERALL VERDICT: BLOCKED - REMEDIATION REQUIRED (Exit Code: 1)"
  echo "================================================================="
  exit 1
fi
