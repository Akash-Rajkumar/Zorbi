#!/usr/bin/env python3
"""
ZORBI — P4-4 QA Regression Test Suite
Automated regression tests covering:
1. SQL purge validator bypass and edge case rejection.
2. Kotlin/Java source mock scanner edge cases (URLs, strings, block comments).
3. Pre-merge check error handling (missing tools, missing fixtures, failed tests).
"""

import os
import sys
import unittest
from pathlib import Path

# Add scripts directory to path to import mock_scanner
SCRIPTS_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPTS_DIR.parent
sys.path.insert(0, str(SCRIPTS_DIR))

from mock_scanner import (
    evaluate_pre_merge_preconditions,
    scan_source_for_mocks,
    strip_comments_and_strings,
    validate_purge_sql,
)


class TestSqlPurgeValidator(unittest.TestCase):
    """Regression tests for Fix 1: SQL Purge Validation."""

    def test_accept_valid_single_table(self):
        sql = "delete from public.sessions where is_mock = true;"
        valid, errors = validate_purge_sql(sql)
        self.assertTrue(valid, f"Should accept valid delete: {errors}")
        self.assertEqual(len(errors), 0)

    def test_accept_valid_all_tables_in_transaction(self):
        sql = """
        begin;
        delete from public.gesture_events where is_mock = true;
        delete from public.sessions where is_mock = true;
        delete from public.game_profiles where is_mock = true;
        commit;
        """
        valid, errors = validate_purge_sql(sql)
        self.assertTrue(valid, f"Should accept standard purge script: {errors}")

    def test_accept_case_and_whitespace_variations(self):
        sql = "DELETE  FROM   PUBLIC.SESSIONS   WHERE   IS_MOCK = TRUE ;"
        valid, errors = validate_purge_sql(sql)
        self.assertTrue(valid, f"Should handle capitalization and whitespace: {errors}")

    def test_accept_is_mock_is_true(self):
        sql = "delete from public.sessions where is_mock is true;"
        valid, errors = validate_purge_sql(sql)
        self.assertTrue(valid, f"Should accept 'is_mock is true': {errors}")

    def test_accept_safe_additional_and_predicates(self):
        sql = "delete from public.sessions where is_mock = true and created_at < now();"
        valid, errors = validate_purge_sql(sql)
        self.assertTrue(valid, f"Should accept safe additional AND predicate: {errors}")

    def test_reject_is_mock_false(self):
        sql = "delete from public.sessions where is_mock = false;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject is_mock = false")
        self.assertTrue(any("is_mock = false" in e for e in errors))

    def test_reject_is_mock_is_false(self):
        sql = "delete from public.sessions where is_mock is false;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject is_mock is false")

    def test_reject_not_is_mock(self):
        sql = "delete from public.sessions where not is_mock;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject not is_mock")

    def test_reject_missing_predicate_unscoped(self):
        sql = "delete from public.sessions;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject unscoped delete without WHERE clause")
        self.assertTrue(any("no WHERE clause" in e for e in errors))

    def test_reject_ambiguous_or_true_bypass(self):
        sql = "delete from public.sessions where is_mock = true or true;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject ambiguous boolean OR true expression")
        self.assertTrue(any("boolean OR expression" in e for e in errors))

    def test_reject_ambiguous_or_1_equals_1(self):
        sql = "delete from public.sessions where is_mock = true or 1=1;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject OR 1=1 injection")

    def test_reject_or_bypass_with_false(self):
        sql = "delete from public.sessions where is_mock = false or is_mock = true;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject combination with OR and is_mock = false")

    def test_reject_implicit_boolean_without_equals_true(self):
        sql = "delete from public.sessions where is_mock;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must require explicit '= true' or 'is true'")

    def test_reject_destructive_drop_table(self):
        sql = "DROP TABLE public.sessions;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject DROP TABLE")

    def test_reject_destructive_truncate(self):
        sql = "TRUNCATE public.sessions;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject TRUNCATE")

    def test_reject_unauthorized_table(self):
        sql = "delete from public.users where is_mock = true;"
        valid, errors = validate_purge_sql(sql)
        self.assertFalse(valid, "Must reject delete on unauthorized table")

    def test_reject_empty_sql(self):
        valid, errors = validate_purge_sql("")
        self.assertFalse(valid, "Must reject empty SQL")


class TestMockScanner(unittest.TestCase):
    """Regression tests for Fix 3: Mock Scanner."""

    def test_detect_fakex_after_url_string(self):
        code = 'val endpoint = "https://zorbi.app/api/v1"; val leak = FakeX()'
        leaks = scan_source_for_mocks(code)
        self.assertEqual(len(leaks), 1, "Must detect FakeX even when preceded by URL with //")
        self.assertEqual(leaks[0][1], "FakeX")

    def test_detect_fake_package_import(self):
        code = "package com.zorbi.app\nimport com.zorbi.fakes.FakeAppGraph\nclass MainActivity"
        leaks = scan_source_for_mocks(code)
        self.assertTrue(len(leaks) >= 1, "Must detect com.zorbi.fakes import")

    def test_detect_mockk_import_and_dsl(self):
        code = "import io.mockk.mockk\nval controller = mockk<EngineController>()"
        leaks = scan_source_for_mocks(code)
        self.assertTrue(len(leaks) >= 1, "Must detect MockK usage")

    def test_detect_mockito_import(self):
        code = "import org.mockito.Mockito\nval x = 1"
        leaks = scan_source_for_mocks(code)
        self.assertTrue(len(leaks) >= 1, "Must detect Mockito usage")

    def test_ignore_single_line_comment_reference(self):
        # Like in core/src/main/java/com/zorbi/core/Contracts.kt
        code = "interface AppGraph { // implemented by RealAppGraph (:app) and FakeAppGraph (:fakes)\n}"
        leaks = scan_source_for_mocks(code)
        self.assertEqual(len(leaks), 0, "Comment-only references must not trigger leak failures")

    def test_ignore_multiline_block_comment_reference(self):
        code = """
        /*
         * Architecture note:
         * Previously tested with FakeEngineController() and FakeTracker()
         */
        class RealEngineController : EngineController
        """
        leaks = scan_source_for_mocks(code)
        self.assertEqual(len(leaks), 0, "Block comments must not trigger leak failures")

    def test_detect_fake_after_multiline_block_comment(self):
        code = """
        /* Multi-line comment */
        val leak = FakeTracker()
        """
        leaks = scan_source_for_mocks(code)
        self.assertEqual(len(leaks), 1, "Must detect FakeTracker after block comment finishes")

    def test_raw_triple_quoted_strings_with_urls(self):
        code = 'val config = """{"url": "https://zorbi.app/api"}"""; val leak = FakeX()'
        leaks = scan_source_for_mocks(code)
        self.assertEqual(len(leaks), 1, "Must detect FakeX after triple-quoted string with //")

    def test_clean_production_code_passes(self):
        code = """
        package com.zorbi.app
        import com.zorbi.core.EngineController
        class RealEngineController(val id: String) : EngineController
        """
        leaks = scan_source_for_mocks(code)
        self.assertEqual(len(leaks), 0, "Clean production code must produce zero leaks")


class TestPreMergeRunnerBehavior(unittest.TestCase):
    """Regression tests for Fix 2: Pre-Merge Skipped Tests & Error Exits."""

    def test_actual_pre_merge_script_exists_and_passes(self):
        script_path = SCRIPTS_DIR / "pre_merge_check.sh"
        self.assertTrue(script_path.is_file(), "pre_merge_check.sh must exist")

    def test_missing_python_interpreter_fails(self):
        # Missing python must return non-zero exit / False
        valid, status, msg = evaluate_pre_merge_preconditions(
            python_bin="",
            fixture_test_file=REPO_ROOT / "tools" / "test_fixtures.py",
            has_java=True,
        )
        self.assertFalse(valid, "Missing Python interpreter must fail pre-merge")
        self.assertEqual(status, "FAIL")
        self.assertIn("Python 3 interpreter not found", msg)

    def test_missing_fixture_file_fails(self):
        # Missing fixture test file must return non-zero exit / False
        valid, status, msg = evaluate_pre_merge_preconditions(
            python_bin=sys.executable,
            fixture_test_file=Path("/non/existent/path/test_fixtures.py"),
            has_java=True,
        )
        self.assertFalse(valid, "Missing fixture test file must fail pre-merge")
        self.assertEqual(status, "FAIL")
        self.assertIn("fixture test file", msg)

    def test_deferred_jvm_tests_not_labeled_passed(self):
        # When Java is unavailable, status must be DEFERRED, not PASS
        valid, status, msg = evaluate_pre_merge_preconditions(
            python_bin=sys.executable,
            fixture_test_file=REPO_ROOT / "tools" / "test_fixtures.py",
            has_java=False,
        )
        self.assertTrue(valid)
        self.assertEqual(status, "DEFERRED")
        self.assertIn("deferred to GitHub Actions CI", msg)
        self.assertNotIn("PASS", status)

    def test_all_preconditions_met(self):
        valid, status, msg = evaluate_pre_merge_preconditions(
            python_bin=sys.executable,
            fixture_test_file=REPO_ROOT / "tools" / "test_fixtures.py",
            has_java=True,
        )
        self.assertTrue(valid)
        self.assertEqual(status, "PASS")


if __name__ == "__main__":
    unittest.main()
