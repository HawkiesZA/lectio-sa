#!/usr/bin/env python3
"""Merge scraped calendar review files into the canonical calendar CSVs."""

import csv
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CALENDAR_DIR = ROOT / "content" / "calendar"
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


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != HEADERS:
            raise ValueError(f"{path} has unexpected CSV headers")
        return list(reader)


def write_rows(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=HEADERS)
        writer.writeheader()
        writer.writerows(rows)


def merge_year(year: int) -> None:
    review_path = CALENDAR_DIR / f"{year}_to_review.csv"
    canonical_path = CALENDAR_DIR / f"{year}.csv"
    review_rows = read_rows(review_path)
    canonical_rows = read_rows(canonical_path)

    reviewed = {
        row["date"]: row for row in canonical_rows if row["review_status"] == "reviewed"
    }
    merged_rows = [reviewed.get(row["date"], row) for row in review_rows]
    write_rows(canonical_path, merged_rows)
    print(f"merged {review_path} -> {canonical_path}: {len(merged_rows)} rows")


if __name__ == "__main__":
    merge_year(2026)
    merge_year(2027)
