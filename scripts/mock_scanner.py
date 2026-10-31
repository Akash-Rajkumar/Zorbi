#!/usr/bin/env python3
"""
ZORBI — Mock Purge & Production Leakage Scanner Engine (P4-4 / Gate T-P4-4)
Provides lexical scanning of Kotlin/Java production sources and rigorous SQL purge validation.
Standard-library only; no external dependencies.
"""

import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


# -----------------------------------------------------------------------------
# Part 1: Kotlin/Java Source Lexer & Comment Stripper
# -----------------------------------------------------------------------------
def strip_comments_and_strings(content: str) -> List[Tuple[int, str]]:
    """
    Lexically analyzes Kotlin/Java source code.
    Strips single-line (//) and multi-line (/* ... */) comments while properly
    respecting string literals ("..." and \"""...\""") and character literals ('...').
    Any '//' or '/*' inside a string literal (e.g. 'https://zorbi.app/api') is preserved
    and NEVER treated as a comment boundary.

    Returns a list of (line_number, cleaned_code_line) where string literals are
    replaced by inert whitespace and comments are removed.
    """
    lines = content.splitlines()
    cleaned_lines: List[Tuple[int, str]] = []

    STATE_NORMAL = 0
    STATE_STRING = 1
    STATE_RAW_STRING = 2
    STATE_CHAR = 3
    STATE_BLOCK_COMMENT = 4

    state = STATE_NORMAL
    i = 0
    n = len(content)
    line_num = 1
    current_line_chars: List[str] = []

    while i < n:
        c = content[i]

        if c == '\n':
            cleaned_lines.append((line_num, "".join(current_line_chars)))
            current_line_chars = []
            line_num += 1
            i += 1
            continue
        elif c == '\r':
            i += 1
            continue

        if state == STATE_NORMAL:
            if content[i : i + 3] == '"""':
                state = STATE_RAW_STRING
                current_line_chars.extend([' ', ' ', ' '])
                i += 3
            elif content[i : i + 2] == '//':
                while i < n and content[i] != '\n':
                    i += 1
                continue
            elif content[i : i + 2] == '/*':
                state = STATE_BLOCK_COMMENT
                i += 2
            elif c == '"':
                state = STATE_STRING
                current_line_chars.append(' ')
                i += 1
            elif c == "'":
                state = STATE_CHAR
                current_line_chars.append(' ')
                i += 1
            else:
                current_line_chars.append(c)
                i += 1

        elif state == STATE_STRING:
            if c == '\\':
                current_line_chars.append(' ')
                i += 2
            elif c == '"':
                state = STATE_NORMAL
                current_line_chars.append(' ')
                i += 1
            else:
                current_line_chars.append(' ')
                i += 1

        elif state == STATE_CHAR:
            if c == '\\':
                current_line_chars.append(' ')
                i += 2
            elif c == "'":
                state = STATE_NORMAL
                current_line_chars.append(' ')
                i += 1
            else:
                current_line_chars.append(' ')
                i += 1

        elif state == STATE_RAW_STRING:
            if content[i : i + 3] == '"""':
                state = STATE_NORMAL
                current_line_chars.extend([' ', ' ', ' '])
                i += 3
            else:
                current_line_chars.append(' ')
                i += 1

        elif state == STATE_BLOCK_COMMENT:
            if content[i : i + 2] == '*/':
                state = STATE_NORMAL
                i += 2
            else:
                i += 1

    if current_line_chars:
        cleaned_lines.append((line_num, "".join(current_line_chars)))

    return cleaned_lines


FORBIDDEN_SOURCE_PATTERNS = [
    (re.compile(r'\bFake[A-Z0-9_]+'), "Reference to fake test double class"),
    (re.compile(r'\bcom\.zorbi\.fakes\b'), "Import or reference to com.zorbi.fakes package"),
    (re.compile(r'\bio\.mockk\b'), "Import or reference to MockK library"),
    (re.compile(r'\borg\.mockito\b'), "Import or reference to Mockito library"),
    (re.compile(r'\borg\.easymock\b'), "Import or reference to EasyMock library"),
    (re.compile(r'\borg\.powermock\b'), "Import or reference to PowerMock library"),
    (re.compile(r'\bmockk<|\bmockkObject\b'), "MockK stubbing or verification method"),
    (re.compile(r'\bevery\s*\{|\bverify\s*\{'), "Mocking DSL block"),
]


def scan_source_for_mocks(content: str) -> List[Tuple[int, str, str]]:
    """
    Scans Kotlin/Java source text for mock/fake leakages.
    Returns list of (line_number, match_text, reason).
    """
    leaks: List[Tuple[int, str, str]] = []
    cleaned_lines = strip_comments_and_strings(content)

    for line_num, code in cleaned_lines:
        if not code.strip():
            continue
        for pattern, reason in FORBIDDEN_SOURCE_PATTERNS:
            m = pattern.search(code)
            if m:
                leaks.append((line_num, m.group(0), reason))
                break

    return leaks


# -----------------------------------------------------------------------------
# Part 2: SQL Purge Validator
# -----------------------------------------------------------------------------
ALLOWED_PURGE_TABLES = {
    "public.gesture_events",
    "public.sessions",
    "public.game_profiles",
    "gesture_events",
    "sessions",
    "game_profiles",
}


def validate_purge_sql(sql_content: str) -> Tuple[bool, List[str]]:
    """
    Validates supabase/purge_mocks.sql against strict safety requirements:
    1. Rejects missing or empty content.
    2. Rejects destructive DDL (DROP, TRUNCATE, ALTER, CREATE).
    3. Rejects non-purge DML (INSERT, UPDATE).
    4. Allows transaction control (BEGIN, COMMIT).
    5. For every DELETE FROM statement:
       - Must target an allowed mock table.
       - Must contain a WHERE clause.
       - Must NOT contain boolean OR expressions (rejects 'OR true', 'OR 1=1', etc.).
       - Must NOT contain 'is_mock = false' or 'is_mock is false' or 'not is_mock'.
       - Must require explicit 'is_mock = true' or 'is_mock is true'.
       - Allows safe additional AND predicates (e.g. 'created_at < now()').
       - Handles whitespace and case-insensitivity.
       - Rejects unparseable or ambiguous syntax.

    Returns (is_valid, list_of_error_messages).
    """
    errors: List[str] = []

    if not sql_content or not sql_content.strip():
        return False, ["Purge SQL content is empty or missing."]

    # Strip SQL single-line and multi-line comments
    no_line_comments = re.sub(r'--.*?$', '', sql_content, flags=re.MULTILINE)
    clean_sql = re.sub(r'/\*.*?\*/', '', no_line_comments, flags=re.DOTALL)

    # Check for destructive DDL keywords
    ddl_match = re.search(r'\b(DROP\s+TABLE|DROP\s+DATABASE|TRUNCATE|ALTER\s+TABLE)\b', clean_sql, re.IGNORECASE)
    if ddl_match:
        errors.append(f"Destructive DDL keyword '{ddl_match.group(0)}' is forbidden.")

    raw_statements = clean_sql.split(';')
    delete_statements_found = 0

    for stmt in raw_statements:
        trimmed = stmt.strip()
        if not trimmed:
            continue

        if re.match(r'^(BEGIN|START\s+TRANSACTION|COMMIT|END)(\s+TRANSACTION)?$', trimmed, re.IGNORECASE):
            continue

        if re.match(r'^(INSERT\s+INTO|UPDATE|CREATE|SELECT)\b', trimmed, re.IGNORECASE):
            errors.append(f"Statement type forbidden in mock purge script: {trimmed[:40]}...")
            continue

        delete_match = re.match(
            r'^DELETE\s+FROM\s+([a-zA-Z0-9_\.]+)(?:\s+(?:AS\s+)?([a-zA-Z0-9_]+))?(?:\s+WHERE\s+(.+))?$',
            trimmed,
            re.IGNORECASE | re.DOTALL,
        )

        if not delete_match:
            errors.append(f"Unrecognized or malformed SQL statement: {trimmed[:60]}")
            continue

        delete_statements_found += 1
        table_name = delete_match.group(1).strip().lower()
        where_clause = delete_match.group(3)

        if table_name not in ALLOWED_PURGE_TABLES:
            errors.append(f"DELETE targets unauthorized table '{table_name}'.")

        if not where_clause or not where_clause.strip():
            errors.append(f"DELETE statement on '{table_name}' has no WHERE clause (unscoped delete).")
            continue

        pred = where_clause.strip()
        pred_collapsed = re.sub(r'\s+', ' ', pred)

        if re.search(r'\bOR\b', pred_collapsed, re.IGNORECASE):
            errors.append(
                f"Ambiguous or unsafe boolean OR expression in WHERE clause on '{table_name}': {pred_collapsed}"
            )

        if re.search(r'\bis_mock\s*=\s*false\b|\bis_mock\s+is\s+false\b|\bnot\s+is_mock\b', pred_collapsed, re.IGNORECASE):
            errors.append(
                f"Forbidden predicate 'is_mock = false' detected in WHERE clause on '{table_name}'."
            )

        and_clauses = re.split(r'\bAND\b', pred_collapsed, flags=re.IGNORECASE)
        has_mock_true = False

        for clause in and_clauses:
            clause_clean = clause.strip()
            if re.match(r'^(?:[a-zA-Z0-9_\.]+\.)?is_mock\s*=\s*true$', clause_clean, re.IGNORECASE) or \
               re.match(r'^(?:[a-zA-Z0-9_\.]+\.)?is_mock\s+is\s+true$', clause_clean, re.IGNORECASE):
                has_mock_true = True
            elif not clause_clean:
                errors.append(f"Empty predicate clause in WHERE on '{table_name}'.")

        if not has_mock_true:
            errors.append(
                f"DELETE statement on '{table_name}' missing mandatory explicit 'is_mock = true' predicate: {pred_collapsed}"
            )

    if delete_statements_found == 0 and not errors:
        errors.append("Purge SQL script contains no DELETE statements.")

    return len(errors) == 0, errors


# -----------------------------------------------------------------------------
# Part 3: Pre-Merge Condition Evaluation
# -----------------------------------------------------------------------------
def evaluate_pre_merge_preconditions(
    python_bin: Optional[str],
    fixture_test_file: Path,
    has_java: bool,
) -> Tuple[bool, str, str]:
    """
    Evaluates pre-merge runner preconditions per Fix 2:
    - Missing python: returns (False, 'FAIL', error_message)
    - Missing fixture test file: returns (False, 'FAIL', error_message)
    - JVM tests unavailable locally: returns (True, 'DEFERRED', info_message)
    """
    if not python_bin or not str(python_bin).strip():
        return False, "FAIL", "Required Python 3 interpreter not found in environment."
    if not fixture_test_file.is_file():
        return False, "FAIL", f"Required fixture test file '{fixture_test_file}' not found."
    if not has_java:
        return True, "DEFERRED", "Step 5: JVM Unit Tests (deferred to GitHub Actions CI)"
    return True, "PASS", "Step 5: JVM Unit Tests (ready for local execution)"


# -----------------------------------------------------------------------------
# Part 4: CLI Runner for Repository Checks
# -----------------------------------------------------------------------------
def run_repo_checks(repo_root: Path) -> int:
    """
    Executes all mock purge checks across the repository:
    1. Production code mock leakage scan
    2. Build dependency wiring check
    3. Supabase mock purge SQL validation
    Returns exit code (0 = all pass, 1 = failure).
    """
    failures = 0

    print("=================================================================")
    print("ZORBI MOCK PURGE & LEAKAGE CHECK (T-P4-4)")
    print(f"Repository Root: {repo_root}")
    print("=================================================================")

    # 1. Production Code Scan
    print("\n--- Check 1: Production Code Mock Leakage ---")
    prod_files: List[Path] = []
    for root, dirs, files in os.walk(repo_root):
        dirs[:] = [d for d in dirs if d not in {".git", ".gradle", "build"}]
        p_root = Path(root)
        parts = p_root.parts
        if "src" in parts and "main" in parts and "fakes" not in parts:
            for f in files:
                if f.endswith(".kt") or f.endswith(".java"):
                    prod_files.append(p_root / f)

    prod_leak_count = 0
    for fpath in prod_files:
        try:
            content = fpath.read_text(encoding="utf-8")
        except Exception as e:
            print(f"  [FAIL] Could not read file {fpath}: {e}")
            failures += 1
            continue

        leaks = scan_source_for_mocks(content)
        if leaks:
            rel = fpath.relative_to(repo_root)
            print(f"  [FAIL] Leak detected in {rel}:")
            for line_no, match_txt, reason in leaks:
                print(f"         Line {line_no}: '{match_txt}' — {reason}")
            prod_leak_count += len(leaks)

    if prod_leak_count == 0:
        print(f"  [PASS] Clean production code: No fakes or mocks detected across {len(prod_files)} production source files.")
    else:
        print(f"  [FAIL] Total production mock leakages detected: {prod_leak_count}")
        failures += 1

    # 2. Dependency Wiring Check
    print("\n--- Check 2: Production Dependency Configuration ---")
    dep_leak_count = 0
    for root, dirs, files in os.walk(repo_root):
        dirs[:] = [d for d in dirs if d not in {".git", ".gradle", "build", "fakes"}]
        for f in files:
            if f == "build.gradle.kts":
                bf = Path(root) / f
                content = bf.read_text(encoding="utf-8")
                for line_no, line in enumerate(content.splitlines(), start=1):
                    if re.match(r'^\s*(implementation|api)\(project\(":fakes"\)\)', line):
                        rel = bf.relative_to(repo_root)
                        print(f"  [FAIL] Production dependency leak in {rel}:{line_no}")
                        print(f"         {line.strip()}")
                        dep_leak_count += 1

    if dep_leak_count == 0:
        print("  [PASS] Clean dependency wiring: :fakes is not exposed in production configurations.")
    else:
        print(f"  [FAIL] Total dependency leaks: {dep_leak_count}")
        failures += 1

    # 3. Supabase Mock Purge SQL Safety Check
    print("\n--- Check 3: Supabase Mock Purge SQL Safety ---")
    sql_path = repo_root / "supabase" / "purge_mocks.sql"
    if not sql_path.is_file():
        print(f"  [FAIL] Missing purge SQL: {sql_path} does not exist.")
        failures += 1
    else:
        try:
            sql_text = sql_path.read_text(encoding="utf-8")
            is_valid, sql_errors = validate_purge_sql(sql_text)
            if is_valid:
                print("  [PASS] Purge SQL safety verified: deletes strictly scoped to is_mock = true.")
            else:
                print(f"  [FAIL] Purge SQL validation failed in {sql_path}:")
                for err in sql_errors:
                    print(f"         {err}")
                failures += 1
        except Exception as e:
            print(f"  [FAIL] Error validating {sql_path}: {e}")
            failures += 1

    print("\n=================================================================")
    if failures == 0:
        print("RESULTS: PASS (All mock purge and production leakage checks passed)")
        print("=================================================================")
        return 0
    else:
        print(f"RESULTS: FAIL ({failures} check group(s) failed)")
        print("=================================================================")
        return 1


if __name__ == "__main__":
    root = Path(__file__).resolve().parent.parent
    sys.exit(run_repo_checks(root))
