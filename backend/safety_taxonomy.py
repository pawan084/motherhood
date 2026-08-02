"""The clinical red-flag taxonomy the deterministic safety layer matches against.

This file is DATA, kept apart from the engine on purpose: a clinician must be
able to review what escalates without reading the matching code. Every entry
cites the symptom class it encodes; the wording is drawn from published
obstetric and postpartum danger-sign guidance (WHO danger signs, ACOG urgent
maternal warning signs).

⚠ HUMAN-GATED (TODO.md): this taxonomy has NOT yet been reviewed by a
qualified clinician. It must be before any real user sees the product.

Structure per rule:
    id       stable identifier (audit rows reference it)
    stages   journey stages it applies to; matching is skipped otherwise —
             EXCEPT that an unknown/missing stage applies every rule (missing
             context must widen the net, not narrow it)
    terms    lowercase substrings/phrases in English, Hindi (Devanagari), and
             romanized Hindi/Hinglish. Word-boundary matched by the engine.
             Negation words INSIDE a term are part of the danger sign itself
             ("no kicks", "can't feel the baby" — absence is the symptom).
    denial_terms  (optional) phrasings that DENY the symptom and carry their
             own negation ("khoon nahi"). Matched hits go straight to
             UNCERTAIN — the LLM adjudicates; the rules never clear them.
    label    short human-readable name shown in audit/review UIs

Matching philosophy: this layer exists to make the OBVIOUS cases impossible to
miss, deterministically, offline, in microseconds. Nuance (negation, reported
speech, past events, typos) belongs to the LLM layer — when these rules match
inside a negated or quoted context, the engine downgrades to UNCERTAIN and
defers, it never silently clears. False negatives are the dangerous direction.
"""

URGENT_RULES = [
    {
        "id": "vaginal_bleeding",
        "label": "Vaginal bleeding in pregnancy",
        "stages": ["pregnant", "trying_to_conceive"],
        "terms": [
            # Hindi stems are deliberately short ("khoon aa", not "khoon aa
            # raha") so surrounding words don't break the match.
            "bleeding", "blood from my vagina", "spotting heavily", "passing clots",
            "khoon aa", "khoon beh", "khoon nikal", "bleeding ho rahi",
            "खून आ", "खून बह", "खून निकल", "रक्तस्राव",
        ],
        # Phrasings that DENY the symptom while containing their own negation
        # word ("khoon NAHI aa raha" never contains a positive stem
        # contiguously). These route straight to UNCERTAIN — the LLM
        # adjudicates, the rules never clear them. Contrast with terms where
        # negation IS the danger sign ("no kicks"), which stay in `terms`.
        "denial_terms": [
            "khoon nahi", "खून नहीं",
        ],
    },
    {
        "id": "postpartum_hemorrhage",
        "label": "Heavy postpartum bleeding",
        "stages": ["postpartum"],
        "terms": [
            "bleeding", "soaking a pad", "soaked a pad", "passing clots", "heavy flow",
            "khoon aa", "khoon beh", "khoon nikal", "bleeding ho rahi",
            "खून आ", "खून बह", "खून निकल",
        ],
        "denial_terms": [
            "khoon nahi", "खून नहीं",
        ],
    },
    {
        "id": "severe_headache_vision",
        "label": "Severe headache / vision changes (preeclampsia sign)",
        "stages": ["pregnant", "postpartum"],
        "terms": [
            "severe headache", "worst headache", "blurry vision", "blurred vision",
            "seeing spots", "seeing stars", "vision changes", "flashing lights",
            "tez sar dard", "sar phat", "dhundhla", "aankhon ke saamne andhera",
            "तेज़ सिर दर्द", "सिर फट", "धुंधला", "आंखों के सामने अंधेरा",
        ],
    },
    {
        "id": "reduced_fetal_movement",
        "label": "Reduced or absent fetal movement",
        "stages": ["pregnant"],
        "terms": [
            "baby is not moving", "baby isn't moving", "baby stopped moving",
            "no fetal movement", "can't feel the baby", "cannot feel the baby",
            "can't feel my baby", "haven't felt the baby", "havent felt the baby",
            "less movement", "fewer kicks", "no kicks",
            "baccha hil nahi", "bachcha hil nahi", "baby hil nahi", "halchal nahi",
            "बच्चा हिल नहीं", "हलचल नहीं",
        ],
    },
    {
        "id": "severe_abdominal_pain",
        "label": "Severe abdominal pain",
        "stages": ["pregnant", "postpartum", "trying_to_conceive"],
        "terms": [
            "severe abdominal pain", "severe stomach pain", "severe cramping",
            "unbearable pain", "pain is unbearable", "sharp constant pain",
            "pet mein tez dard", "asahaniya dard", "bahut tez dard",
            "पेट में तेज़ दर्द", "असहनीय दर्द", "बहुत तेज़ दर्द",
        ],
    },
    {
        "id": "high_fever",
        "label": "High fever",
        "stages": ["pregnant", "postpartum", "trying_to_conceive"],
        "terms": [
            "high fever", "fever of 102", "fever of 103", "fever of 104",
            "fever and chills", "tez bukhar", "bukhar aur kampkampi",
            "तेज़ बुखार", "बुखार और कंपकंपी",
        ],
    },
    {
        "id": "breathing_chest",
        "label": "Trouble breathing / chest pain",
        "stages": ["pregnant", "postpartum", "trying_to_conceive"],
        "terms": [
            "can't breathe", "cannot breathe", "trouble breathing", "short of breath",
            "shortness of breath", "chest pain", "heart is racing",
            "saans nahi aa rahi", "saans lene mein", "seene mein dard",
            "सांस नहीं आ रही", "सांस लेने में", "सीने में दर्द",
        ],
    },
    {
        "id": "fluid_leak_waters",
        "label": "Waters breaking / fluid leak before term",
        "stages": ["pregnant"],
        "terms": [
            "water broke", "waters broke", "water just broke", "leaking fluid",
            "gushing fluid", "paani nikal", "pani nikal",
            "पानी निकल",
        ],
    },
    {
        "id": "seizure_faint",
        "label": "Seizure / fainting / severe swelling",
        "stages": ["pregnant", "postpartum"],
        "terms": [
            "seizure", "convulsion", "fainted", "passed out", "blacked out",
            "sudden swelling", "face is swollen", "hands are swollen",
            "daura", "behosh", "achanak sujan",
            "दौरा", "बेहोश", "अचानक सूजन",
        ],
    },
    {
        "id": "self_harm_risk",
        "label": "Suicidal ideation / self-harm risk",
        # Every stage — perinatal mental health does not respect stage bounds.
        "stages": ["pregnant", "postpartum", "trying_to_conceive"],
        "terms": [
            "kill myself", "end my life", "want to die", "hurt myself",
            "harm myself", "harm the baby", "hurt the baby", "hurt my baby",
            "don't want to be alive", "dont want to be alive", "suicide",
            "no reason to live",
            "marna chahti", "marna chahta", "jaan de", "khudkushi", "atmahatya",
            "मरना चाहती", "मरना चाहता", "जान दे", "खुदकुशी", "आत्महत्या",
        ],
    },
]

# Terms that flag emotional distress worth a gentle care nudge (decision
# `caution`, NOT urgent): the reply proceeds but leads with support and
# surfaces the care team. Kept small — the LLM layer owns nuance.
CAUTION_RULES = [
    {
        "id": "low_mood",
        "label": "Persistent low mood / possible PPD signals",
        "stages": ["pregnant", "postpartum", "trying_to_conceive"],
        "terms": [
            "crying all the time", "crying every day", "feel hopeless",
            "feeling hopeless", "can't stop crying", "cant stop crying",
            "so anxious", "constant anxiety", "panic attack",
            "not bonding with the baby", "feel like a bad mother",
            "har waqt rona", "roti rehti", "umeed nahi", "ghabrahat",
            "हर वक्त रोना", "रोती रहती", "उम्मीद नहीं", "घबराहट",
        ],
    },
]

# Words that, appearing shortly before a matched term, mean the sentence may be
# DENYING the symptom ("no bleeding", "koi khoon nahi"). A negated match is
# never cleared by this layer — it is downgraded to UNCERTAIN and the LLM
# decides. Note "nahi/नहीं" follows the noun in Hindi ("khoon nahi aa raha"),
# so the engine checks a window on BOTH sides.
NEGATION_MARKERS = [
    "no", "not", "n't", "without", "never", "denies", "stopped having",
    "nahi", "nahin", "koi", "bina",
    "नहीं", "कोई", "बिना",
]
