#!/usr/bin/env python3
"""Create a text overlay for chapters whose SWORD import lost verse boundaries."""

import argparse
import csv
import re
from collections import defaultdict
from pathlib import Path

BOOK_NAMES = {
    "GEN": "Genesis",
    "EXO": "Exodus",
    "LEV": "Leviticus",
    "NUM": "Numbers",
    "DEU": "Deuteronomy",
    "JOS": "Joshua",
    "JDG": "Judges",
    "RUT": "Ruth",
    "1SA": "I Samuel",
    "2SA": "II Samuel",
    "1KI": "I Kings",
    "2KI": "II Kings",
    "1CH": "I Chronicles",
    "2CH": "II Chronicles",
    "EZR": "Ezra",
    "NEH": "Nehemiah",
    "TOB": "Tobit",
    "JDT": "Judith",
    "EST": "Esther",
    "ESG": "Esther",
    "1MA": "I Maccabees",
    "2MA": "II Maccabees",
    "JOB": "Job",
    "PSA": "Psalms",
    "PRO": "Proverbs",
    "ECC": "Ecclesiastes",
    "SOL": "Song of Solomon",
    "WIS": "Wisdom",
    "SIR": "Sirach",
    "ISA": "Isaiah",
    "JER": "Jeremiah",
    "LAM": "Lamentations",
    "BAR": "Baruch",
    "EZE": "Ezekiel",
    "DNG": "Daniel",
    "HOS": "Hosea",
    "JOE": "Joel",
    "AMO": "Amos",
    "OBA": "Obadiah",
    "JON": "Jonah",
    "MIC": "Micah",
    "NAH": "Nahum",
    "HAB": "Habakkuk",
    "ZEP": "Zephaniah",
    "HAG": "Haggai",
    "ZEC": "Zechariah",
    "MAL": "Malachi",
    "MAT": "Matthew",
    "MAR": "Mark",
    "LUK": "Luke",
    "JOH": "John",
    "ACT": "Acts",
    "ROM": "Romans",
    "1CO": "I Corinthians",
    "2CO": "II Corinthians",
    "GAL": "Galatians",
    "EPH": "Ephesians",
    "PHI": "Philippians",
    "COL": "Colossians",
    "1TH": "I Thessalonians",
    "2TH": "II Thessalonians",
    "1TI": "I Timothy",
    "2TI": "II Timothy",
    "TIT": "Titus",
    "PHM": "Philemon",
    "HEB": "Hebrews",
    "JAM": "James",
    "1PE": "I Peter",
    "2PE": "II Peter",
    "1JO": "I John",
    "2JO": "II John",
    "3JO": "III John",
    "JUD": "Jude",
    "REV": "Revelation of John",
}


def read_vpl(path: Path) -> dict[tuple[str, int], dict[int, str]]:
    pattern = re.compile(r"^([A-Z0-9]+) (\d+):(\d+)\s+(.*)$")
    rows: dict[tuple[str, int], dict[int, str]] = defaultdict(dict)
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        match = pattern.match(line)
        if not match:
            continue
        code, chapter, verse, text = match.groups()
        book = BOOK_NAMES.get(code)
        if book is None:
            continue
        text = " ".join(text.split())
        if text:
            rows[(book, int(chapter))][int(verse)] = text
    return rows


def read_rendered(path: Path) -> dict[tuple[str, int], set[int]]:
    rows: dict[tuple[str, int], set[int]] = defaultdict(set)
    with path.open(encoding="utf-8") as stream:
        for fields in csv.reader(stream, delimiter="\t"):
            if (
                len(fields) >= 4
                and fields[1].isdigit()
                and fields[2].isdigit()
                and fields[3].strip()
            ):
                rows[(fields[0], int(fields[1]))].add(int(fields[2]))
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("vpl", type=Path)
    parser.add_argument("rendered", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    source = read_vpl(args.vpl)
    rendered = read_rendered(args.rendered)
    affected = {
        key for key, verses in source.items() if set(verses) - rendered.get(key, set())
    }
    for book, chapter in sorted(affected):
        missing = sorted(
            set(source[(book, chapter)]) - rendered.get((book, chapter), set())
        )
        print(f"{book} {chapter}: missing {len(missing)} verse rows ({missing})")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(("book", "chapter", "verse", "text"))
        for key in sorted(affected):
            book, chapter = key
            for verse, text in sorted(source[key].items()):
                writer.writerow((book, chapter, verse, text))
    print(f"WROTE {args.output} ({len(affected)} affected chapters)")


if __name__ == "__main__":
    main()
