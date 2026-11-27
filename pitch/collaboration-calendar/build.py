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
    ("2026-08-25", "milestone", "Team group starts", "Doctors and engineers start the group chat"),
    ("2026-08-30", "meet", "Team meeting 2", "Online team call"),
    ("2026-08-31", "review", "Clinician feature list", "Dr Sujay's list of patient needs"),
    ("2026-09-06", "mentor", "Organisers' webinar", "Health-a-thon Connect session"),
    ("2026-09-12", "review", "Clinical draft shared", "Dr Sharada's clinical reference and Dr Sujay's form"),
    ("2026-09-20", "review", "Dr Shirin's review", "One care calendar, fewer clicks"),
    ("2026-09-21", "meet", "Product review call", "Team meeting, minutes saved"),
    ("2026-09-23", "meet", "Round 1 pitch call", "Team meeting, minutes saved"),
    ("2026-09-24", "review", "Dr Sujay's feedback", "Morning feedback on the deck"),
    ("2026-09-25", "milestone", "Round 1 answers", "Revised portal answers saved"),
    ("2026-09-27", "review", "Dr Sharada's review", "Round 1 deck and story"),
    ("2026-10-09", "milestone", "Top 120 shortlist", "Shortlisted from 450+ submissions"),
    ("2026-10-14", "release", "Closed testing 0.4.1", "Play Store closed testing"),
    ("2026-10-19", "release", "Production 0.4.1", "First public release, Google Play India"),
    ("2026-10-22", "release", "Production 0.5.0", "100% India rollout, live by 24 Oct"),
    ("2026-10-20", "mentor", "Mentor meeting", "Mentor session with test APK"),
    ("2026-10-23", "review", "Hospital survey", "Dr Sujay's survey follow-up"),
    ("2026-10-24", "review", "Symptom list agreed", "Dr Shirin with Dr Sharada and Dr Sujay"),
    ("2026-10-28", "meet", "Team meeting", "Goals-of-care sign-off decisions"),
    ("2026-10-29", "review", "Dr Sharada's specs", "Clinical and tech specifications"),
    ("2026-10-31", "review", "Legal review", "Legal comments on the form"),
    ("2026-11-01", "review", "Doctors' app notes", "Dr Sujay and Dr Sharada on the app"),
    ("2026-11-03", "meet", "Team meeting", "Transcript saved, changes built 4 Nov"),
    ("2026-11-06", "review", "Dr Shirin's notes", "Family and caregiver screens"),
    ("2026-11-07", "milestone", "Final submission pack", "Brief and deck with Dr Sujay's edits"),
    ("2026-11-07", "review", "Dr Sujay's review", "13-item checklist and wording, built that day"),
    ("2026-11-09", "release", "Production 0.7.1", "Live on the Play Store"),
    ("2026-11-18", "milestone", "Finale shortlist", "Selected for the Grand Finale"),
    ("2026-11-20", "meet", "Team meeting", "Finale deck and survey plan"),
    ("2026-11-21", "review", "Dr Sujay's photo review", "Device test, six screens"),
    ("2026-11-22", "review", "Video reviews", "Dr Sujay's two videos; notes from Dr Shirin and Dr Sharada"),
    ("2026-11-22", "meet", "Team meeting", "Pitch plan and field-test plan"),
    ("2026-11-23", "mentor", "Organisers' briefing", "Finale format and jury"),
    ("2026-11-23", "release", "0.8.0 in review", "Sent to Play Store review"),
    ("2026-11-23", "review", "Doctors' notes", "Alerts, consent, caregiving changes"),
    ("2026-11-24", "meet", "Team meeting", "Changes built the same night"),
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
    "active_days": sum(1 for k, v in days.items() if k <= "2026-11-27" and (v["commits"] or v["chat"] or v["events"] or v["ai"])),
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
    "2026-08-07": ["Hackathon brief saved"],
    "2026-09-08": ["First web prototype"], "2026-09-09": ["Webinar notes saved"],
    "2026-09-13": ["Patient portal + map", "ED Quick View"], "2026-09-14": ["Familiar labels", "Note summaries kept"],
    "2026-09-15": ["Escalation plan grid", "Offline QR scanner", "Hindi patient side"],
    "2026-09-16": ["Clinician tool trials", "ESAS-r symptom radar", "Oncology emergencies"],
    "2026-09-18": ["20 simulated talks", "Bleeding crisis guide"],
    "2026-09-21": ["Family care pages", "Role-based sign-in", "Signed notes, handover"],
    "2026-09-22": ["Meeting follow-up"], "2026-09-23": ["Website deployed", "Family care flows"],
    "2026-09-24": ["Care-talk requests", "Round 1 deck rebuilt"], "2026-09-25": ["Dr Sujay's deck edits"],
    "2026-09-26": ["App + deck refresh"], "2026-09-27": ["Pitch story revised"],
    "2026-10-02": ["Karnataka directory", "Shared hospital reports"], "2026-10-05": ["Project checkpoint"],
    "2026-10-09": ["Native Android + map"], "2026-10-10": ["Safe account deletion", "Renamed Sahara", "Account recovery"],
    "2026-10-11": ["Prescription organiser", "Web features on Android"], "2026-10-13": ["Scanner verified"],
    "2026-10-14": ["Testers invited"], "2026-10-15": ["Play support follow-up"],
    "2026-10-16": ["Sahara 0.5 alpha", "Privacy and safety", "Care tasks + labs"],
    "2026-10-19": ["On-device processing", "Hospital survey", "Feedback forms"],
    "2026-10-20": ["National care directory", "On-phone Hindi tools"],
    "2026-10-21": ["Release verified", "Research archive"], "2026-10-22": ["Daily health workflow"],
    "2026-10-23": ["Phase 2 deck"], "2026-10-24": ["Symptom diary (0.6)", "3D pain locations", "New walkthroughs"],
    "2026-10-25": ["Symptom tracker", "Navigation, feedback"],
    "2026-10-26": ["Pain body map", "Motion and loading", "Family invite link"],
    "2026-10-27": ["Hindi + offline speech", "1,631 care centres", "Doctor workspace", "Web-link QR card"],
    "2026-10-28": ["Ask your documents", "Distress thermometer", "Pain bands + trends", "In-app doctor approval"],
    "2026-10-29": ["Hospital survey admin", "Spec groundwork", "Smaller Play download"],
    "2026-10-30": ["Goals of care + QR", "Telugu, Urdu, Kannada", "Emergency care plan"],
    "2026-10-31": ["Patient summary", "Side-effect notes", "Care timeline"],
    "2026-11-01": ["Recording to text", "Today / Care redesign", "Legal comments in app"],
    "2026-11-02": ["Doctor schedule", "Patient profile"],
    "2026-11-04": ["Clinical document", "8 practice talks pass", "3 Nov meeting changes"],
    "2026-11-05": ["AI + backup models", "Offline Whisper"],
    "2026-11-06": ["Community groups", "910 tests pass", "Pre-filled forms"],
    "2026-11-07": ["Caregiver wellbeing", "13-item checklist", "Medicine safety fixes"],
    "2026-11-08": ["0.7.0 build for Play", "Website catches up"],
    "2026-11-09": ["Sahara feature film", "Care plan step removed"], "2026-11-10": ["Journey film v2"],
    "2026-11-21": ["Visit dialog fix"],
    "2026-11-22": ["Video-review fixes", "One goals-of-care entry", "Surprise-question prompt"],
    "2026-11-23": ["First-run care details", "Under-18 care plans", "Doctor alert pop-up"],
    "2026-11-24": ["Simple Mode, 60+", "Calmer UI", "Caregiving changes"],
    "2026-11-25": ["Sahara 0.8.2", "Six more languages", "Smoother native feel"],
    "2026-11-26": ["Patient-side redesign", "Tighter Today screen"],
    "2026-11-27": ["Doctor-side redesign", "24 Nov follow-ups", "Searchable lists"],
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
    maxchips = 4 if len(cal) <= 5 else 3
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
            if v["chat"]: meta.append(f'<span class="chat">{ICON_CHAT}{v["chat"]}</span>')
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
            10: "Shortlisted. Closed testing on 14 Oct, live on Google Play from 19 Oct; mentor review; hospital survey.",
            11: "Final submission, finale shortlist, doctors' device and video reviews, two redesigns, then the finale."}[m]
    chat_note = '<div class="small muted">Chat counts cover the exported group chat (25 Aug to 23 Nov).</div>' if m in (8, 11) else ""
    built_note = '<div class="small muted">Teal chips: what was built that day, from the git history and the team\'s dated change log.</div>'
    return f'''
<section class="page month">
  <header class="mhead">
    <div><div class="kicker">How we built Sahara together</div><h2>{MON[m]} 2026</h2><div class="mnote">{note}</div></div>
    <div class="mstats">
      <div><b>{mc}</b><span>commits</span></div>
      <div><b>{mad}</b><span>build days</span></div>
      <div><b>{mmeet}</b><span>meetings</span></div>
      <div><b>{mrev}</b><span>doctor inputs</span></div>
      <div><b>{mch}</b><span>chat messages</span></div>
      <div><b>{mai}</b><span>AI sessions</span></div>
    </div>
  </header>
  <div class="mbody">
    <div class="grid rows{len(cal)}">{''.join(cells)}</div>
    <aside class="side">
      <div class="scard"><h3>Key days</h3>
      <ul class="evl">{evlist or '<li class="muted">No logged meetings.</li>'}</ul></div>
      {chat_note}{built_note}
    </aside>
  </div>
  {LEGEND}
</section>'''

LEGEND = f'''<footer class="legend">
  <span class="lg"><span class="sw h1"></span><span class="sw h2"></span><span class="sw h3"></span><span class="sw h4"></span>&nbsp;commits per day (1–3, 4–8, 9–20, 21+)</span>
  <span class="lg"><span class="chip c-meet mini">Meeting</span>team / mentor / organisers</span>
  <span class="lg"><span class="chip c-review mini">Doctor</span>doctor or legal review</span>
  <span class="lg"><span class="chip c-mile mini">Milestone</span>release or milestone</span>
  <span class="lg"><span class="chip c-built mini">Built</span>built that day</span>
  <span class="lg chat">{ICON_CHAT}&nbsp;group-chat messages</span>
  <span class="lg ai">{ICON_AI}&nbsp;AI-assisted build sessions</span>
  <span class="src">Sources: git history, chat message counts, meeting minutes, review notes, Play Console dates</span>
</footer>'''

s = stats
cp = s["chat_people"]
cover = f'''
<section class="page cover">
  <div class="brand">Team Zeros and Ones · Health-a-thon 2026 Grand Finale · IIT Bombay · 28 Nov 2026</div>
  <h1>How we built Sahara together</h1>
  <div class="sub">Doctors and engineers, 7 Aug to 28 Nov 2026</div>
  <div class="tiles">
    <div class="tile big"><b>{fmt(s["commits"])}</b><span>code commits</span><i>{s["build_days"]} active build days</i></div>
    <div class="tile"><b>{s["team_meetings"]}</b><span>team meetings</span><i>doctors and engineers together</i></div>
    <div class="tile"><b>{s["mentor"]}</b><span>mentor and organiser sessions</span><i>webinar, mentor review, finale briefing</i></div>
    <div class="tile rose"><b>{s["reviews"]}</b><span>logged doctor and legal reviews</span><i>concept, specs, survey, legal advice, device tests</i></div>
    <div class="tile"><b>{fmt(s["chat"])}</b><span>group-chat messages</span><i>{s["chat_days"]} days, 25 Aug to 23 Nov</i></div>
    <div class="tile"><b>{s["ai_sessions"]}</b><span>AI-assisted build sessions</span><i>on {s["ai_days"]} days to 23 Nov, incl. agent sub-tasks</i></div>
    <div class="tile gold"><b>{s["releases"]}</b><span>Play Store releases</span><i>closed test, production 0.4.1, 0.5.0, 0.7.1, then 0.8.0</i></div>
  </div>
  <div class="story">
    <div class="step"><span class="n">1</span><div><b>Idea from the bedside.</b> Dr Sujay's goals-of-care concept and two August team meetings set the problem: a family's wishes never reach the emergency room.</div></div>
    <div class="step"><span class="n">2</span><div><b>Clinician-led reviews.</b> Dr Sharada, Dr Shirin and Dr Sujay wrote the clinical reference, specs and survey, and reviewed every pitch and screen.</div></div>
    <div class="step"><span class="n">3</span><div><b>Build sprints to field testing.</b> The engineers turned each review into builds, from closed testing and the first live release in October to doctors testing new builds on their own phones in November.</div></div>
  </div>
  <div class="team">
    <div><span class="tag rose">Clinicians</span>Dr Sujay H S · Dr Sharada Vinod Kutty · Dr Shirin Ayub</div>
    <div><span class="tag">Engineers</span>Shaik Abdul Basith (tech lead) · Shaik Awaiz</div>
  </div>
</section>'''

timeline_items = [
    ("20 Aug", "review", "Goals-of-care concept"), ("24 Aug", "meet", "First team meeting"),
    ("12 Sep", "review", "Clinical reference shared"), ("21–23 Sep", "meet", "Two review meetings"),
    ("25 Sep", "milestone", "Round 1 answers"), ("9 Oct", "milestone", "Top 120 shortlist"),
    ("14 Oct", "release", "Closed testing 0.4.1"), ("19 Oct", "release", "Live on Google Play"), ("20 Oct", "mentor", "Mentor meeting"),
    ("29 Oct", "review", "Dr Sharada's specs"), ("7 Nov", "milestone", "Final submission pack"),
    ("9 Nov", "release", "Production 0.7.1"), ("18 Nov", "milestone", "Finale shortlist"), ("21–22 Nov", "review", "Doctors' device reviews"),
    ("23 Nov", "release", "0.8.0 in review"), ("24 Nov", "meet", "Team meeting"), ("28 Nov", "dday", "D-DAY: Grand Finale"),
]
tl = "".join(
    f'<div class="ti {"dd" if k=="dday" else ""}"><div class="tdot k-{k}"></div><div class="td">{E(d)}</div><div class="tt">{E(t)}</div></div>'
    for d, k, t in timeline_items)

def bar(label, n, tot, cls):
    w = max(2, round(100 * n / tot)) if tot else 0
    return f'<div class="bar"><span class="bl">{E(label)}</span><span class="bt"><span class="bf {cls}" style="width:{w}%"></span></span><span class="bn">{fmt(n)}</span></div>'

maxchat = max(cp.values())
wk0 = dt.date(2026, 8, 3)
weeks = []
w = wk0
while w <= dt.date(2026, 11, 23):
    ks = [(w + dt.timedelta(days=i)).isoformat() for i in range(7)]
    weeks.append((w, sum(days[k]["commits"] for k in ks if k in days), sum(days[k]["chat_clin"] for k in ks if k in days),
                  sum(days[k]["chat_eng"] for k in ks if k in days),
                  sum(1 for k in ks if k in days for e in days[k]["events"] if e["kind"] in ("meet","mentor")),
                  sum(1 for k in ks if k in days for e in days[k]["events"] if e["kind"]=="review")))
    w += dt.timedelta(days=7)
W, H, mid = 1000, 122, 64
bw = W / len(weeks)
mc_ = max(x[1] for x in weeks); mch_ = max(x[2] + x[3] for x in weeks)
svg = [f'<svg viewBox="0 0 {W} {H}" class="pulse">']
for i, (wd, c, cc, ce, mt, rv) in enumerate(weeks):
    x = i * bw + bw * 0.18; bwid = bw * 0.64
    hc = 50 * c / mc_
    svg.append(f'<rect x="{x:.1f}" y="{mid-2-hc:.1f}" width="{bwid:.1f}" height="{hc:.1f}" rx="2" fill="#3B7562"/>')
    if c: svg.append(f'<text x="{x+bwid/2:.1f}" y="{mid-5-hc:.1f}" class="pv">{c}</text>')
    he = 32 * ce / mch_; hcl = 32 * cc / mch_
    svg.append(f'<rect x="{x:.1f}" y="{mid+2:.1f}" width="{bwid:.1f}" height="{hcl:.1f}" fill="#7353AC"/>')
    svg.append(f'<rect x="{x:.1f}" y="{mid+2+hcl:.1f}" width="{bwid:.1f}" height="{he:.1f}" fill="#8FA89C"/>')
    for j in range(mt):
        svg.append(f'<circle cx="{x+bwid/2 - (mt-1)*5 + j*10:.1f}" cy="{H-15}" r="4" fill="#4357C7"/>')
    for j in range(rv):
        svg.append(f'<circle cx="{x+bwid/2 - (rv-1)*5 + j*10:.1f}" cy="{H-5}" r="4" fill="#7353AC"/>')
svg.append(f'<line x1="0" x2="{W}" y1="{mid}" y2="{mid}" stroke="#6B7A73" stroke-width="1"/></svg>')
wlabels = "".join(f'<span>{wd.day} {wd.strftime("%b")}</span>' for wd, *_ in weeks)
pulse = f'''<div class="pulsebox"><div class="ph"><b>Week by week</b>
<span class="lg"><span class="sw" style="background:#3B7562"></span>commits</span>
<span class="lg"><span class="sw" style="background:#7353AC"></span>chat: clinicians</span>
<span class="lg"><span class="sw" style="background:#8FA89C"></span>chat: engineers</span>
<span class="lg"><span class="mk dot meet"></span>meeting</span><span class="lg"><span class="mk dot review"></span>doctor review</span>
<span class="muted" style="margin-left:auto">weeks starting Monday; chat export ends 23 Nov</span></div>
{"".join(svg)}<div class="wl">{wlabels}</div></div>'''
last = f'''
<section class="page last">
  <header class="mhead"><div><div class="kicker">How we built Sahara together</div><h2>Milestones and who did what</h2></div></header>
  <div class="timeline">{tl}</div>
  <div class="split">
    <div class="col clin">
      <h3><span class="tag rose">Clinicians</span> set the direction and checked the work</h3>
      <ul>
        <li><b>Reviews and inputs:</b> {s["reviews"]-1} logged doctor inputs plus 1 legal review, from the first concept (20 Aug) to the device, video and written reviews (21–23 Nov).</li>
        <li><b>Questionnaires and survey:</b> the clinical form that guided Round 1 (Sep) and the hospital survey the doctors ran (Oct).</li>
        <li><b>Goals-of-care content:</b> the clinical reference, the specifications and the wording of the doctor's note.</li>
        <li><b>Field tests:</b> testing the Play Store build on their own phones, with photo and video reviews.</li>
      </ul>
      <div class="bars">
        <div class="bh">Group-chat messages</div>
        {bar("Dr Sujay H S", cp.get("sujay",0), maxchat, "rose")}
        {bar("Dr Sharada", cp.get("sharada",0), maxchat, "rose")}
        {bar("Dr Shirin Ayub", cp.get("shirin",0), maxchat, "rose")}
      </div>
    </div>
    <div class="col eng">
      <h3><span class="tag">Engineers</span> turned each review into a build</h3>
      <ul>
        <li><b>Builds:</b> {fmt(s["commits"])} commits on {s["build_days"]} days, {s["first"][8:].lstrip("0")} Aug to {int(s["last"][8:])} Nov; {fmt(s["ins"])} lines added.</li>
        <li><b>Releases:</b> closed testing 0.4.1 (14 Oct); production 0.4.1 (19 Oct), 0.5.0 (22 Oct) and 0.7.1 (9 Nov); 0.8.0 sent for review (23 Nov).</li>
        <li><b>Acting on feedback:</b> the 21 Sep, 28 Oct, 3 Nov and 24 Nov meetings were each followed by recorded changes; {s["clin_commits"]} commit messages name a doctor's feedback.</li>
        <li><b>AI-assisted build sessions:</b> {s["ai_sessions"]} sessions on {s["ai_days"]} days (main working sessions plus their agent sub-tasks).</li>
      </ul>
      <div class="bars">
        <div class="bh">Commits</div>
        {bar("Shaik Abdul Basith", s["basith"], s["commits"], "sage")}
        {bar("Shaik Awaiz", s["awaiz"], s["commits"], "sage")}
        <div class="bh">Group-chat messages</div>
        {bar("Shaik Abdul Basith", cp.get("basith",0), maxchat, "sage")}
        {bar("Shaik Awaiz", cp.get("awaiz",0), maxchat, "sage")}
      </div>
    </div>
  </div>
  {pulse}
  <div class="together"><b>Together:</b> {s["team_meetings"]} team meetings and {s["mentor"]} mentor and organiser sessions; {fmt(s["chat"])} chat messages ({fmt(s["chat_clin"])} from clinicians, {fmt(s["chat_eng"])} from engineers); activity on {s["active_days"]} of the days from 1 Aug to 27 Nov.</div>
  <div class="foot">Counts from git history (all branches), the team's group-chat export (counts only, no messages shown), saved meeting minutes and review notes, and Play Console release dates. Prepared 27 Nov 2026.</div>
</section>'''


# ---------- impact metric sheets ----------
def tile(num, label, sub, cls=""):
    return f'<div class="itile {cls}"><b>{num}</b><span>{label}</span><i>{sub}</i></div>'

impact1 = f'''
<section class="page impact">
  <header class="mhead"><div><div class="kicker">Impact in numbers · 1 of 2</div><h2>What the collaboration produced</h2></div></header>
  <div class="igrid">
    {tile(fmt(s["commits"]), "code commits", f'{fmt(s["basith"])} Basith · {s["awaiz"]} Awaiz · 7 Aug to 27 Nov', "solid")}
    {tile(s["build_days"], "active build days", f'{fmt(s["ins"])} lines of code and docs added')}
    {tile(s["team_meetings"], "team meetings", "doctors and engineers together")}
    {tile(s["mentor"], "mentor and organiser sessions", "webinar 6 Sep · mentor 20 Oct · briefing 23 Nov")}
    {tile(fmt(s["chat"]), "team conversations", f'group-chat messages, {fmt(s["chat_clin"])} from the doctors')}
    {tile(s["reviews"], "doctor and legal reviews", "concept, specs, survey, legal advice, device tests", "rose")}
  </div>
  <div class="ifoot">Counts from git (all branches), the team group-chat export (counts only), saved minutes and review notes.</div>
</section>'''

impact2 = f'''
<section class="page impact">
  <header class="mhead"><div><div class="kicker">Impact in numbers · 2 of 2</div><h2>What patients, families and doctors get</h2></div></header>
  <div class="igrid">
    {tile("LIVE", "on Google Play in India", "closed test 14 Oct · live from 19 Oct · 0.7.1 on 9 Nov · 0.8.0 in review 23 Nov", "solid")}
    {tile(s["releases"], "Play Store releases", "0.4.1 · 0.5.0 · 0.7.1 · 0.8.0", "gold")}
    {tile("22 + English", "languages for speech", "goals-of-care recordings and note translation (Sarvam); app screens in English and Hindi, with Telugu, Urdu and Kannada", "long")}
    {tile("981", "Android unit tests passing", "plus the website's checks")}
    {tile("3", "tools based on validated scales", "ESAS-style symptom diary · ZBI-4 carer check · NCCN distress thermometer", "rose")}
    {tile("30", "fixes from 2 doctor video reviews", "15 patient-app + 15 doctor-portal points, 22 Nov", "rose")}
  </div>
  <div class="ifoot">"Based on" validated tools: the scales are not yet validated in every Indian language. Language count is for speech transcription and translation, not every screen.</div>
</section>'''

# ---------- what the medical team did ----------
medical = '''
<section class="page medical">
  <header class="mhead"><div><div class="kicker">Clinician-led from day one</div><h2>What the medical team did</h2>
  <div class="mnote">The doctors set the problem, wrote the clinical content and tested every build on their own phones.</div></div></header>
  <div class="mcols">
    <div class="mcard">
      <h3>Dr Sujay H S</h3>
      <ul>
        <li><b>20 Aug</b> One-page goals-of-care concept: the idea Sahara grew from.</li>
        <li><b>12 Sep</b> Distilled clinical form that guided the Round 1 answers.</li>
        <li><b>23–29 Oct</b> Hospital survey: follow-up and sending it to doctors.</li>
        <li><b>3 Nov</b> Walked through the whole app in a team meeting; changes built the next day.</li>
        <li><b>7 Nov</b> His 13 topics and edits shaped the final submission.</li>
        <li><b>21–22 Nov</b> Field review on his phone: 6 photographed screens, 2 videos, 30 issues logged.</li>
      </ul>
    </div>
    <div class="mcard">
      <h3>Dr Sharada Vinod Kutty</h3>
      <ul>
        <li><b>Sep</b> Wrote the full clinical reference: values, home support, named people, readable copies.</li>
        <li><b>27 Sep</b> Reviewed the Round 1 deck.</li>
        <li><b>20 Oct</b> Explained the problem to the mentor.</li>
        <li><b>24 Oct</b> Symptom list, with Dr Shirin.</li>
        <li><b>29 Oct</b> Clinical and technical specifications for the emergency care plan and advance care planning.</li>
        <li><b>22 Nov</b> Feedback on doctor invite links; with Dr Sujay, the finale pitch outline.</li>
      </ul>
    </div>
    <div class="mcard">
      <h3>Dr Shirin Ayub</h3>
      <ul>
        <li><b>20 Sep</b> One calendar for medicines, visits and tests; fewer clicks; past treatments in the history.</li>
        <li><b>20 Sep</b> Keep the doctor's instructions beside each appointment.</li>
        <li><b>24 Oct</b> Symptom list, with Dr Sharada.</li>
        <li><b>Nov</b> Caregiver-burnout evidence for the final submission.</li>
        <li><b>22 Nov</b> Clearer capacity step, medicine and visit alarms, guardian's phone on the SOS QR.</li>
        <li><b>23 Nov</b> First-run care details (illness, current doctor, current treatment), built the same day.</li>
      </ul>
    </div>
  </div>
  <div class="mrow">
    <div class="mbox"><h4>Goals-of-care content and form</h4><p>The clinical form, the clinical reference and the specifications define every section of the doctor's note. The note now prints the lawyer-reviewed form word for word.</p></div>
    <div class="mbox"><h4>Emergency care plan</h4><p>Dr Sharada's specification of the emergency care plan and advance care planning (29 Oct) drives the plan, the pocket card and the offline QR.</p></div>
    <div class="mbox"><h4>Symptom and carer tools</h4><p>Symptom list from Dr Shirin, modelled on ESAS-r and IPOS (agreed with Dr Sharada and Dr Sujay, 24 Oct); ZBI-4 for carers and the NCCN distress thermometer.</p></div>
    <div class="mbox"><h4>Field testing</h4><p>Doctors tested the Play Store build on their own phones (21–22 Nov). Next: the new build tested on real phones with at least 5 goals-of-care conversations (agreed 22 Nov).</p></div>
  </div>
  <div class="foot">Sources: meeting minutes (21 and 23 Sep, 22 Nov), the 3 Nov meeting transcript, team-review notes (21–22 Nov), dated team documents and git history.</div>
</section>'''

# ---------- legal review ----------
legal = '''
<section class="page legal">
  <header class="mhead"><div><div class="kicker">Built inside Indian law</div><h2 class="lh">Legal review</h2>
  <div class="mnote">We sought legal advice on the wording and format of the goals-of-care discussion, and the app follows India's data and end-of-life law.</div></div></header>
  <div class="lgrid">
    <div class="lcard wide">
      <h3><span class="ln">1</span>Legal advice on the goals-of-care discussion <span class="date">31 Oct 2026</span></h3>
      <div class="two">
        <ul>
          <li><b>Ask both sex and gender</b> in the patient details.</li>
          <li><b>Family includes</b> live-in and queer relationships.</li>
          <li><b>Record who was tried</b> but could not be reached.</li>
          <li><b>Primary and secondary</b> decision makers, each reachable 24 hours.</li>
        </ul>
        <ul>
          <li><b>Capacity:</b> add guidance on how, and when, to assess it.</li>
          <li><b>Withholding or withdrawing life support</b> is kept out of the document; in India it follows the <i>Common Cause v Union of India</i> (2018, 2023) board process.</li>
          <li><b>Witnesses</b> for a thumb impression or limited literacy, at home and in hospital.</li>
          <li><b>Result:</b> the app's note prints this form word for word on Android and web, and a test checks every line.</li>
        </ul>
      </div>
    </div>
    <div class="lcard">
      <h3><span class="ln">2</span>Advance directive law</h3>
      <ul>
        <li>The note records a conversation. It is <b>not</b> an advance directive or a DNAR order.</li>
        <li>The advance care planning section helps people prepare an advance directive: under the 2023 simplification it needs two witnesses and a notary or gazetted officer.</li>
        <li>Only adults (18+) can make an advance directive.</li>
      </ul>
    </div>
    <div class="lcard">
      <h3><span class="ln">3</span>DPDP Act 2023: consent and data</h3>
      <ul>
        <li><b>Consent before recording</b> from the patient (or legal representative) or the family in the room; either is enough, and the app records who agreed.</li>
        <li><b>18+</b> to create an account; for a child patient, a parent or lawful guardian consents.</li>
        <li><b>Data minimisation:</b> report files and photos stay on the phone; recordings leave only with consent.</li>
        <li><b>Morphine reports</b> are not linked to anyone's account.</li>
        <li>Withdraw consent, delete a person's records or the account.</li>
      </ul>
    </div>
    <div class="lcard">
      <h3>Next steps</h3>
      <ul>
        <li>Name a nominee under DPDP s.14.</li>
        <li>Publish the data-retention period and a grievance contact in the privacy policy.</li>
        <li>Validate the symptom and carer scales in more Indian languages.</li>
      </ul>
    </div>
  </div>
  <div class="foot">Sources: legal advice on the goals-of-care discussion wording and format (31 Oct), the form coverage note, the privacy-policy draft (DPDP Act 2023), the app's consent screen and the emergency care plan references.</div>
</section>'''

css = open(os.path.join(HERE, "style.css"), encoding="utf-8").read()
page = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Sahara Collaboration Calendar</title>
<style>{css}</style></head><body>
{cover}{impact1}{impact2}{"".join(month_page(m) for m in (8, 9, 10, 11))}{medical}{legal}{last}
</body></html>'''
open(os.path.join(HERE, "calendar.html"), "w", encoding="utf-8").write(page)
print(json.dumps(stats, indent=1))
