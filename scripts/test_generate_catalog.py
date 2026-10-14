"""Regression checks for catalog verification, without network or Gradle execution."""

import importlib.util
import subprocess
import unittest
from contextlib import ExitStack, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import Mock, patch


SPEC = importlib.util.spec_from_file_location("generate_catalog", Path(__file__).with_name("generate-catalog.py"))
catalog = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(catalog)


class CatalogChecksTest(unittest.TestCase):
    def test_windows_uses_studio_jdk_even_when_java_home_is_set(self):
        root = Path.cwd()
        with (
            patch.object(catalog, "Path", return_value=root),
            patch.object(catalog.os, "name", "nt"),
            patch.dict(catalog.os.environ, {"JAVA_HOME": "other-jdk"}),
            patch.object(Path, "is_file", return_value=True),
            patch.object(catalog.subprocess, "run", return_value=Mock(returncode=0)) as run,
            redirect_stdout(StringIO()),
        ):
            catalog.run_checks(root)

        self.assertEqual(catalog.DEFAULT_JAVA_HOME, run.call_args.kwargs["env"]["JAVA_HOME"])
        self.assertEqual(catalog.CHECK_TIMEOUT_SECONDS, run.call_args.kwargs["timeout"])
        self.assertEqual(catalog.GRADLE_TASKS, run.call_args.args[0][1:-1])

    def test_java_home_is_kept_when_studio_is_missing(self):
        root = Path.cwd()
        with (
            patch.object(catalog, "Path", return_value=root),
            patch.object(catalog.os, "name", "nt"),
            patch.dict(catalog.os.environ, {"JAVA_HOME": "custom-jdk"}),
            patch.object(Path, "is_file", return_value=False),
            patch.object(catalog.subprocess, "run", return_value=Mock(returncode=0)) as run,
            redirect_stdout(StringIO()),
        ):
            catalog.run_checks(root)

        self.assertEqual("custom-jdk", run.call_args.kwargs["env"]["JAVA_HOME"])

    def test_failed_checks_are_reported(self):
        with (
            patch.object(catalog.subprocess, "run", return_value=Mock(returncode=1)),
            redirect_stdout(StringIO()),
            self.assertRaisesRegex(SystemExit, "vérifications ont échoué"),
        ):
            catalog.run_checks(Path.cwd())

    def test_timed_out_checks_are_reported(self):
        with (
            patch.object(catalog.subprocess, "run", side_effect=subprocess.TimeoutExpired("gradle", 1200)),
            redirect_stdout(StringIO()),
            self.assertRaisesRegex(SystemExit, "dépassé 20 minutes"),
        ):
            catalog.run_checks(Path.cwd())

    def test_unchanged_catalog_retries_checks_without_rewriting_files(self):
        self.check_unchanged_catalog(no_verify=False)

    def test_no_verify_skips_checks_for_unchanged_catalog(self):
        self.check_unchanged_catalog(no_verify=True)

    def check_unchanged_catalog(self, no_verify):
        source = Mock()
        source.read_text.return_value = "{}"
        source.stat.return_value.st_mtime = 0
        arguments = ["generate-catalog.py", "--offline"]
        if no_verify:
            arguments.append("--no-verify")
        with ExitStack() as stack:
            stack.enter_context(patch.object(catalog.sys, "argv", arguments))
            stack.enter_context(patch.object(catalog, "download", return_value=source))
            stack.enter_context(patch.object(catalog, "read_synonyms", return_value=dict.fromkeys(catalog.LANGUAGES, {})))
            stack.enter_context(patch.object(catalog, "current_version", return_value=4))
            stack.enter_context(patch.object(catalog, "build", return_value=[]))
            stack.enter_context(patch.object(catalog, "published_products", return_value=[]))
            checks = stack.enter_context(patch.object(catalog, "run_checks"))
            writes = stack.enter_context(patch.object(catalog, "write"))
            version = stack.enter_context(patch.object(catalog, "set_version"))
            notes = stack.enter_context(patch.object(catalog, "add_release_note"))
            stack.enter_context(redirect_stdout(StringIO()))
            catalog.main()

        self.assertEqual(0 if no_verify else 1, checks.call_count)
        writes.assert_not_called()
        version.assert_not_called()
        notes.assert_not_called()


if __name__ == "__main__":
    unittest.main()
