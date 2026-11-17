#!/usr/bin/env python3
"""Audit and optionally repair structural errors in annual calendar CSVs."""

from __future__ import annotations

import argparse
import csv
import re
from pathlib import Path


HEADERS = [
    "date",
    "calendar_scope",
    "celebration",
    "rank",
    "liturgical_colour",
    "mass_form",
    "first_reading",
    "psalm",
    "second_reading",
    "gospel",
    "source_url",
    "review_status",
]
MASS_FORM_HEADERS = [
    "date",
    "calendar_scope",
    "form_id",
    "label",
    "sort_order",
    "is_default",
    "source_url",
    "review_status",
]
MASS_READING_HEADERS = [
    "date",
    "calendar_scope",
    "form_id",
    "sequence",
    "role",
    "selection_rule",
    "reading_citation",
    "psalm_citation",
    "source_url",
    "review_status",
]
SECOND_BOOKS = (
    "1 Cor",
    "2 Cor",
    "Gal",
    "Eph",
    "Phil",
    "Col",
    "1 Thess",
    "2 Thess",
    "1 Tim",
    "2 Tim",
    "Titus",
    "Phlm",
    "Heb",
    "Jas",
    "1 Pet",
    "2 Pet",
    "1 John",
    "2 John",
    "3 John",
    "Rev",
    "Rom",
    "Acts",
)
SECOND_BOOK_RE = re.compile(
    r"\s+(?P<book>"
    + "|".join(re.escape(book) for book in sorted(SECOND_BOOKS, key=len, reverse=True))
    + r")\s+(?=\d)",
    re.IGNORECASE,
)
EDITORIAL_MARKERS = (
    "Readings:",
    "The Readings:",
    "These readings",
    "Vigil Mass:",
    "Mass of the Day:",
    "Mass of the Vigil:",
    "Assumption Vigil",
    "Triumph of the Cross",
    "Mass 1 2 or 3 of All Souls",
)


def load(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != HEADERS:
            raise ValueError(f"{path}: unexpected CSV header")
        return list(reader)


def save(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=HEADERS)
        writer.writeheader()
        writer.writerows(rows)


def audit_mass_forms(forms_path: Path, readings_path: Path) -> None:
    if not forms_path.exists() and not readings_path.exists():
        return
    with forms_path.open(newline="", encoding="utf-8") as stream:
        form_reader = csv.DictReader(stream)
        if form_reader.fieldnames != MASS_FORM_HEADERS:
            raise ValueError(f"{forms_path}: unexpected mass-form CSV header")
        forms = list(form_reader)
    with readings_path.open(newline="", encoding="utf-8") as stream:
        reading_reader = csv.DictReader(stream)
        if reading_reader.fieldnames != MASS_READING_HEADERS:
            raise ValueError(f"{readings_path}: unexpected mass-reading CSV header")
        readings = list(reading_reader)
    form_keys = {(row["date"], row["calendar_scope"], row["form_id"]) for row in forms}
    form_groups: dict[tuple[str, str], list[dict[str, str]]] = {}
    for row in forms:
        form_groups.setdefault((row["date"], row["calendar_scope"]), []).append(row)
    for day, day_forms in sorted(form_groups.items()):
        if sum(row["is_default"] == "1" for row in day_forms) != 1:
            raise ValueError(f"{forms_path}: expected one default form for {day}")
    reading_groups: dict[tuple[str, str, str], list[dict[str, str]]] = {}
    for row in readings:
        if (row["date"], row["calendar_scope"], row["form_id"]) not in form_keys:
            raise ValueError(f"{readings_path}: unknown mass form: {row}")
        if int(row["sequence"]) < 1:
            raise ValueError(f"{readings_path}: sequence must be positive: {row}")
        if row["selection_rule"] not in {"obligatory", "optional"}:
            raise ValueError(f"{readings_path}: invalid selection rule: {row}")
        if not row["reading_citation"]:
            raise ValueError(f"{readings_path}: reading citation is blank: {row}")
        reading_groups.setdefault(
            (row["date"], row["calendar_scope"], row["form_id"]), []
        ).append(row)
    for day, day_rows in sorted(reading_groups.items()):
        sequences = sorted(int(row["sequence"]) for row in day_rows)
        if sequences != list(range(1, len(sequences) + 1)):
            raise ValueError(f"{readings_path}: sequences are not contiguous for {day}")
        if len(sequences) != len(set(sequences)):
            raise ValueError(f"{readings_path}: duplicate sequence for {day}")
        print(
            f"{readings_path}: {day[0]} {day[2]} has {len(day_rows)} ordered readings"
        )


def audit(rows: list[dict[str, str]]) -> dict[str, list[dict[str, str]]]:
    findings: dict[str, list[dict[str, str]]] = {
        "psalm_second_shift": [],
        "other_column_shift": [],
        "editorial_text": [],
        "cross_chapter": [],
        "alternatives": [],
    }
    for row in rows:
        if not row["second_reading"] and SECOND_BOOK_RE.search(row["psalm"]):
            findings["psalm_second_shift"].append(row)
        if re.search(
            r"^Ps\b.*\b(?:Luke|Matt|Mark|John|Acts|Eph|Phil|Rom|Heb)\s+\d",
            row["gospel"],
            re.IGNORECASE,
        ):
            findings["other_column_shift"].append(row)
        elif not row["second_reading"] and re.search(
            r"\b(?:Eph|Phil|Rom|Heb|Jas|1 Pet|2 Pet|1 Cor|2 Cor)\s+\d",
            row["psalm"],
            re.IGNORECASE,
        ):
            findings["other_column_shift"].append(row)
        reading_text = " | ".join(
            row[column]
            for column in ("first_reading", "psalm", "second_reading", "gospel")
        )
        if any(marker in reading_text for marker in EDITORIAL_MARKERS):
            findings["editorial_text"].append(row)
        if any(
            re.search(r"\d+:[^ ]+\s+\d+:[^ ]+", row[column])
            for column in ("first_reading", "second_reading", "gospel")
        ):
            findings["cross_chapter"].append(row)
        if re.search(r"\s+or\s+", reading_text, re.IGNORECASE):
            findings["alternatives"].append(row)
    return findings


def repair_safe_shifts(rows: list[dict[str, str]]) -> int:
    repaired = 0
    for row in rows:
        if row["second_reading"]:
            continue
        match = SECOND_BOOK_RE.search(row["psalm"])
        if not match:
            continue
        row["second_reading"] = row["psalm"][match.start() :].strip()
        row["psalm"] = row["psalm"][: match.start()].strip()
        repaired += 1
    return repaired


def print_report(path: Path, findings: dict[str, list[dict[str, str]]]) -> None:
    print(f"{path}")
    for category, rows in findings.items():
        dates = ", ".join(row["date"] for row in rows)
        print(f"  {category}: {len(rows)}" + (f" [{dates}]" if dates else ""))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        default=[Path("content/calendar/2026.csv"), Path("content/calendar/2027.csv")],
    )
    parser.add_argument("--fix-safe-shifts", action="store_true")
    args = parser.parse_args()

    for path in args.paths:
        rows = load(path)
        if args.fix_safe_shifts:
            repaired = repair_safe_shifts(rows)
            save(path, rows)
            print(f"repaired_safe_shifts={repaired}")
        print_report(path, audit(rows))
    audit_mass_forms(
        Path("content/calendar/mass_forms.csv"),
        Path("content/calendar/mass_readings.csv"),
    )


if __name__ == "__main__":
    main()
