#!/usr/bin/env python3
"""Check Sahara's CARE questionnaire files against CARE's form rules and the app's own fields.

Usage: python3 docs/care/validate_questionnaires.py [--android DIR]

Part 1 (CARE rules) follows QuestionnaireSpec and SlugType at ohcnetwork/care@2bae933
(care/emr/resources/questionnaire/spec.py, care/emr/utils/slug_type.py) and the
25-character slug limit in the CARE docs (docs.ohc.network/concepts/definitions/questionnaire).
Checks that need a CARE database (slug already taken, value set exists, organisation exists)
cannot run here.

Part 2 (app fields) reads the Kotlin sources under android/ and compares the option lists,
prompts and length limits. It is skipped with a warning if android/ is missing.

Exit code 0 means every check passed.
"""
import json
import re
import sys
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
FORMS = HERE / "questionnaires"
ANDROID = HERE.parent.parent / "android" / "app" / "src" / "main" / "java" / "in" / "wedevit" / "care"

QUESTION_TYPES = {"group", "boolean", "decimal", "integer", "string", "text", "display", "date",
                  "dateTime", "time", "choice", "url", "quantity", "structured"}
OPERATORS = {"exists", "equals", "not_equals", "greater", "less", "greater_or_equals", "less_or_equals"}
STATUSES = {"active", "retired", "draft"}
SUBJECTS = {"patient", "encounter"}
# Internal structured types a slug must not shadow (OHC care-form-skill form.knowledge.md:75-108).
INTERNAL_TYPES = {"symptom", "diagnosis", "medication_statement", "medication_request", "allergy_intolerance",
                  "service_request", "appointment", "files", "encounter", "time_of_death", "charge_item"}
TOP_KEYS = {"slug", "version", "title", "description", "type", "status", "subject_type", "styling_metadata",
            "questions", "organizations"}
QUESTION_KEYS = {"link_id", "id", "code", "collect_time", "collect_performer", "text", "description", "type",
                 "structured_type", "enable_when", "enable_behavior", "disabled_display", "collect_body_site",
                 "collect_method", "required", "repeats", "read_only", "max_length", "answer_constraint",
                 "answer_option", "answer_value_set", "is_observation", "unit", "questions", "formula",
                 "styling_metadata", "templates", "is_component"}
SLUG_RE = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_-]*[a-zA-Z0-9]$")
SLUG_MIN, SLUG_MAX = 5, 25  # code allows 50; the docs say 25, so use the stricter one

errors = []


def err(where, msg):
    errors.append(f"{where}: {msg}")


def walk(questions):
    for q in questions:
        yield q
        yield from walk(q.get("questions", []))


def uuid_version(value):
    try:
        return uuid.UUID(value).version
    except (ValueError, AttributeError, TypeError):
        return None


def check_care_rules(name, form):
    for key in form:
        if key not in TOP_KEYS:
            err(name, f"unknown top-level key '{key}'")
    slug = form.get("slug")
    if not isinstance(slug, str) or not SLUG_RE.match(slug) or not SLUG_MIN <= len(slug) <= SLUG_MAX:
        err(name, f"slug {slug!r} must be {SLUG_MIN}-{SLUG_MAX} URL-safe characters, starting and ending with a letter or digit")
    if slug in INTERNAL_TYPES:
        err(name, f"slug {slug!r} shadows an internal CARE type")
    if Path(name).stem != slug:
        err(name, f"file name should match slug {slug!r}")
    if not isinstance(form.get("title"), str) or not form["title"].strip():
        err(name, "title is required and cannot be empty")
    if not isinstance(form.get("version", "1.0"), str):
        err(name, "version must be a string such as \"1.0\"")
    if form.get("status") not in STATUSES:
        err(name, f"status must be one of {sorted(STATUSES)}")
    if form.get("status") != "draft":
        err(name, "keep status 'draft' in the repo; a form freezes once it is active")
    if form.get("subject_type") not in SUBJECTS:
        err(name, f"subject_type must be one of {sorted(SUBJECTS)}")
    orgs = form.get("organizations")
    if not isinstance(orgs, list) or not orgs or any(uuid_version(o) != 4 for o in orgs):
        err(name, "organizations needs at least one UUID4")
    if not isinstance(form.get("questions"), list) or not form["questions"]:
        err(name, "questions must be a non-empty list")
        return

    all_q = list(walk(form["questions"]))
    link_ids = [q.get("link_id") for q in all_q]
    ids = [q.get("id") for q in all_q]
    for dup in {x for x in link_ids if link_ids.count(x) > 1}:
        err(name, f"link_id '{dup}' is not unique")
    for dup in {x for x in ids if ids.count(x) > 1}:
        err(name, f"id '{dup}' is not unique")

    for q in all_q:
        where = f"{name} [{q.get('link_id')}]"
        for key in q:
            if key not in QUESTION_KEYS:
                err(where, f"unknown question key '{key}'")
        if not isinstance(q.get("link_id"), str) or not q["link_id"]:
            err(where, "link_id is required")
        if uuid_version(q.get("id")) not in (4, 5):
            err(where, "id must be a UUID4 or UUID5")
        if not isinstance(q.get("text"), str):
            err(where, "text is required")
        qtype = q.get("type")
        if qtype not in QUESTION_TYPES:
            err(where, f"type '{qtype}' is not allowed")
        if "code" in q or "unit" in q:
            err(where, "no 'code' or 'unit': they need a terminology server and an agreed LOINC/UCUM mapping")
        if "answer_value_set" in q:
            err(where, "no 'answer_value_set': a value set must first be created by the instance superuser")
        if q.get("structured_type") and qtype != "structured":
            err(where, "structured_type is only for structured questions")
        if qtype in ("choice", "quantity"):
            options = q.get("answer_option") or []
            if not options:
                err(where, "choice needs answer_option")
            values = [o.get("value") for o in options]
            if any(not isinstance(v, str) or not v.strip() for v in values):
                err(where, "every answer option must be a non-empty string")
            if len(values) != len(set(values)):
                err(where, "answer options repeat")
        elif "answer_option" in q:
            err(where, "answer_option is only for choice or quantity")
        if qtype == "group" and not q.get("questions"):
            err(where, "a group needs at least one child question")
        if qtype != "group" and q.get("questions"):
            err(where, "only a group can have child questions")
        if "max_length" in q and (not isinstance(q["max_length"], int) or q["max_length"] < 1):
            err(where, "max_length must be a positive integer")
        for cond in q.get("enable_when") or []:
            if cond.get("question") not in link_ids:
                err(where, f"enable_when points to unknown link_id '{cond.get('question')}'")
            if cond.get("operator") not in OPERATORS:
                err(where, f"enable_when operator '{cond.get('operator')}' is not allowed")


# ---------- Part 2: app fields ----------

def kotlin(file):
    return (ANDROID / file).read_text(encoding="utf-8")


def one(pattern, text, what):
    m = re.search(pattern, text, re.S)
    if not m:
        raise SystemExit(f"Could not find {what} in the Kotlin source; update the validator.")
    return m


def english_map(src, name):
    body = one(rf"val {name} = linkedMapOf\((.*?)\)\n", src, name).group(1)
    return re.findall(r'"([^"]+)" to \("([^"]+)" to', body)


def app_fields():
    sym = kotlin("CareSymptomData.kt")
    pain = kotlin("CarePainBodyData.kt")
    prep = kotlin("DoctorPreparationQuestions.kt")
    data = kotlin("CareData.kt")
    symptoms = re.findall(r'"([^"]+)" to "', one(r"val symptoms = linkedMapOf\((.*?)\n    \)", sym, "symptoms").group(1))
    english = dict(re.findall(r'"([^"]+)" to "([^"]+)"', one(r"englishLabels = mapOf\((.*?)\)\n", sym, "englishLabels").group(1)))
    sites_body = one(r"val sites = linkedMapOf\((.*?)\n    \)", pain, "sites").group(1)
    sections = re.findall(r'"([^"]+)"', one(r"object NoteRules.*?val labels = listOf\((.*?)\)", data, "NoteRules.labels").group(1))
    standard = []
    for key in ("WHAT_MATTERS", "WHO_WAS_THERE", "WHAT_WE_DISCUSSED", "STILL_TO_DISCUSS", "NEXT_STEPS"):
        standard.append(re.findall(r'"([^"]+)"', one(rf"DOCTOR_STANDARD_QUESTIONS_{key} = listOf\((.*?)\)\n", prep, key).group(1)))
    ack = one(r"name\.trim\(\)\.length in 1\.\.(\d+) && relationship in listOf\((.*?)\)", data, "acknowledge rule")
    return {
        "symptom_labels": [english.get(s, s) for s in symptoms],
        "respondents": [v for _, v in english_map(sym, "respondents")],
        "recalls": [v for _, v in english_map(sym, "recalls")],
        "triggers": [v for _, v in english_map(sym, "triggers")],
        "changes": [v for _, v in english_map(sym, "changes")],
        "sites": [v for _, v in re.findall(r'"([^"]+)" to \("([^"]+)" to', sites_body)],
        "symptom_max": int(one(r"symptom\.trim\(\)\.length !in 1\.\.(\d+)", sym, "symptom length").group(1)),
        "author_max": int(one(r"author\.trim\(\)\.length !in 1\.\.(\d+)", sym, "author length").group(1)),
        "note_max": int(one(r"value\.note\.length > (\d+)", sym, "note length").group(1)),
        "location_max": int(one(r"painLocation\.length > (\d+)", sym, "pain location length").group(1)),
        "sections": sections,
        "standard": standard,
        "answer_max": int(one(r"DOCTOR_PREPARATION_MAX_ANSWER_CHARS = (\d+)", prep, "answer length").group(1)),
        "ack_name_max": int(ack.group(1)),
        "ack_relationships": re.findall(r'"([^"]+)"', ack.group(2)),
    }


def by_link(form):
    return {q["link_id"]: q for q in walk(form["questions"])}


def expect(where, q, qtype=None, required=None, max_length=None, options=None, repeats=None):
    if q is None:
        err(where, "question is missing")
        return
    if qtype and q.get("type") != qtype:
        err(where, f"type is {q.get('type')!r}, app field needs {qtype!r}")
    if required is not None and bool(q.get("required")) != required:
        err(where, f"required should be {required} to match the app")
    if max_length is not None and q.get("max_length") != max_length:
        err(where, f"max_length is {q.get('max_length')}, app limit is {max_length}")
    if options is not None and [o["value"] for o in q.get("answer_option", [])] != options:
        err(where, f"options {[o['value'] for o in q.get('answer_option', [])]} differ from app {options}")
    if repeats is not None and bool(q.get("repeats")) != repeats:
        err(where, f"repeats should be {repeats}")


def check_app_fields(forms, app):
    name = "sahara-symptom-diary.json"
    q = by_link(forms["sahara-symptom-diary"])
    expect(f"{name} [observed-at]", q.get("observed-at"), "dateTime", True)
    expect(f"{name} [respondent]", q.get("respondent"), "choice", True, options=app["respondents"])
    expect(f"{name} [recall]", q.get("recall"), "choice", True, options=app["recalls"])
    expect(f"{name} [author]", q.get("author"), "string", True, app["author_max"])
    rows = q.get("symptoms", {}).get("questions", [])
    texts = [r["text"].removesuffix(" (0-10)") for r in rows]
    if texts != app["symptom_labels"]:
        err(f"{name} [symptoms]", f"symptom rows {texts} differ from app {app['symptom_labels']}")
    for r in rows:
        expect(f"{name} [{r['link_id']}]", r, "integer", False)  # blank = "Not rated"
    expect(f"{name} [sym-other-name]", q.get("sym-other-name"), "string", False, app["symptom_max"])
    expect(f"{name} [change]", q.get("change"), "choice", False, options=app["changes"])
    expect(f"{name} [note]", q.get("note"), "text", False, app["note_max"])

    name = "sahara-pain-body-map.json"
    q = by_link(forms["sahara-pain-body-map"])
    expect(f"{name} [pain-observed-at]", q.get("pain-observed-at"), "dateTime", True)
    expect(f"{name} [pain-respondent]", q.get("pain-respondent"), "choice", True, options=app["respondents"])
    expect(f"{name} [pain-recall]", q.get("pain-recall"), "choice", True, options=app["recalls"])
    expect(f"{name} [pain-author]", q.get("pain-author"), "string", True, app["author_max"])
    expect(f"{name} [pain-spot]", q.get("pain-spot"), "group", repeats=True)
    expect(f"{name} [pain-site]", q.get("pain-site"), "choice", True, options=app["sites"])
    expect(f"{name} [pain-rating]", q.get("pain-rating"), "integer", False)  # per-spot rating is optional in the app
    expect(f"{name} [pain-location]", q.get("pain-location"), "string", False, app["location_max"])
    expect(f"{name} [pain-triggers]", q.get("pain-triggers"), "choice", False, options=app["triggers"], repeats=True)
    expect(f"{name} [pain-note]", q.get("pain-note"), "text", False, app["note_max"])

    name = "sahara-goc-conversation.json"
    form = forms["sahara-goc-conversation"]
    groups = [g for g in form["questions"] if g["link_id"].startswith("goc-sec-")]
    if [g["text"] for g in groups] != app["sections"]:
        err(name, f"section groups {[g['text'] for g in groups]} differ from app {app['sections']}")
    for g, prompts in zip(groups, app["standard"]):
        if [c["text"] for c in g["questions"]] != prompts:
            err(f"{name} [{g['link_id']}]", f"prompts differ from app {prompts}")
        for c in g["questions"]:
            expect(f"{name} [{c['link_id']}]", c, "text", False, app["answer_max"])
    q = by_link(form)
    expect(f"{name} [goc-confirmation]", q.get("goc-confirmation"), "group", repeats=True)
    expect(f"{name} [goc-confirm-name]", q.get("goc-confirm-name"), "string", True, app["ack_name_max"])
    expect(f"{name} [goc-confirm-relationship]", q.get("goc-confirm-relationship"), "choice", True, options=app["ack_relationships"])


def main():
    global ANDROID
    if "--android" in sys.argv:
        ANDROID = Path(sys.argv[sys.argv.index("--android") + 1])
    files = sorted(FORMS.glob("*.json"))
    forms = {}
    for path in files:
        form = json.loads(path.read_text(encoding="utf-8"))
        check_care_rules(path.name, form)
        forms[form.get("slug")] = form
    print(f"CARE rules: checked {len(files)} files")
    expected = {"sahara-symptom-diary", "sahara-pain-body-map", "sahara-goc-conversation"}
    if set(forms) != expected:
        err("questionnaires/", f"expected slugs {sorted(expected)}, found {sorted(forms)}")
    elif ANDROID.is_dir():
        check_app_fields(forms, app_fields())
        print(f"App fields: compared with {ANDROID}")
    else:
        print(f"WARNING: {ANDROID} not found, app-field checks skipped")
    for e in errors:
        print("FAIL", e)
    print("OK" if not errors else f"{len(errors)} problem(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
