"""Build the Sahara collaboration calendar (data.json + calendar.html).

Counts only. Chat data comes from chat_daily.json (per-day counts produced by a
separate counting pass); no message text is read here or printed in the page.
Run:  python -I build.py <chat_daily.json> <sessions_daily.json>
"""
import calendar, collections, datetime as dt, html, json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
chat_daily = json.load(open(sys.argv[1]))
sessions_daily = json.load(open(sys.argv[2]))

# ---------- git ----------
log = subprocess.run(["git", "-C", REPO, "log", "--all", "--format=%ad|%an", "--date=short"],
                     capture_output=True, text=True, check=True).stdout.split("\n")
AUTH = {"basithladdu": "basith", "Basith": "basith", "shaikawaiz2501": "awaiz"}
commits = collections.defaultdict(collections.Counter)
for l in log:
    if not l.strip():
        continue
    d, a = l.split("|", 1)
    commits[d][AUTH.get(a, "other")] += 1
    commits[d]["total"] += 1
total_commits = sum(c["total"] for c in commits.values())
first_commit, last_commit = min(commits), max(commits)
stat = subprocess.run(["git", "-C", REPO, "log", "--all", "--no-merges", "--shortstat", "--format="],
                      capture_output=True, text=True, check=True).stdout
ins = sum(int(x) for x in re.findall(r"(\d+) insertion", stat))
dels = sum(int(x) for x in re.findall(r"(\d+) deletion", stat))
msgs = subprocess.run(["git", "-C", REPO, "log", "--all", "--format=%s"], capture_output=True,
                      text=True, check=True).stdout
clin_commits = sum(1 for s in msgs.split("\n") if re.search(r"sujay|shirin|sharada|svk", s, re.I))

# ---------- curated events (each confirmed in repo docs / git / supplied dates) ----------
# kind: meet (team call), mentor (mentor/organiser), review (clinician input), release, milestone
EV = [
    ("2026-08-20", "review", "Goals-of-care idea", "Dr Sujay's one-page concept (doc creation date)"),
    ("2026-08-24", "meet", "Team meeting 1", "First team meeting, transcript saved"),
    ("2026-08-25", "milestone", "Team group starts", "Doctors and engineers start working as one group"),
    ("2026-08-30", "meet", "Team meeting 2", "Online team call"),
    ("2026-08-31", "review", "Clinician feature list", "Dr Sujay's list of patient needs"),
    ("2026-09-06", "mentor", "Organisers' webinar", "Health-a-thon Connect session"),
    ("2026-09-12", "review", "Clinical draft shared", "Dr Sharada's clinical reference and Dr Sujay's form"),
    ("2026-09-20", "review", "Shirin's review", "One care calendar, fewer clicks"),
    ("2026-09-21", "meet", "Product review call", "Team meeting, minutes saved"),
    ("2026-09-23", "meet", "Round 1 pitch call", "Team meeting, minutes saved"),
    ("2026-09-24", "review", "Dr Sujay's feedback", "Morning feedback on the deck"),
    ("2026-09-25", "milestone", "Round 1 answers", "Revised portal answers saved"),
    ("2026-10-09", "milestone", "Top 120 shortlist", "Shortlisted from 450+ submissions"),
    ("2026-10-14", "release", "Closed testing 0.4.1", "Play Store closed testing"),
    ("2026-10-20", "mentor", "Mentor meeting", "Mentor session with test APK"),
    ("2026-10-23", "review", "Hospital survey", "Dr Sujay's survey follow-up"),
    ("2026-10-28", "meet", "Team meeting", "Goals-of-care sign-off decisions"),
    ("2026-10-29", "review", "Dr Sharada's specs", "Clinical and tech specifications"),
    ("2026-11-03", "meet", "Team meeting", "Changes built 4 Nov"),
    ("2026-11-07", "milestone", "Final submission pack", "Brief and deck, Dr Sujay's edits"),
    ("2026-11-09", "release", "Production 0.7.1", "Live on the Play Store"),
    ("2026-11-21", "review", "Dr Sujay's photo review", "Device test, six screens"),
    ("2026-11-22", "review", "Video reviews", "Dr Sujay's videos; Shirin's and Dr Sharada's notes"),
    ("2026-11-22", "meet", "Team meeting", "Pitch plan and field-test plan"),
    ("2026-11-23", "mentor", "Organisers' briefing", "Finale format and jury"),
    ("2026-11-23", "release", "0.8.0 in review", "Sent to Play Store review"),
    ("2026-11-24", "meet", "Team meeting", "Screen by screen, with the doctors"),
    ("2026-11-25", "release", "Sahara 0.8.2", "Simple Mode, 6 more languages"),
    ("2026-11-27", "meet", "Pitch rehearsal", "Deck and demo plan, in person"),
    ("2026-11-28", "dday", "D-DAY: Grand Finale", "IIT Bombay"),
]
events = collections.defaultdict(list)
for d, k, label, detail in EV:
    events[d].append({"kind": k, "label": label, "detail": detail})

# ---------- per-day table ----------
start, end = dt.date(2026, 8, 1), dt.date(2026, 11, 30)
days = {}
d = start
while d <= end:
    k = d.isoformat()
    c = commits.get(k, {})
    ch = chat_daily.get(k, {})
    days[k] = {
        "commits": c.get("total", 0), "basith": c.get("basith", 0), "awaiz": c.get("awaiz", 0),
        "chat": ch.get("total", 0), "chat_clin": ch.get("clin", 0), "chat_eng": ch.get("eng", 0),
        "ai": sessions_daily.get(k, 0), "events": events.get(k, []),
    }
    d += dt.timedelta(days=1)

chat_total = sum(v.get("total", 0) for v in chat_daily.values())
chat_people = collections.Counter()
for v in chat_daily.values():
    for kk, n in v.items():
        if kk.startswith("p_"):
            chat_people[kk[2:]] += n
kinds = collections.Counter(e[1] for e in EV)
stats = {
    "commits": total_commits, "basith": sum(c["basith"] for c in commits.values()),
    "awaiz": sum(c["awaiz"] for c in commits.values()),
    "build_days": len(commits), "first": first_commit, "last": last_commit,
    "ins": ins, "dels": dels, "clin_commits": clin_commits,
    "team_meetings": kinds["meet"], "mentor": kinds["mentor"], "reviews": kinds["review"],
    "releases": kinds["release"], "chat": chat_total, "chat_days": len(chat_daily),
    "chat_people": dict(chat_people),
    "chat_clin": sum(v.get("clin", 0) for v in chat_daily.values()),
    "chat_eng": sum(v.get("eng", 0) for v in chat_daily.values()),
    "ai_sessions": sum(sessions_daily.values()), "ai_days": len(sessions_daily),
    "active_days": sum(1 for v in days.values() if v["commits"] or v["chat"] or v["events"] or v["ai"]),
    "meetings_reviews": kinds["meet"] + kinds["review"],
}
json.dump({"stats": stats, "days": days}, open(os.path.join(HERE, "data.json"), "w"), indent=1)

# ---------- HTML ----------
E = html.escape
MON = {8: "August", 9: "September", 10: "October", 11: "November"}
KCOL = {"meet": "var(--indigo)", "mentor": "var(--indigo)", "review": "var(--rose)",
        "release": "var(--gold)", "milestone": "var(--sage)", "dday": "var(--sage)"}

def heat(n):
    if n == 0: return "h0"
    if n <= 3: return "h1"
    if n <= 8: return "h2"
    if n <= 20: return "h3"
    return "h4"

def fmt(n): return f"{n:,}"

ICON_CHAT = '<svg viewBox="0 0 12 12" class="ic"><path d="M2 2h8a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H5l-3 2V9H2a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z" fill="none" stroke="currentColor" stroke-width="1.2"/></svg>'
ICON_AI = '<svg viewBox="0 0 12 12" class="ic"><path d="M6 1l1.3 3.2L10.5 5.5 7.3 6.8 6 10 4.7 6.8 1.5 5.5 4.7 4.2z" fill="currentColor"/></svg>'

# Deck-style ("Build Sprint Journey") month page: joined white grid on sand, Week N labels,
# coloured chips with white bold text. Teal "built" chips for 9 Oct - 7 Nov are taken from the
# final submission deck's sprint calendar (output/final-submission/source/build_deck_v3.py),
# which was itself built from the git history.
BUILT = {
    # September (git log subjects for the day)
    "2026-09-08": ["First prototype"], "2026-09-09": ["Webinar notes saved"],
    "2026-09-13": ["Patient portal", "Nearby-care map"], "2026-09-14": ["Care UI refresh", "Plainer labels"],
    "2026-09-15": ["Voice dictation", "Hindi patient portal", "Offline QR scanner"],
    "2026-09-16": ["Symptom tracker (ESAS-r)", "Care journey timeline", "Home care protocols"],
    "2026-09-18": ["Care map + QR bridge", "Bleeding crisis guide"],
    "2026-09-21": ["Care map pins", "Role-based sign-in", "Caregiver handover"],
    "2026-09-22": ["Minutes into caregiver brief"],
    "2026-09-23": ["Caregiver care flows", "Web app deployed"],
    "2026-09-24": ["Care conversation requests", "Pitch deck visuals"],
    "2026-09-25": ["Pitch deck reordered"], "2026-09-26": ["App and deck refresh"],
    "2026-09-27": ["Dr Sharada's deck review"],
    # October
    "2026-10-02": ["Karnataka care directory", "Shared hospital reports"],
    "2026-10-09": ["Native Android + map"],
    "2026-10-10": ["Safe account deletion", "Medicine organiser", "Sahara branding"],
    "2026-10-11": ["Prescription organiser", "Web features on Android"], "2026-10-13": ["Scanner verified"],
    "2026-10-14": ["Testers invited"], "2026-10-16": ["Sahara 0.5 alpha", "Privacy and safety"],
    "2026-10-19": ["Hospital survey live", "On-device processing", "Feedback form"],
    "2026-10-20": ["National care directory"],
    "2026-10-21": ["Release verified", "Research archive"], "2026-10-22": ["Daily health workflow"],
    "2026-10-23": ["Phase 2 deck"], "2026-10-24": ["Symptom diary", "3D pain locations"],
    "2026-10-25": ["Symptom tracker", "Navigation, feedback"],
    "2026-10-26": ["Pain body map", "Motion and loading"],
    "2026-10-27": ["Hindi + offline speech", "Accessibility pass", "In-app account deletion"],
    "2026-10-28": ["Ask your documents", "In-app doctor approval"],
    "2026-10-29": ["Survey admin page"],
    "2026-10-30": ["Emergency care plan", "Emergency QR card", "Telugu, Urdu, Kannada"],
    "2026-10-31": ["Patient summary", "Care timeline", "Side-effect notes"],
    # November
    "2026-11-01": ["Goals-of-care recording", "Today / Care redesign"],
    "2026-11-02": ["Doctor schedule", "Patient profile"],
    "2026-11-04": ["Meeting changes", "8-tap goals of care", "Clinical document"],
    "2026-11-05": ["AI + backup models", "Offline Whisper", "Final deck v2"],
    "2026-11-06": ["Community groups", "910 tests pass", "Illustrations, animations"],
    "2026-11-07": ["Caregiver wellbeing", "Dr Sujay's fixes"],
    "2026-11-08": ["Sahara 0.7.0 build", "Final deck and brief"],
    "2026-11-09": ["Sahara feature film"], "2026-11-10": ["Sahara journey film"],
    "2026-11-21": ["Next-visit dialog fix"], "2026-11-22": ["Video-review fixes"],
    "2026-11-23": ["First-run care details", "UI revamp"],
    "2026-11-24": ["Simple Mode", "Calmer UI"],
    "2026-11-25": ["6 more languages", "Smoother native feel"],
    "2026-11-26": ["Patient-side redesign", "Tighter Today cards"],
    "2026-11-27": ["Doctor-side redesign", "Searchable lists"],
}
CHIP = {"meet": "c-meet", "mentor": "c-meet", "review": "c-review", "release": "c-mile", "milestone": "c-mile"}

def shape(kind):
    if kind in ("meet", "mentor"): return '<span class="mk dot meet"></span>'
    if kind == "review": return '<span class="mk dot review"></span>'
    return '<span class="mk dot mile"></span>'

def week_no(day):
    return (day - dt.date(2026, 8, 3)).days // 7 + 1

def month_page(m):
    cal = calendar.Calendar(firstweekday=0).monthdatescalendar(2026, m)
    mdays = [v for k, v in days.items() if int(k[5:7]) == m]
    mc = sum(v["commits"] for v in mdays)
    mad = sum(1 for v in mdays if v["commits"])
    mch = sum(v["chat"] for v in mdays)
    mmeet = sum(1 for v in mdays for e in v["events"] if e["kind"] in ("meet", "mentor"))
    mrev = sum(1 for v in mdays for e in v["events"] if e["kind"] == "review")
    mai = sum(v["ai"] for v in mdays)
    mbuilt = sum(len(b) for k, b in BUILT.items() if int(k[5:7]) == m)
    maxchips = 4
    cells = ['<div class="wk"></div>'] + [f'<div class="wd">{w}</div>' for w in ["MON","TUE","WED","THU","FRI","SAT","SUN"]]
    for week in cal:
        cells.append(f'<div class="wk">Week {week_no(week[0])}</div>')
        for day in week:
            k = day.isoformat()
            if day.month != m:
                cells.append(f'<div class="cell out"><span class="dn">{day.day}</span></div>')
                continue
            v = days[k]
            if any(e["kind"] == "dday" for e in v["events"]):
                cells.append(f'<div class="cell dday"><span class="ddc">{day.day}</span><div class="ddbig">D-DAY</div>'
                             '<div class="ddsub">Grand Finale · IIT Bombay</div></div>')
                continue
            cls = ["cell", heat(v["commits"])]
            if day > dt.date(2026, 11, 27): cls.append("future")
            chips = [(CHIP[e["kind"]], e["label"]) for e in v["events"]]
            chips += [("c-built", b) for b in BUILT.get(k, [])]
            chips = chips[:maxchips]
            chip_html = "".join(f'<div class="chip {c}">{E(t)}</div>' for c, t in chips)
            com = f'<span class="cc">{v["commits"]} commit{"s" if v["commits"] != 1 else ""}</span>' if v["commits"] else ""
            meta = []
            if v["ai"]: meta.append(f'<span class="ai">{ICON_AI}{v["ai"]}</span>')
            cells.append(f'<div class="{" ".join(cls)}"><div class="top"><span class="dn">{day.day}</span>{com}</div>'
                         f'<div class="chips">{chip_html}</div><div class="meta">{"".join(meta)}</div></div>')
    evs = [(k, e) for k, v in days.items() if int(k[5:7]) == m for e in v["events"]]
    evlist = "".join(
        f'<li><span class="ed">{int(k[8:])} {MON[m][:3]}</span>{shape(e["kind"])}'
        f'<span class="et"><b>{E(e["label"])}</b><br><span class="edt">{E(e["detail"])}</span></span></li>'
        for k, e in evs)
    note = {8: "The idea phase: two team meetings and the first clinical notes.",
            9: "Clinicians shape the product; the Round 1 pitch comes out of two review meetings.",
            10: "Shortlisted. The first test build reaches real phones; mentor review; hospital survey.",
            11: "Final submission, production release, doctors' device reviews, pitch rehearsal, then the finale."}[m]
    built_note = '<div class="small muted">Teal chips: what was built or done that day, from the git history and project notes.</div>'
    built_note += '<div class="small muted">Sources: git history, meeting minutes, review notes, Play Console dates.</div>'
    return f'''
<section class="page month">
  <header class="mhead">
    <div><div class="kicker">Team Zeros and Ones · How we built Sahara together</div><h2>{MON[m]} 2026</h2><div class="mnote">{note}</div></div>
    <div class="mstats">
      <div><b>{mbuilt}</b><span>built or done</span></div>
      <div><b>{mad}</b><span>build days</span></div>
      <div class="sec"><b>{mc}</b><span>commits</span></div>
      <div><b>{mai}</b><span>AI sessions</span></div>
    </div>
  </header>
  <div class="mbody">
    <div class="grid rows{len(cal)}">{''.join(cells)}</div>
    <aside class="side">
      <div class="scard"><h3>Key days</h3>
      <ul class="evl{' long' if len(evs) > 9 else ''}">{evlist or '<li class="muted">No logged meetings.</li>'}</ul></div>
      {built_note}
    </aside>
  </div>
  {LEGEND}
</section>'''

LEGEND = f'''<footer class="legend">
  <span class="lg heatlg"><span class="sw h1"></span><span class="sw h2"></span><span class="sw h3"></span><span class="sw h4"></span>&nbsp;commits per day (1–3, 4–8, 9–20, 21+)</span>
  <span class="lg"><span class="chip c-meet mini">Meeting</span>team / mentor / organisers</span>
  <span class="lg"><span class="chip c-review mini">Doctor</span>doctor review or input</span>
  <span class="lg"><span class="chip c-mile mini">Milestone</span>release or milestone</span>
  <span class="lg"><span class="chip c-built mini">Built</span>built or done</span>
  <span class="lg ai">{ICON_AI}&nbsp;AI-assisted sessions</span>
</footer>'''

s = stats
# Cover tile only: the owner supplied 25 as the true number of team meetings and doctor reviews
# (27 Nov 2026). The EV log above is incomplete (it gives stats["meetings_reviews"]), so the
# cover does not use the computed count. The month pages still count from EV.
COVER_MEETINGS_REVIEWS = 25
cover = f'''
<section class="page cover">
  <div class="brand"><span class="tn">Team Zeros and Ones</span> Health-a-thon 2026 Grand Finale · IIT Bombay · 28 Nov 2026</div>
  <h1>How we built Sahara together</h1>
  <div class="sub">Clinicians and technologists, August to November 2026</div>
  <div class="tiles">
    <div class="tile big"><b>{fmt(s["commits"])}</b><span>code commits</span><i>{int(s["first"][8:])} Aug to {int(s["last"][8:])} Nov, from the git history</i></div>
    <div class="tile rose"><b>{COVER_MEETINGS_REVIEWS}</b><span>team meetings and doctor reviews</span><i>clinicians and technologists together</i></div>
    <div class="tile"><b>{s["build_days"]}</b><span>active build days</span><i>days with code committed</i></div>
    <div class="tile"><b>{s["mentor"]}</b><span>mentor and organiser sessions</span><i>webinar, mentor review, finale briefing</i></div>
    <div class="tile"><b>{s["ai_sessions"]}</b><span>AI-assisted build sessions</span><i>on {s["ai_days"]} days, incl. agent sub-tasks</i></div>
    <div class="tile gold"><b>{s["releases"]}</b><span>Play Store releases</span><i>0.4.1 closed test, 0.7.1 production, 0.8.0, 0.8.2</i></div>
    <div class="tile live"><b>LIVE</b><span>Live on the website</span><span class="l2">sahara.wedevit.in</span></div>
    <div class="tile playcard"><div class="ph2"><svg viewBox="0 0 24 24" class="play"><polygon points="3,2 13,12 3,22" fill="#00C3FF"/><polygon points="3,2 16.5,9.5 13,12" fill="#00E676"/><polygon points="3,22 13,12 16.5,14.5" fill="#FF3A44"/><polygon points="16.5,9.5 21,12 16.5,14.5 13,12" fill="#FFD500"/></svg><div><span class="gp">Google Play</span><span class="lv">Live on Google Play</span></div><div class="appicon"></div></div>
      <div class="shot"></div><i>Play Store listing · production since 9 Nov 2026</i></div>
  </div>
  <div class="started">
    <div class="sh">How it started<br><span>August 2026</span></div>
    <div class="si"><b>1–3 Aug</b>Doctors and healthcare professionals register</div>
    <div class="si"><b>15 Aug</b>Tech team registration begins</div>
    <div class="si"><b>20 Aug</b>Goals-of-care idea (Dr Sujay)</div>
    <div class="si"><b>22 Aug</b>Matchmaking begins</div>
    <div class="si"><b>24 Aug</b>First team meeting</div>
  </div>
  <div class="story">
    <div class="step"><span class="n">1</span><div><b>Idea from the bedside.</b> Dr Sujay's goals-of-care concept and the first team meetings in August set the problem: a patient's and caregiver's wishes never reach the emergency room.</div></div>
    <div class="step"><span class="n">2</span><div><b>Clinician-led reviews.</b> Dr Sharada, Shirin and Dr Sujay wrote the clinical reference, specs and survey, and reviewed every pitch and screen.</div></div>
    <div class="step"><span class="n">3</span><div><b>Build sprints to field testing.</b> The technologists turned each review into builds, from closed testing in October to production in November and doctors testing on their own phones.</div></div>
  </div>
  <div class="team5">
    <div class="th">Team<br>Zeros and Ones</div>
    <div class="tp clin"><b>Dr Sujay H S</b><span>Clinician</span></div>
    <div class="tp clin"><b>Dr Sharada Vinod Kutty</b><span>Clinician</span></div>
    <div class="tp clin"><b>Shirin Ayub</b><span>Medical student</span></div>
    <div class="tp tech"><b>Shaik Abdul Basith</b><span>Technologist · tech lead</span></div>
    <div class="tp tech"><b>Shaik Awaiz</b><span>Technologist</span></div>
  </div>
</section>'''

css = open(os.path.join(HERE, "style.css"), encoding="utf-8").read()
page = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Sahara Collaboration Calendar</title>
<style>{css}</style></head><body>
{cover}{"".join(month_page(m) for m in (9, 10, 11))}
</body></html>'''
open(os.path.join(HERE, "calendar.html"), "w", encoding="utf-8").write(page)
open(os.path.join(HERE, "calendar-print.html"), "w", encoding="utf-8").write(page.replace("<body>", '<body class="saver">', 1))
three = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Sahara: three months, September to November 2026</title>
<style>{css}</style></head><body>
{"".join(month_page(m) for m in (9, 10, 11))}
</body></html>'''
open(os.path.join(HERE, "calendar-3-months.html"), "w", encoding="utf-8").write(three)
print(json.dumps(stats, indent=1))
