#!/usr/bin/env python3
"""Extract one Psalm's verse-level WEB Catholic text from an eBible VPL file."""

import argparse
import csv
import re
from pathlib import Path


def extract(input_path: Path, output_path: Path, chapter: int | None) -> None:
    verse_pattern = re.compile(r"^PSA (\d+):(\d+)\s+(.*)$")
    rows: dict[int, dict[int, str]] = {}
    for line in input_path.read_text(encoding="utf-8-sig").splitlines():
        match = verse_pattern.match(line)
        if match:
            found_chapter = int(match.group(1))
            if chapter is not None and found_chapter != chapter:
                continue
            verse = int(match.group(2))
            chapter_rows = rows.setdefault(found_chapter, {})
            if verse in chapter_rows:
                raise ValueError(f"duplicate Psalm {found_chapter} verse: {verse}")
            chapter_rows[verse] = " ".join(match.group(3).split())

    if not rows:
        raise ValueError(f"Psalm {chapter or 'source'} was not found")
    for found_chapter, chapter_rows in rows.items():
        expected = set(range(1, max(chapter_rows) + 1))
        if set(chapter_rows) != expected:
            raise ValueError(
                f"Psalm {found_chapter} must contain verses 1-{max(chapter_rows)}; "
                f"missing={sorted(expected - set(chapter_rows))}, extra={sorted(set(chapter_rows) - expected)}"
            )
        if any(not text for text in chapter_rows.values()):
            raise ValueError(f"Psalm {found_chapter} contains blank verse text")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(("book", "chapter", "verse", "text"))
        for found_chapter in sorted(rows):
            for verse in sorted(rows[found_chapter]):
                writer.writerow(
                    ("Psalms", found_chapter, verse, rows[found_chapter][verse])
                )
    print(f"WROTE {output_path} ({sum(map(len, rows.values()))} verses)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("chapter", help="Psalm number, or 'all'")
    parser.add_argument("output", type=Path)
    arguments = parser.parse_args()
    extract(
        arguments.input,
        arguments.output,
        None if arguments.chapter == "all" else int(arguments.chapter),
    )
