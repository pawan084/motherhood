"""Seed content for the Journey tab: week-banded editorial content per stage.

⚠ HUMAN-GATED (TODO.md): this copy is general-knowledge maternal-health
information written for product development. It has NOT been reviewed by a
clinician and MUST be before any real user sees it. It deliberately avoids
diagnosis, dosing, and prescriptive medical claims; every band points worry
back to the care team.

Why BANDS, not 50 per-week rows: each band is a substantial, reviewable unit
of writing. Serving "week 24" resolves to its band server-side, so the product
still feels per-week while the content stays maintainable and a clinician can
review 16 units instead of 54. Admin-authored per-week overrides can land
later without changing the API shape.

Seed-once semantics (sayli's lesson): rows are inserted only when the band id
is absent, so admin edits and deletions stick across restarts.

Fields per band:
    id          stable identifier ("preg-21-24") — admin edits key on it
    stage       journey stage
    week_start/week_end   inclusive band, in that stage's week numbering
    title       the band's headline
    yourself    "Your body this week" (mockup) — what many people notice
    baby        "Your baby this week" — growth, plainly told
    prepare     "Prepare for your visit" — questions worth asking
"""

BANDS = [
    # ── Pregnant (gestational weeks, week 40 = due date) ──────────────────
    dict(id="preg-1-3", stage="pregnant", week_start=1, week_end=3,
         title="The very beginning",
         yourself="These weeks are counted before most people even know. You may "
                  "feel nothing at all yet — that's normal.",
         baby="Fertilisation and implantation happen in this window. It's earlier "
              "than most tests can show.",
         prepare="If you haven't yet, ask about starting a prenatal vitamin with "
                 "folic acid, and which of your current medicines are fine to continue."),
    dict(id="preg-4-7", stage="pregnant", week_start=4, week_end=7,
         title="Early days",
         yourself="Tiredness, tender breasts, and nausea are common — and their "
                  "absence is normal too. Feelings can swing; that's the hormones, "
                  "not a verdict on you.",
         baby="The embryo is tiny — poppy-seed to blueberry scale — and the "
              "neural tube, heart, and first structures are forming.",
         prepare="Ask when your first scan will be, what folic acid dose is right "
                 "for you, and what symptoms your care team wants to hear about early."),
    dict(id="preg-8-12", stage="pregnant", week_start=8, week_end=12,
         title="The first trimester, in full",
         yourself="Nausea and exhaustion often peak in these weeks. Small frequent "
                  "meals help many people. If you can't keep fluids down, tell your "
                  "care team — that one is worth a call.",
         baby="All the major organs are laying down their foundations. By week 12 "
              "the baby is about the size of a lime.",
         prepare="The first-trimester scan and blood tests usually happen here. Ask "
                 "what each test checks and when you'll get results."),
    dict(id="preg-13-16", stage="pregnant", week_start=13, week_end=16,
         title="Turning a corner",
         yourself="Energy often returns and nausea eases for many people. You might "
                  "start showing. If low mood hasn't lifted, say so at your next "
                  "visit — it matters as much as anything physical.",
         baby="Bones are hardening and the baby can make small movements, though "
              "you probably can't feel them yet.",
         prepare="Ask about the anomaly scan booking, and whether your iron and "
                 "vitamin D levels are worth checking."),
    dict(id="preg-17-20", stage="pregnant", week_start=17, week_end=20,
         title="Halfway",
         yourself="You may feel the first movements — flutters more than kicks, "
                  "and earlier for some than others. Comparing with anyone else's "
                  "timeline is a trap; your pattern is the one that matters.",
         baby="The detailed anomaly scan usually happens around week 20 — the baby "
              "is about banana-sized and busy.",
         prepare="Bring your questions to the anomaly scan: what's being checked, "
                 "and what happens if anything needs a second look."),
    dict(id="preg-21-24", stage="pregnant", week_start=21, week_end=24,
         title="Movement becomes a rhythm",
         yourself="Movements firm up into a pattern you'll come to know. Backache "
                  "and disturbed sleep are common companions from here.",
         baby="Hearing is developing — your voice carries through. By week 24 the "
              "baby is around corn-cob length.",
         prepare="Ask about the glucose screening test, and what movement pattern "
                 "changes should prompt a call — get the threshold in their words."),
    dict(id="preg-25-28", stage="pregnant", week_start=25, week_end=28,
         title="The third trimester approaches",
         yourself="Heartburn, swelling ankles, and vivid dreams show up for many. "
                  "Sudden severe swelling or headaches are care-team-now symptoms, "
                  "not wait-and-see ones.",
         baby="Eyes open, lungs practise, and weight gain begins in earnest.",
         prepare="Ask about whooping-cough vaccination timing, anaemia checks, and "
                 "when to start counting kicks."),
    dict(id="preg-29-32", stage="pregnant", week_start=29, week_end=32,
         title="Growing, fast",
         yourself="Breathlessness and pelvic pressure are common as space gets "
                  "tight. Rest is productive now, not indulgent.",
         baby="The baby is putting on weight quickly and usually settles head-down "
              "somewhere in this stretch.",
         prepare="Ask how the baby is lying, what your birth-preferences "
                 "conversation should cover, and when to worry about reduced movement."),
    dict(id="preg-33-36", stage="pregnant", week_start=33, week_end=36,
         title="Nearly there",
         yourself="Sleep gets awkward and trips to the bathroom multiply. Practice "
                  "contractions (irregular, easing with rest) are common — regular "
                  "ones that strengthen are the real thing.",
         baby="Lungs are close to ready. The baby's movements feel different — "
              "less acrobatics, same frequency. Less frequent is a call, not a note.",
         prepare="Ask what early labour actually looks like, when to come in, and "
                 "what to pack. Group B strep testing may happen around now."),
    dict(id="preg-37-40", stage="pregnant", week_start=37, week_end=40,
         title="Full term",
         yourself="You're carrying a lot — physically and mentally. Any bleeding, "
                  "fluid leak, severe headache, or drop in movement is a call right "
                  "now, at any hour.",
         baby="From 37 weeks the baby is considered full term and gaining the "
              "final layers before birth.",
         prepare="Confirm the plan for going past your due date, who to call when "
                 "labour starts, and how monitoring works from here."),
    dict(id="preg-41-42", stage="pregnant", week_start=41, week_end=42,
         title="Past the date",
         yourself="Going past the due date is common and hard on patience. Your "
                  "care team will be watching more closely now — lean on them.",
         baby="The baby keeps growing; monitoring makes sure everything stays well.",
         prepare="Ask about membrane sweeps, induction options and timing, and how "
                 "the baby's wellbeing is checked while you wait."),

    # ── Postpartum (weeks since birth) ─────────────────────────────────────
    dict(id="pp-1-2", stage="postpartum", week_start=1, week_end=2,
         title="The first days",
         yourself="Bleeding (lochia), afterpains, and exhaustion are expected. "
                  "Soaking a pad in an hour, fever, or a headache with vision "
                  "changes are care-team-now symptoms. So is feeling unable to "
                  "cope — that call is as legitimate as any physical one.",
         baby="Feeding, sleeping, and being held are the whole job. Weight dips "
              "then recovers in the first two weeks.",
         prepare="Ask who to call day or night, what's normal for bleeding, and "
                 "how feeding is going — get help early, not heroically late."),
    dict(id="pp-3-6", stage="postpartum", week_start=3, week_end=6,
         title="Finding a rhythm",
         yourself="Bleeding tapers; energy is still scarce. Baby blues should have "
                  "lifted — if low mood, anxiety, or intrusive thoughts persist, "
                  "tell your care team. Postpartum depression is common and treatable.",
         baby="More alert time, first social smiles toward six weeks, and feeding "
              "patterns that slowly become predictable.",
         prepare="The six-week check is for YOU: healing, mood, contraception, and "
                 "any pain that hasn't settled. Write your questions down before."),
    dict(id="pp-7-12", stage="postpartum", week_start=7, week_end=12,
         title="A new normal",
         yourself="Your body is still recovering even when the world assumes "
                  "you're 'back'. Pelvic floor work matters; pain or leaking "
                  "deserves a professional look, not endurance.",
         baby="Vaccination schedules begin, and the baby's own personality starts "
              "showing through.",
         prepare="Ask about the vaccination timetable, safe return to exercise, "
                 "and anything about your delivery you still want explained."),
    dict(id="pp-13-52", stage="postpartum", week_start=13, week_end=52,
         title="The long arc",
         yourself="Recovery isn't linear and doesn't run on anyone else's clock. "
                  "Mood, sleep, and your sense of self all count as health — keep "
                  "saying the true thing at appointments.",
         baby="Solids, sitting, and eventually first steps — the first year is a "
              "parade of firsts, each on its own schedule.",
         prepare="Keep a running list between visits. No question about your body "
                 "or your baby is too small to ask."),

    # ── Trying to conceive (no week index) ─────────────────────────────────
    dict(id="ttc", stage="trying_to_conceive", week_start=0, week_end=0,
         title="While you're trying",
         yourself="Cycles, timing, and waiting can take over a lot of headspace. "
                  "Folic acid before conception is one of the few universally "
                  "recommended steps — and looking after your own health counts.",
         baby="",
         prepare="If you've been trying for a while — a year, or six months over "
                 "35 — a conversation with your care team is reasonable, not "
                 "premature. Ask what basic checks make sense for both partners."),
]


# ── Per-week baby size + milestones (the "journey" feel) ─────────────────────
# Coarse, widely-used size comparisons — the emotional hook every week needs
# even inside a content band. Same HUMAN-GATED posture as the copy above.
# Weeks 1-3 are pre-detection; the app shows no size for them.

WEEK_SIZES = {
    4: ("🌱", "a poppy seed"), 5: ("🌱", "a sesame seed"),
    6: ("🫛", "a lentil"), 7: ("🫐", "a blueberry"),
    8: ("🫐", "a raspberry"), 9: ("🍒", "a cherry"),
    10: ("🍓", "a strawberry"), 11: ("🌰", "a fig"),
    12: ("🍋", "a lime"), 13: ("🫛", "a pea pod"),
    14: ("🍋", "a lemon"), 15: ("🍎", "an apple"),
    16: ("🥑", "an avocado"), 17: ("🥔", "a turnip"),
    18: ("🫑", "a bell pepper"), 19: ("🍅", "an heirloom tomato"),
    20: ("🍌", "a banana"), 21: ("🥕", "a carrot"),
    22: ("🥥", "a papaya"), 23: ("🥭", "a large mango"),
    24: ("🌽", "an ear of corn"), 25: ("🥬", "a swede"),
    26: ("🥒", "a courgette"), 27: ("🥦", "a cauliflower"),
    28: ("🍆", "a large aubergine"), 29: ("🎃", "a butternut squash"),
    30: ("🥬", "a large cabbage"), 31: ("🥥", "a coconut"),
    32: ("🥬", "a napa cabbage"), 33: ("🍍", "a pineapple"),
    34: ("🍈", "a cantaloupe"), 35: ("🍈", "a honeydew melon"),
    36: ("🥬", "a romaine lettuce"), 37: ("🥬", "a bunch of chard"),
    38: ("🎃", "a small pumpkin"), 39: ("🍉", "a mini watermelon"),
    40: ("🎃", "a small pumpkin"), 41: ("🍉", "a watermelon"),
    42: ("🍉", "a watermelon"),
}

# The landmarks that make a timeline a timeline. Weeks are typical, not
# prescriptive — the copy in each band still says "ask your care team".
MILESTONES = {
    "pregnant": [
        {"week": 12, "label": "First scan", "emoji": "🩺"},
        {"week": 13, "label": "Second trimester", "emoji": "🌿"},
        {"week": 20, "label": "Anomaly scan", "emoji": "🩺"},
        {"week": 26, "label": "Glucose test", "emoji": "🩸"},
        {"week": 28, "label": "Third trimester", "emoji": "🌿"},
        {"week": 37, "label": "Full term", "emoji": "🌟"},
        {"week": 40, "label": "Due date", "emoji": "🎉"},
    ],
    "postpartum": [
        {"week": 1, "label": "First days", "emoji": "🌱"},
        {"week": 6, "label": "Six-week check", "emoji": "🩺"},
        {"week": 8, "label": "First vaccinations", "emoji": "💉"},
        {"week": 12, "label": "Three months", "emoji": "🌟"},
    ],
}


# Approximate crown-rump (to wk 20) then crown-heel length in cm — the
# standard published approximations every pregnancy app uses. HUMAN-GATED
# like all seeded content.
WEEK_LENGTHS_CM = {
    4: 0.4, 5: 0.9, 6: 1.2, 7: 1.5, 8: 1.6, 9: 2.3, 10: 3.1, 11: 4.1,
    12: 5.4, 13: 7.4, 14: 8.7, 15: 10.1, 16: 11.6, 17: 13.0, 18: 14.2,
    19: 15.3, 20: 25.6, 21: 26.7, 22: 27.8, 23: 28.9, 24: 30.0, 25: 34.6,
    26: 35.6, 27: 36.6, 28: 37.6, 29: 38.6, 30: 39.9, 31: 41.1, 32: 42.4,
    33: 43.7, 34: 45.0, 35: 46.2, 36: 47.4, 37: 48.6, 38: 49.8, 39: 50.7,
    40: 51.2, 41: 51.5, 42: 51.7,
}
