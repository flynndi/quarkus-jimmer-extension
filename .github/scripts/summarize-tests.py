#!/usr/bin/env python3
"""Append a summary of Maven test reports to the GitHub Actions job summary."""

import html
import os
from collections import defaultdict
from decimal import Decimal, InvalidOperation
from pathlib import Path
import re
import xml.etree.ElementTree as ET


MAX_FAILURES = 20
MAX_DIAGNOSTICS = 10
METRICS = ("tests", "failures", "errors", "skipped", "time")


def markdown(value, limit=400):
    text = " ".join(str(value).split())
    if len(text) > limit:
        text = text[:limit] + "…"
    return re.sub(r"([\\`*_{}\[\]()#+.!|>~-])", r"\\\1", html.escape(text, quote=False))


def seconds(value):
    number = Decimal(value)
    if not number.is_finite() or number < 0:
        raise ValueError(f"Invalid report time: {value!r}")
    return number


def testcase_status(testcase):
    for status in ("error", "failure", "skipped"):
        if testcase.find(status) is not None:
            return status
    return "passed"


def read_report(path):
    root = ET.parse(path).getroot()
    # JUnit reports can have a namespace or an outer testsuites container.
    for element in root.iter():
        element.tag = element.tag.rsplit("}", 1)[-1]
    if root.tag not in ("testsuite", "testsuites"):
        raise ValueError(f"Unexpected root element: {root.tag}")
    suites = [suite for suite in root.iter("testsuite")
              if suite.find(".//testsuite") is None]
    if not suites:
        raise ValueError("No test suites in report")
    totals = dict.fromkeys(METRICS, 0)
    failures = []
    for suite in suites:
        cases = list(suite.iter("testcase"))
        statuses = [testcase_status(case) for case in cases]
        fallback = {"tests": len(cases), "failures": statuses.count("failure"),
                    "errors": statuses.count("error"), "skipped": statuses.count("skipped")}
        counts = {key: int(suite.get(key, value)) for key, value in fallback.items()}
        if any(value < 0 for value in counts.values()) or sum(
                counts[key] for key in ("failures", "errors", "skipped")) > counts["tests"]:
            raise ValueError("Inconsistent test counts in report")
        counts["time"] = (seconds(suite.get("time")) if "time" in suite.attrib else
                          sum((seconds(case.get("time", "0")) for case in cases), Decimal(0)))
        for key in METRICS:
            totals[key] += counts[key]
        for case, status in zip(cases, statuses):
            if status not in ("failure", "error"):
                continue
            problem = case.find(status)
            identity = ".".join(filter(None, (case.get("classname"), case.get("name"))))
            message = problem.get("message") or problem.get("type") or problem.text or "No error details"
            failures.append((identity or suite.get("name", "Unnamed test"), status, message))
    return totals, failures


def row(module, report_type, counts):
    passed = counts["tests"] - counts["failures"] - counts["errors"] - counts["skipped"]
    return (f"| {module} | {report_type} | {counts['tests']} | {passed} | "
            f"{counts['failures']} | {counts['errors']} | {counts['skipped']} | {counts['time']:.3f} s |")


def main():
    root = Path.cwd()
    reports = sorted(path for report_type in ("surefire", "failsafe")
                     for path in root.glob(f"**/target/{report_type}-reports/TEST-*.xml"))
    groups = defaultdict(lambda: dict.fromkeys(METRICS, 0))
    total = dict.fromkeys(METRICS, 0)
    failures, diagnostics = [], []
    parsed = 0
    for path in reports:
        module = str(path.parent.parent.parent.relative_to(root))
        report_type = path.parent.name.removesuffix("-reports").capitalize()
        try:
            counts, cases = read_report(path)
        except (ET.ParseError, OSError, ValueError, InvalidOperation) as error:
            diagnostics.append(f"{path.relative_to(root)}: {error}")
            continue
        parsed += 1
        for key in METRICS:
            groups[(module, report_type)][key] += counts[key]
            total[key] += counts[key]
        failures.extend((module, report_type, *case) for case in cases)

    lines = [f"## {markdown(os.environ.get('TEST_PHASE', 'Test'))} test results", "",
             f"Build step: **{markdown(os.environ.get('BUILD_OUTCOME', 'unknown'))}** · "
             f"Job status at summary: **{markdown(os.environ.get('JOB_STATUS', 'unknown'))}**", ""]
    if parsed:
        lines += [f"Parsed {parsed} of {len(reports)} test report files.", "",
                  "| Module | Report | Tests | Passed | Failures | Errors | Skipped | Time |",
                  "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
        lines.extend(row(markdown(module), report_type, counts)
                     for (module, report_type), counts in sorted(groups.items()))
        lines += [row("**Total**", "", total), ""]
    else:
        lines += ["**No test reports were available to summarize; test success cannot be determined.**", ""]
    if failures:
        lines += ["### Failed tests", ""]
        for module, report_type, identity, status, message in failures[:MAX_FAILURES]:
            lines.append(f"- **{markdown(module)} / {report_type}** — {markdown(identity, 180)} "
                         f"({status}): {markdown(message)}")
        if len(failures) > MAX_FAILURES:
            lines.append(f"- {len(failures) - MAX_FAILURES} more failed tests; see the report artifact.")
        lines.append("")
    if diagnostics:
        lines += ["### Report diagnostics", "", "**The totals are incomplete because some reports could not be read.**", ""]
        lines.extend(f"- {markdown(message)}" for message in diagnostics[:MAX_DIAGNOSTICS])
        if len(diagnostics) > MAX_DIAGNOSTICS:
            lines.append(f"- {len(diagnostics) - MAX_DIAGNOSTICS} more unreadable reports.")
        lines.append("")
    artifact_url = os.environ.get("ARTIFACT_URL", "")
    if re.fullmatch(r"https://[^\s<>]+", artifact_url):
        lines.append(f"[Download test reports](<{html.escape(artifact_url)}>)")
    else:
        lines.append("No report artifact link is available.")
    summary = "\n".join(lines) + "\n"
    if summary_path := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary_path, "a", encoding="utf-8") as output:
            output.write(summary)
    else:
        print(summary, end="")


if __name__ == "__main__":
    main()
