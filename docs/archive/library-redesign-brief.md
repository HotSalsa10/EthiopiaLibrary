You're helping me improve an app for a small charity library run by my father.

How it's actually used and built:
- Runs on a SINGLE tablet at the front desk, operated by one person — the library's
  bookkeeper. It's a staff tool for one trusted operator, not a public self-service app.
- Its main job is managing book loans: lending books out, taking them back, and
  tracking what's currently out.
- Data lives locally on the tablet. It's connected to a FREE-TIER Firebase, with a
  button in Settings so that whenever internet is available, the bookkeeper can
  manually upload/back up the data. Backup is manual and on-demand, not continuous sync.
- The tablet has a working camera, and the app ALREADY uses it for scanning.

I want you to reimagine this app holistically — not just patch it — while preserving
everything that already works and respecting the architecture above (local-first +
manual Firebase backup, existing camera scanning).

Before diving in: list the Claude Code skills currently installed and tell me which
are relevant here. In particular, use the frontend-design skill for any UI/visual
work. If a skill would clearly help but isn't installed, tell me and I'll decide —
don't install anything without asking.

Work in phases and check in with me between each:

PHASE 1 — Understand. Explore the codebase and report back in plain language: what
the app does today, the tech stack, the architecture, the main screens and flows
(especially the loan flow and the camera/scanning flow), exactly how local data is
stored, and how the manual Firebase backup works. Note anything broken, fragile,
half-finished, or confusing. Also list the installed skills relevant to this work.

PHASE 2 — Critique & vision. Assess it honestly with these priorities:
- Tablet-first and touch-operated: big tap targets, readable at arm's length, fast
  for repetitive loan transactions, forgiving (clear confirmations, easy undo).
- One non-technical operator: the flow should be obvious with almost no training.
- The core is the loan lifecycle — make check-out and return as fast and foolproof
  as possible, and lean on the EXISTING camera scanning to speed it up (improve it,
  don't reinvent it).
- Backup reliability is the biggest data risk. Because backup is manual, it's easy
  to forget: consider showing "last backed up X ago," warning when there are unsaved
  changes since the last backup, and gently prompting to back up when internet is
  available — without nagging.
- Restore path: if the tablet breaks or is replaced, there must be a clear, tested
  way to pull the data back down from Firebase onto a fresh tablet. Verify it works.
- Firebase free tier: keep uploads efficient (batch, send only what changed) to stay
  within free quotas. Check current free-tier limits before designing this.
- Security: this holds borrower names and loan records. Make sure the Firebase
  security rules are locked down so the backup isn't publicly readable or writable.

Before proposing the vision, research how other library / book-loan apps handle
things — both FOSS (e.g. Koha, Evergreen, smaller personal/home-library apps) and
commercial ones — to borrow good ideas and avoid known mistakes, especially around
the loan flow, overdue handling, and borrower management. Filter everything hard
against our reality: one non-technical bookkeeper, one tablet, local-first, manual
backup, small charity. Most library software targets multi-staff, multi-branch setups
with servers and catalogs — do NOT pull in that complexity. For each idea worth
adopting, tell me why it fits our constraints; if it doesn't fit, leave it out.
Licensing: you may study open-source apps for ideas and UX patterns, but do not copy
their code — Koha and Evergreen are GPL-licensed and copying carries obligations we
don't want. Learn from them; don't lift from them.

Then describe what this app could be at its best, and propose improvements we may not
have considered — e.g. quick borrower lookup with one-tap new-borrower creation, clear
"currently out" / "due soon" / "overdue" views, renewals and holds, simple activity
reports for whoever oversees the library, and smooth fully-offline operation with
backup only when the operator chooses. Prioritize ruthlessly: essential for a
one-person loan desk vs. nice-to-have vs. skip.

PHASE 3 — Plan. Give me a concrete, ordered plan. Flag anything risky or destructive
first, and never delete working functionality, change the local data format, or alter
the Firebase backup/restore behavior without telling me and explaining the migration.

PHASE 4 — Build. Once I approve, implement incrementally. Use relevant skills
(frontend-design for UI). Keep the app working at every step, protect existing local
data and backups, commit in logical chunks with clear messages, and explain what
changed and why.

Constraints: it's a charity, so favor free/low-cost, low-maintenance solutions and
avoid paid services or heavy dependencies without asking. Everything must keep working
on one tablet, offline, run by one non-technical person day after day.

Start with Phase 1 and wait for me before moving on.