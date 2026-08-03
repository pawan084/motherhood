"""Seed catalog for the Videos tab: short, stage-aware guide videos.

⚠ HUMAN-GATED (TODO.md): this catalog was curated for product development.
The selection (and each video's continued availability and accuracy) MUST be
reviewed before real users see it — same posture as `seed_journey.py` and the
safety taxonomy. Entries reference well-known public educational channels;
verify ids, rights, and content before launch.

Seed-once semantics: rows are inserted only when the id is absent, so admin
edits and deletions stick across restarts.

Fields:
    id            stable identifier ("vid-preg-mid-nutrition")
    title/topic   display strings
    stage         journey stage the video targets
    week_start/week_end   inclusive band in that stage's week numbering
                  (0-0 = stage-wide, not week-specific)
    youtube_id    the YouTube video id (watch?v=<id>)
    duration_min  approximate length, for the row subtitle
"""

VIDEOS = [
    # ── Pregnant ───────────────────────────────────────────────────────────
    dict(id="vid-preg-early-what", stage="pregnant", week_start=1, week_end=13,
         title="First trimester: what's actually happening",
         topic="Early pregnancy", youtube_id="Xath6kOf0NE", duration_min=6),
    dict(id="vid-preg-early-nausea", stage="pregnant", week_start=4, week_end=16,
         title="Coping with nausea and food aversions",
         topic="Everyday comfort", youtube_id="0Xp0PLLypVQ", duration_min=5),
    dict(id="vid-preg-mid-movement", stage="pregnant", week_start=16, week_end=28,
         title="Feeling your baby move: what to notice",
         topic="Your baby", youtube_id="illT2A0hCUQ", duration_min=4),
    dict(id="vid-preg-mid-nutrition", stage="pregnant", week_start=13, week_end=28,
         title="Eating well in the second trimester",
         topic="Nutrition", youtube_id="Y5B1HGtEHIY", duration_min=7),
    dict(id="vid-preg-mid-exercise", stage="pregnant", week_start=13, week_end=32,
         title="Safe, gentle exercise while pregnant",
         topic="Movement", youtube_id="opt51Un9Y1s", duration_min=10),
    dict(id="vid-preg-late-labour", stage="pregnant", week_start=28, week_end=42,
         title="Signs of labour, plainly explained",
         topic="Preparing for birth", youtube_id="d0aYhBAkfGA", duration_min=8),
    dict(id="vid-preg-late-hospital", stage="pregnant", week_start=32, week_end=42,
         title="Packing your hospital bag",
         topic="Preparing for birth", youtube_id="8QuBQTP0dGQ", duration_min=6),
    dict(id="vid-preg-breathing", stage="pregnant", week_start=0, week_end=0,
         title="Two-minute calming breath practice",
         topic="Wellbeing", youtube_id="tybOi4hjZFQ", duration_min=3),

    # ── Postpartum ─────────────────────────────────────────────────────────
    dict(id="vid-pp-early-recovery", stage="postpartum", week_start=1, week_end=6,
         title="The first weeks: recovery basics",
         topic="Your recovery", youtube_id="8N6pRDsQ6zE", duration_min=7),
    dict(id="vid-pp-feeding", stage="postpartum", week_start=1, week_end=12,
         title="Feeding: getting comfortable, getting help",
         topic="Feeding", youtube_id="TLHzTHkE5vE", duration_min=9),
    dict(id="vid-pp-mood", stage="postpartum", week_start=1, week_end=52,
         title="Baby blues vs postpartum depression",
         topic="Your mind", youtube_id="Xk2jDp7l1Qs", duration_min=6),
    dict(id="vid-pp-floor", stage="postpartum", week_start=6, week_end=52,
         title="Gentle pelvic floor recovery",
         topic="Movement", youtube_id="q0X6uK8jK1w", duration_min=8),

    # ── Trying to conceive ─────────────────────────────────────────────────
    dict(id="vid-ttc-cycle", stage="trying_to_conceive", week_start=0, week_end=0,
         title="Understanding your cycle and fertile window",
         topic="Conceiving", youtube_id="ITTAdXd_zfU", duration_min=6),
    dict(id="vid-ttc-prep", stage="trying_to_conceive", week_start=0, week_end=0,
         title="Preparing your body before pregnancy",
         topic="Preconception", youtube_id="6y1Y3JzXhKo", duration_min=5),
]
