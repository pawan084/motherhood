"""Seed catalog for the Videos tab: the product's OWN hosted videos.

The client produced a set of short-form videos (6 formats × portrait +
landscape) now served by this backend at `/media/videos/...` — replacing the
earlier YouTube catalog so demos stream from our own server. `stream_path`
and `thumb_path` are relative; clients prefix their API base URL. The
landscape variants live beside the portrait ones for web/tablet use
(`/media/videos/landscape/<name>.mp4`).

⚠ HUMAN-GATED (TODO.md): content review before real users, same as all
seeded content. Files themselves are NOT in git (backend/media/README.md
documents how to populate the directory).

Seed-once semantics: rows insert only when the id is absent, so admin edits
and deletions stick. Changing this file affects fresh databases only.
"""

VIDEOS = [
    # ── Pregnant ───────────────────────────────────────────────────────────
    dict(id="own-benefit", stage="pregnant", week_start=1, week_end=13,
         title="One habit with outsized benefits",
         topic="Early pregnancy",
         stream_path="/media/videos/portrait/benefit.mp4",
         thumb_path="/media/videos/thumbs/benefit.png", duration_min=1),
    dict(id="own-dodont", stage="pregnant", week_start=13, week_end=28,
         title="Do's and don'ts, without the fear",
         topic="Everyday care",
         stream_path="/media/videos/portrait/dodont.mp4",
         thumb_path="/media/videos/thumbs/dodont.png", duration_min=1),
    dict(id="own-listicle", stage="pregnant", week_start=28, week_end=42,
         title="Five things to have ready",
         topic="Preparing for birth",
         stream_path="/media/videos/portrait/listicle.mp4",
         thumb_path="/media/videos/thumbs/listicle.png", duration_min=1),

    # ── Postpartum ─────────────────────────────────────────────────────────
    dict(id="own-question", stage="postpartum", week_start=1, week_end=12,
         title="The questions every new parent asks",
         topic="Your recovery",
         stream_path="/media/videos/portrait/question.mp4",
         thumb_path="/media/videos/thumbs/question.png", duration_min=1),
    dict(id="own-myths", stage="postpartum", week_start=1, week_end=52,
         title="Postpartum myths, busted",
         topic="Your recovery",
         stream_path="/media/videos/portrait/myths.mp4",
         thumb_path="/media/videos/thumbs/myths.png", duration_min=1),

    # ── Trying to conceive ─────────────────────────────────────────────────
    dict(id="own-stat", stage="trying_to_conceive", week_start=0, week_end=0,
         title="The one number worth knowing",
         topic="Conceiving",
         stream_path="/media/videos/portrait/stat.mp4",
         thumb_path="/media/videos/thumbs/stat.png", duration_min=1),
]
