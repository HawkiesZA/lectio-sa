#!/usr/bin/env python3
"""Extract verse-level WEB Catholic Psalm 119 text from the eBible VPL file."""

import argparse
import csv
import re
from pathlib import Path


VERSE = re.compile(r"^PSA 119:(\d+)\s+(.*)$")
HEADINGS = (
    "SIN AND SHIN",
    "ALEPH",
    "BETH",
    "GIMEL",
    "DALETH",
    "HE",
    "VAV",
    "ZAYIN",
    "HETH",
    "TETH",
    "YODH",
    "KAPF",
    "LAMEDH",
    "MEM",
    "NUN",
    "SAMEKH",
    "AYIN",
    "PE",
    "TZADHE",
    "QOPH",
    "RESH",
    "TAV",
)


def clean_heading(text: str) -> str:
    for heading in HEADINGS:
        text = re.sub(rf"(?:^|\s){re.escape(heading)}(?=\s|$)", " ", text)
    return " ".join(text.split())


def extract(input_path: Path, output_path: Path) -> None:
    rows: dict[int, str] = {}
    for line in input_path.read_text(encoding="utf-8-sig").splitlines():
        match = VERSE.match(line)
        if match:
            verse = int(match.group(1))
            if verse in rows:
                raise ValueError(f"duplicate Psalm 119 verse: {verse}")
            rows[verse] = clean_heading(match.group(2))

    expected = set(range(1, 177))
    if set(rows) != expected:
        missing = sorted(expected - set(rows))
        extra = sorted(set(rows) - expected)
        raise ValueError(
            f"Psalm 119 must contain verses 1-176; missing={missing}, extra={extra}"
        )
    if any(not text for text in rows.values()):
        raise ValueError("Psalm 119 contains blank verse text")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(("book", "chapter", "verse", "text"))
        for verse in range(1, 177):
            writer.writerow(("Psalms", "119", verse, rows[verse]))
    print(f"WROTE {output_path} (176 verses)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    arguments = parser.parse_args()
    extract(arguments.input, arguments.output)
