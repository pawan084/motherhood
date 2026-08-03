"""Daily-insight pool: one small, useful, stage-aware tip per day.

Research-backed retention pattern (Pregnancy+/BabyCenter): a piece of content
that CHANGES EVERY DAY is the single strongest reason to open the app daily.
Rotation is deterministic (day-of-year + week), so it needs no LLM call, no
storage, and the same learner sees the same tip all day across devices.

⚠ HUMAN-GATED (TODO.md): general-knowledge wellness copy — clinician review
before real users, like every other seeded content. Nothing here diagnoses,
doses, or contradicts a care team.

Buckets: pregnant tips are per-trimester so they stay relevant; postpartum
and trying_to_conceive have their own pools.
"""

TIPS = {
    "pregnant_t1": [
        "Small, frequent meals often settle first-trimester nausea better than three big ones.",
        "Cold or room-temperature foods can smell less — worth trying on queasy days.",
        "Tiredness now is real biology: your body is building the placenta. Rest counts as care.",
        "Ginger — tea, chews, or biscuits — has decent evidence for easing mild nausea.",
        "A prenatal vitamin with folic acid is the one habit almost every guideline agrees on.",
        "Tender breasts, food aversions, mood swings: common, hormonal, and usually temporary.",
        "If a smell suddenly bothers you, that's a known first-trimester effect — not a quirk.",
        "Hydration helps with headaches and fatigue — little and often beats big and rare.",
        "It's okay that you don't 'feel pregnant' some days. Symptoms wax and wane.",
        "Line up your first scan questions early — appointments feel fast in the room.",
    ],
    "pregnant_t2": [
        "Energy often returns in the second trimester — a good window for gentle exercise habits.",
        "Sleeping on your side with a pillow between your knees eases back strain.",
        "First movements can feel like flutters or bubbles — many people miss them at first.",
        "Iron needs rise now; pairing iron-rich foods with vitamin C helps absorption.",
        "Leg cramps at night are common — gentle calf stretches before bed can help.",
        "Your anomaly scan is a good moment to write down questions beforehand.",
        "Backache creeping in? Watch posture when sitting — a small cushion helps a lot.",
        "Many people find this the best-feeling trimester — bank some rest anyway.",
        "Talk, hum, or read aloud — hearing develops in this stretch and your voice carries.",
        "Comfortable shoes are not a luxury from here on. Your ligaments are loosening.",
    ],
    "pregnant_t3": [
        "Movements should stay regular to the end — less frequent is a call, not a note.",
        "Practice contractions ease with rest; real ones strengthen and get regular.",
        "Pack the hospital bag early — it turns 3am labour into logistics, not panic.",
        "Heartburn worse lying down? Smaller evening meals and an extra pillow help.",
        "Swollen ankles by evening are common; sudden or severe swelling is a call now.",
        "Pelvic pressure and waddling are the baby settling — your body is doing it right.",
        "Sleep in whatever position works. From here, sleep quantity beats sleep style.",
        "Write your birth preferences, then hold them lightly — flexibility is a plan too.",
        "Know your route and who to call when labour starts — decided beats deciding.",
        "Rest is productive. The last weeks are training for the first weeks.",
    ],
    "postpartum": [
        "Sleep when the baby sleeps is cliché because nothing else replaces it.",
        "Bleeding tapers over weeks — soaking a pad in an hour is a call-now sign.",
        "Feeding is a learned skill for both of you. Asking for help early is strength.",
        "The baby blues should lift within two weeks — if low mood stays, say it out loud.",
        "Accept every offer of food. Your job is recovery and the baby; that's two jobs.",
        "A five-minute shower alone is legitimate self-care. Take it without guilt.",
        "Your six-week check is for YOU — write down every question before it.",
        "Peeing, coughing, laughing shouldn't leak long-term — pelvic floor help works.",
        "Comparison is the thief of the fourth trimester. Your recovery has its own clock.",
        "Skin-to-skin time counts as doing something. It's biology, not idleness.",
    ],
    "trying_to_conceive": [
        "The fertile window is roughly the five days before ovulation plus the day itself.",
        "Folic acid before conception is one of the few universally recommended steps.",
        "Cycle tracking beats guessing — even a simple calendar sharpens the picture.",
        "Both partners' habits count: sleep, alcohol, and smoking all move the odds.",
        "A year of trying (six months over 35) is the usual point to involve your doctor.",
        "Stress doesn't cause infertility, but kindness to yourself makes the wait livable.",
        "Ovulation kits measure a hormone surge — a day or two of warning, used well.",
        "Regular, unprotected sex every 2-3 days across the cycle beats perfect timing math.",
    ],
}


# Language-keyed pools (review: Hindi tips). "en" is the reviewed source of
# truth; "hi" ships EMPTY on purpose — clinical copy is HUMAN-GATED and
# machine translation of health guidance is not acceptable. The lookup
# falls back to English until reviewed pools land here.
TIPS_BY_LANG: dict[str, dict[str, list[str]]] = {
    "en": TIPS,
    "hi": {},
    "hi-Latn": {},
}
