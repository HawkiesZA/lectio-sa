#!/usr/bin/env python3
"""Fetch the Southern Africa Ordo and prepare annual SACBC review CSVs."""

from __future__ import annotations

import csv
import html
import re
import urllib.parse
import urllib.request
from datetime import date
from html.parser import HTMLParser
from pathlib import Path

BASE_URL = "https://ordo.jesuitssouthern.africa/ordo_full.php"
SACBC_URL = "https://ordo.jesuitssouthern.africa/sacbc.php"
OUTPUT_DIR = Path(__file__).resolve().parents[1] / "calendar"
HEADER = [
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

MONTHS = {
    name: number
    for number, names in enumerate(
        [
            ("Jan",),
            ("Feb",),
            ("Mar",),
            ("Apr",),
            ("May",),
            ("Jun",),
            ("Jul",),
            ("Aug",),
            ("Sep",),
            ("Oct",),
            ("Nov",),
            ("Dec",),
        ],
        1,
    )
    for name in names
}

BOOKS = {
    "Lk": "Luke",
    "Jn": "John",
    "Mt": "Matthew",
    "Mk": "Mark",
    "Is": "Isaiah",
    "Jer": "Jeremiah",
    "Ezek": "Ezekiel",
    "Gen": "Genesis",
    "Ex": "Exodus",
    "Deut": "Deuteronomy",
    "Ps": "Ps",
}
RANKS = {"Solemnity", "Feast", "Memorial", "Optional", "Sunday"}
READING_BOOKS = (
    "Genesis|Exodus|Leviticus|Numbers|Deuteronomy|Joshua|Judges|Ruth|"
    "1 Samuel|2 Samuel|1 Kings|2 Kings|1 Chronicles|2 Chronicles|Ezra|Nehemiah|"
    "Tobit|Judith|Esther|1 Maccabees|2 Maccabees|Job|Psalms?|Proverbs|Ecclesiastes|"
    "Song|Wisdom|Sirach|Isaiah|Jeremiah|Lamentations|Baruch|Ezekiel|Daniel|Hosea|Joel|"
    "Amos|Obadiah|Jonah|Micah|Nahum|Habakkuk|Zephaniah|Haggai|Zechariah|Malachi|"
    "Matthew|Mark|Luke|John|Acts|Romans|1 Corinthians|2 Corinthians|Galatians|"
    "Ephesians|Philippians|Colossians|1 Thessalonians|2 Thessalonians|1 Timothy|"
    "2 Timothy|Titus|Philemon|Hebrews|James|1 Peter|2 Peter|1 John|2 John|3 John|"
    "Jude|Revelation|Gen|Ex|Deut|Num|Ps|Prov|Wis|Sir|Is|Jer|Ezek|Dan|Hos|Amos|"
    "Obad|Jon|Mic|Nah|Hab|Zeph|Hag|Zech|Mal|Mt|Mk|Lk|Jn|Rom|Cor|Gal|Eph|Phil|Col|"
    "Thess|Tim|Tit|Phlm|Hebr|Pet|Rev|Matt"
)


class RowParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.rows: list[list[str]] = []
        self.attrs: list[list[dict[str, str]]] = []
        self._cells: list[str] | None = None
        self._cell_attrs: list[dict[str, str]] = []
        self._text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "tr":
            self._cells = []
            self._cell_attrs = []
        elif tag == "td" and self._cells is not None:
            self._text = []
            self._cell_attrs.append({key: value or "" for key, value in attrs})

    def handle_data(self, data: str) -> None:
        if self._cells is not None:
            self._text.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag == "td" and self._cells is not None:
            self._cells.append(" ".join(self._text))
        elif tag == "tr" and self._cells is not None:
            self.rows.append([clean(value) for value in self._cells])
            self.attrs.append(self._cell_attrs or [])
            self._cells = None
            self._cell_attrs = []


def clean(value: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(value)).strip()


def fetch(year: int) -> str:
    body = urllib.parse.urlencode(
        {"year": str(year), "loc_opt": "1", "jd_opt": "1", "ob_opt": "1"}
    ).encode()
    request = urllib.request.Request(BASE_URL, data=body, method="POST")
    return urllib.request.urlopen(request, timeout=60).read().decode("utf-8", "replace")


def date_blocks(source: str, year: int) -> list[tuple[date, str]]:
    marker = re.compile(
        r'<tr><td[^>]*><b>(\d{1,2})&nbsp;([A-Z][a-z]{2})&nbsp;[^<]+</b><a id="\d+"'
    )
    matches = list(marker.finditer(source))
    blocks = []
    for index, match in enumerate(matches):
        day = int(match.group(1))
        month = MONTHS[match.group(2)]
        current = date(year, month, day)
        end = matches[index + 1].start() if index + 1 < len(matches) else len(source)
        blocks.append((current, source[match.start() : end]))
    return blocks


def colour(value: str, attrs: dict[str, str]) -> str:
    title = attrs.get("title", "").strip().lower()
    if title:
        return title
    match = re.search(r"\b(white|green|red|violet|rose|gold)\b", value.lower())
    return match.group(1) if match else ""


def main_celebration(value: str) -> str:
    uppercase_matches = re.findall(r"\b[A-Z][A-Z ,.'’-]{8,}\b", value)
    if uppercase_matches:
        result = clean(uppercase_matches[-1]).title()
        for word in ("Of", "The", "And", "In", "On", "For"):
            result = re.sub(rf"\b{word}\b", word.lower(), result)
        return result.strip(" .")
    lines = [
        clean(item)
        for item in re.split(r"\s*\n\s*|(?<=[.!?])\s+(?=[A-Z])", value)
        if clean(item)
    ]
    lines = [
        item
        for item in lines
        if not any(
            marker in item
            for marker in (
                "World Day",
                "est.",
                "Titular Feast",
                "Outside SJ",
                "Texts in the Jesuit",
                "Download",
            )
        )
    ]
    uppercase = [item for item in lines if sum(char.isupper() for char in item) >= 3]
    return (uppercase[-1] if uppercase else (lines[-1] if lines else "")).strip(" .")


def citation_parts(value: str) -> list[str]:
    value = re.sub(r"^\[[^]]+\]\s*", "", value)
    value = re.sub(r"\s*\[[^]]+\]", "", value)
    parts: list[str] = []
    book_start = re.compile(rf"^(?:{READING_BOOKS})\b\s+\d", re.I)
    for part in value.split(";"):
        part = part.strip().rstrip(".")
        if not part:
            continue
        if parts and not book_start.match(part):
            parts[-1] += " " + part
        else:
            parts.append(part)
    return parts


def normalize_citation(value: str) -> str:
    value = re.sub(r"\s+", " ", value.strip())
    value = re.sub(
        r"\b([A-Za-z]+)\s+",
        lambda match: BOOKS.get(match.group(1), match.group(1)) + " ",
        value,
    )
    value = re.sub(r":\s+", ":", value)
    value = value.replace(",", "")
    return value.strip().rstrip(".")


def readings(value: str) -> tuple[str, str, str, str]:
    value = value.split("Download", 1)[0]
    parts = citation_parts(value)
    normalized = [normalize_citation(part) for part in parts]
    if len(normalized) < 3:
        return ("", "", "", normalized[-1] if normalized else "")
    if len(normalized) == 3:
        return (normalized[0], normalized[1], "", normalized[2])
    return (normalized[0], normalized[1], normalized[-2], normalized[-1])


def parse_block(day: date, source: str) -> dict[str, str]:
    parser = RowParser()
    parser.feed(source)
    rows = parser.rows
    attrs = parser.attrs
    dated = next(
        index
        for index, row in enumerate(rows)
        if row
        and re.match(
            r"\d{1,2} Jan|\d{1,2} Feb|\d{1,2} Mar|\d{1,2} Apr|\d{1,2} May|\d{1,2} Jun|\d{1,2} Jul|\d{1,2} Aug|\d{1,2} Sep|\d{1,2} Oct|\d{1,2} Nov|\d{1,2} Dec",
            row[0],
        )
    )
    rows = rows[dated:]
    attrs = attrs[dated:]
    info = {
        "date": day.isoformat(),
        "celebration": "",
        "rank": "",
        "liturgical_colour": "",
        "mass_form": "",
        "first_reading": "",
        "psalm": "",
        "second_reading": "",
        "gospel": "",
    }
    for index, row in enumerate(rows):
        if (
            len(row) >= 2
            and not info["celebration"]
            and len(row) >= 3
            and row[1]
            and row[0].lower() in {"white", "green", "red", "violet", "rose", "gold"}
        ):
            info["liturgical_colour"] = row[0].lower()
            info["celebration"] = main_celebration(row[1])
            info["rank"] = row[2]
        if (
            len(row) >= 2
            and not info["mass_form"]
            and row[1].lower().startswith("mass")
        ):
            info["mass_form"] = row[1]
    lect_index = next(
        (
            row_index
            for row_index, row_attrs in enumerate(attrs)
            if any("lect" in cell_attrs.get("class", "") for cell_attrs in row_attrs)
        ),
        None,
    )
    if lect_index is not None and rows[lect_index]:
        reading_text = " ".join(
            cell
            for cell, cell_attrs in zip(rows[lect_index], attrs[lect_index])
            if "lect" in cell_attrs.get("class", "")
        )
        info["first_reading"], info["psalm"], info["second_reading"], info["gospel"] = (
            readings(reading_text)
        )
    base = re.search(
        r'title="(white|green|red|violet|rose|gold)"[^>]*>.*?</span>.*?</td><td[^>]*>(.*?)</td><td[^>]*>(.*?)</td>',
        source,
        re.I | re.S,
    )
    if base:
        info["liturgical_colour"] = base.group(1).lower()
        base_name = re.sub(r"<a\b.*", "", base.group(2), flags=re.I | re.S)
        info["celebration"] = main_celebration(
            clean(re.sub(r"<[^>]+>", " ", base_name))
        )
        candidate_rank = clean(re.sub(r"<[^>]+>", " ", base.group(3)))
        if candidate_rank in RANKS:
            info["rank"] = candidate_rank
    local_start = source.find('<table class="local">')
    local_html = ""
    while local_start >= 0:
        cursor = local_start
        depth = 0
        for match in re.finditer(r"</?table\b[^>]*>", source[local_start:], re.I):
            token = match.group(0)
            depth += -1 if token.lower().startswith("</") else 1
            if depth == 0:
                candidate = source[local_start : local_start + match.end()]
                if "[SACBC]" in candidate:
                    local_html = candidate
                break
        if local_html:
            break
        local_start = source.find('<table class="local">', cursor + 1)
    if local_html and "[SACBC]" in local_html:
        local_parser = RowParser()
        local_parser.feed(local_html)
        local_rows = local_parser.rows
        local_attrs = local_parser.attrs
        local = next(
            (
                row
                for row in local_rows
                if len(row) >= 3
                and re.search(r"\b(?:white|green|red|violet|rose|gold)\b", row[0], re.I)
            ),
            None,
        )
        if local is not None:
            row_index = local_rows.index(local)
            info["liturgical_colour"] = colour(
                local[0], local_attrs[row_index][0] if local_attrs[row_index] else {}
            )
            info["celebration"] = main_celebration(local[1])
            info["rank"] = local[2]
            info["mass_form"] = next(
                (
                    re.split(
                        r"\s+(?=(?:Wis|Ps|Eph|Jn|Lk|Mk|Mt)\s)", row[1], maxsplit=1
                    )[0]
                    for row in local_rows[row_index + 1 :]
                    if len(row) > 1 and row[1]
                ),
                "",
            )
            lect_index = next(
                (
                    index
                    for index, row_attrs in enumerate(local_attrs)
                    if any("lect" in attrs.get("class", "") for attrs in row_attrs)
                ),
                None,
            )
            if lect_index is not None and local_rows[lect_index]:
                reading_text = " ".join(
                    cell
                    for cell, cell_attrs in zip(
                        local_rows[lect_index], local_attrs[lect_index]
                    )
                    if "lect" in cell_attrs.get("class", "")
                )
                (
                    info["first_reading"],
                    info["psalm"],
                    info["second_reading"],
                    info["gospel"],
                ) = readings(reading_text)
    return info


def scrape(year: int) -> list[dict[str, str]]:
    source = fetch(year)
    rows = [parse_block(day, block) for day, block in date_blocks(source, year)]
    expected = 366 if date(year, 12, 31).timetuple().tm_yday == 366 else 365
    if len(rows) != expected:
        raise RuntimeError(
            f"{year}: expected {expected} date blocks, found {len(rows)}"
        )
    for row in rows:
        row.update(
            calendar_scope="SACBC", source_url=BASE_URL, review_status="needs_review"
        )
    missing = [
        row["date"]
        for row in rows
        if not any(
            row[column]
            for column in ("first_reading", "psalm", "second_reading", "gospel")
        )
    ]
    if missing:
        print(f"WARNING {year}: no readings in source row for {', '.join(missing)}")
    return rows


def write(year: int, rows: list[dict[str, str]]) -> None:
    path = OUTPUT_DIR / f"{year}_to_review.csv"
    with path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=HEADER)
        writer.writeheader()
        writer.writerows(rows)
    print(f"WROTE {path} ({len(rows)} rows)")


if __name__ == "__main__":
    for requested_year in (2026, 2027):
        write(requested_year, scrape(requested_year))
