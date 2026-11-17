-- Logical content schema. The generated Android database must be validated
-- against the Room schema before it is copied into app/src/main/assets.

CREATE TABLE content_metadata (
    key TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE edition (
    id TEXT NOT NULL PRIMARY KEY,
    display_name TEXT NOT NULL,
    source_module TEXT NOT NULL,
    versification TEXT NOT NULL,
    attribution TEXT NOT NULL,
    rights_notice TEXT NOT NULL
);

CREATE TABLE verse_unit (
    id INTEGER NOT NULL PRIMARY KEY,
    edition_id TEXT NOT NULL,
    book_code TEXT NOT NULL,
    chapter INTEGER NOT NULL,
    native_verse_label TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    text TEXT NOT NULL,
    osis_fragment TEXT NOT NULL,
    FOREIGN KEY (edition_id) REFERENCES edition(id) ON DELETE CASCADE
);

CREATE TABLE calendar_day (
    date TEXT NOT NULL,
    calendar_scope TEXT NOT NULL,
    celebration TEXT NOT NULL,
    rank TEXT NOT NULL,
    liturgical_colour TEXT NOT NULL,
    review_status TEXT NOT NULL,
    source_url TEXT NOT NULL,
    PRIMARY KEY (date, calendar_scope)
);

CREATE TABLE mass_form (
    date TEXT NOT NULL,
    calendar_scope TEXT NOT NULL,
    form_id TEXT NOT NULL,
    label TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    is_default INTEGER NOT NULL,
    source_url TEXT NOT NULL,
    review_status TEXT NOT NULL,
    PRIMARY KEY (date, calendar_scope, form_id),
    FOREIGN KEY (date, calendar_scope) REFERENCES calendar_day(date, calendar_scope) ON DELETE CASCADE
);

CREATE TABLE reading_assignment (
    id INTEGER NOT NULL PRIMARY KEY,
    date TEXT NOT NULL,
    calendar_scope TEXT NOT NULL,
    mass_form_id TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    role TEXT NOT NULL,
    mass_form TEXT NOT NULL,
    original_citation TEXT NOT NULL,
    paired_psalm_citation TEXT NOT NULL,
    selection_rule TEXT NOT NULL,
    review_status TEXT NOT NULL,
    FOREIGN KEY (date, calendar_scope) REFERENCES calendar_day(date, calendar_scope) ON DELETE CASCADE,
    FOREIGN KEY (date, calendar_scope, mass_form_id) REFERENCES mass_form(date, calendar_scope, form_id) ON DELETE CASCADE
);

CREATE INDEX index_verse_unit_edition_id_book_code_chapter_sort_order
    ON verse_unit (edition_id, book_code, chapter, sort_order);

CREATE INDEX index_calendar_day_date_calendar_scope
    ON calendar_day (date, calendar_scope);

CREATE INDEX index_mass_form_date_calendar_scope_sort_order
    ON mass_form (date, calendar_scope, sort_order);

CREATE INDEX index_reading_assignment_date_calendar_scope_sequence
    ON reading_assignment (date, calendar_scope, mass_form_id, sequence);
