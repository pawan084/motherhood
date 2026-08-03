"""Seed catalog for the Videos tab: short, stage-aware guide videos.

Every youtube_id below was VERIFIED against YouTube's oEmbed endpoint on
2026-08-03 (HTTP 200 + matching title = real, playable video). Titles are the
videos' own. Durations are deliberately omitted (oEmbed doesn't provide them
— null beats a made-up number).

⚠ HUMAN-GATED (TODO.md): verification above covers EXISTENCE, not clinical
quality. The selection must be reviewed for content accuracy before real
users see it — same posture as `seed_journey.py` and the safety taxonomy.

Seed-once semantics: rows are inserted only when the id is absent, so admin
edits and deletions stick across restarts. Changing this file therefore only
affects FRESH databases — restart with a clean DB (or update rows via the
admin) to pick up changes.

Fields:
    id            stable identifier — admin edits key on it
    title/topic   display strings (title = the video's real title)
    stage         journey stage the video targets
    week_start/week_end   inclusive band in that stage's week numbering
                  (0-0 = stage-wide, not week-specific)
    youtube_id    the YouTube video id (watch?v=<id>)
    duration_min  approximate length; None when unknown
"""

VIDEOS = [
    # ── Pregnant ───────────────────────────────────────────────────────────
    dict(id="vid-preg-tri1-expect", stage="pregnant", week_start=1, week_end=13,
         title="Pregnancy - What to Expect in the First Trimester",
         topic="Early pregnancy", youtube_id="ML-H2ayCiQ0", duration_min=None),
    dict(id="vid-preg-tri1-weekly", stage="pregnant", week_start=1, week_end=13,
         title="What to expect in your First Trimester | Week-by-Week",
         topic="Early pregnancy", youtube_id="cfn04QUO4B8", duration_min=None),
    dict(id="vid-preg-tri1-3d", stage="pregnant", week_start=1, week_end=13,
         title="First Trimester | 3D Animated Pregnancy Guide",
         topic="Your baby", youtube_id="E0i7NQsJdWY", duration_min=None),
    dict(id="vid-preg-nutrition", stage="pregnant", week_start=13, week_end=28,
         title="Nutrition During Pregnancy",
         topic="Nutrition", youtube_id="0BrxCY89_uQ", duration_min=None),
    dict(id="vid-preg-eating-well", stage="pregnant", week_start=13, week_end=28,
         title="Eating Well for Pregnancy",
         topic="Nutrition", youtube_id="OzHjA5u7_Vs", duration_min=None),
    dict(id="vid-preg-yoga-10", stage="pregnant", week_start=0, week_end=0,
         title="10 minute Prenatal Yoga for Beginners (all trimesters)",
         topic="Movement", youtube_id="4NwQKXpWN_A", duration_min=10),
    dict(id="vid-preg-yoga-15", stage="pregnant", week_start=13, week_end=32,
         title="15-Minute Pregnancy Yoga | All Trimesters",
         topic="Movement", youtube_id="zmUJWKM98hM", duration_min=15),
    dict(id="vid-preg-labour", stage="pregnant", week_start=28, week_end=42,
         title="Childbirth: signs of labor, delivering baby, and postpartum care",
         topic="Preparing for birth", youtube_id="lux8wHzOTFY", duration_min=None),

    # ── Postpartum ─────────────────────────────────────────────────────────
    dict(id="vid-pp-recovery-expect", stage="postpartum", week_start=1, week_end=6,
         title="Postpartum: What to Expect During Your Recovery",
         topic="Your recovery", youtube_id="Rn40vrKUxso", duration_min=None),
    dict(id="vid-pp-care-basics", stage="postpartum", week_start=1, week_end=12,
         title="What to Expect After Birth: Postpartum Care Basics",
         topic="Your recovery", youtube_id="zsXRdgM6D04", duration_min=None),
    dict(id="vid-pp-warning-signs", stage="postpartum", week_start=1, week_end=6,
         title="6 signs to watch for after giving birth",
         topic="Your health", youtube_id="4-9Uj5K_zRU", duration_min=None),
    dict(id="vid-pp-breastfeeding", stage="postpartum", week_start=1, week_end=12,
         title="Nine Instinctive Steps to Breastfeeding",
         topic="Feeding", youtube_id="ATvelAK6hIU", duration_min=None),
    dict(id="vid-pp-healing-tips", stage="postpartum", week_start=2, week_end=26,
         title="Postpartum Recovery: Symptoms and Healing Tips",
         topic="Your recovery", youtube_id="pTBJaM6l_30", duration_min=None),

    # ── Trying to conceive ─────────────────────────────────────────────────
    dict(id="vid-ttc-cycle-timing", stage="trying_to_conceive", week_start=0, week_end=0,
         title="Best Time in Your Cycle to Get Pregnant | Proven by Science",
         topic="Conceiving", youtube_id="ADoXOMxCzt8", duration_min=None),
    dict(id="vid-ttc-mistakes", stage="trying_to_conceive", week_start=0, week_end=0,
         title="Top 5 Mistakes to Avoid When Trying to Get Pregnant",
         topic="Conceiving", youtube_id="5nNqLEhrOx4", duration_min=None),
]
