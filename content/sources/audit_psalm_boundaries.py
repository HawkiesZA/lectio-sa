#!/usr/bin/env python3
"""Find Psalm verse-boundary loss between the VPL source and rendered SWORD TSV."""

import argparse
import csv
import re
from collections import defaultdict
from pathlib import Path


def read_vpl(path: Path) -> dict[int, dict[int, str]]:
    result: dict[int, dict[int, str]] = defaultdict(dict)
    pattern = re.compile(r"^PSA (\d+):(\d+)\s+(.*)$")
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        match = pattern.match(line)
        if match:
            chapter, verse = int(match.group(1)), int(match.group(2))
            if verse in result[chapter]:
                raise ValueError(f"duplicate Psalm {chapter}:{verse} in VPL")
            result[chapter][verse] = " ".join(match.group(3).split())
    return result


def read_rendered(path: Path) -> dict[int, set[int]]:
    result: dict[int, set[int]] = defaultdict(set)
    for line in path.read_text(encoding="utf-8").splitlines()[1:]:
        fields = line.split("\t", 4)
        if (
            len(fields) == 5
            and fields[0] == "Psalms"
            and fields[1].isdigit()
            and fields[2].isdigit()
        ):
            if fields[2] != "0" and fields[3].strip():
                result[int(fields[1])].add(int(fields[2]))
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("vpl", type=Path)
    parser.add_argument("rendered", type=Path)
    parser.add_argument("--overlay-output", type=Path)
    args = parser.parse_args()

    source = read_vpl(args.vpl)
    rendered = read_rendered(args.rendered)
    affected = []
    for chapter in sorted(source):
        expected = set(source[chapter])
        missing = sorted(expected - rendered[chapter])
        if missing:
            affected.append(chapter)
            print(f"Psalm {chapter}: missing {len(missing)} verse rows ({missing})")

    if args.overlay_output:
        args.overlay_output.parent.mkdir(parents=True, exist_ok=True)
        with args.overlay_output.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
            writer.writerow(("book", "chapter", "verse", "text"))
            for chapter in affected:
                for verse in sorted(source[chapter]):
                    writer.writerow(("Psalms", chapter, verse, source[chapter][verse]))
        print(f"WROTE {args.overlay_output} ({len(affected)} affected Psalms)")

    print(f"affected_psalms={len(affected)}")
    if affected:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
