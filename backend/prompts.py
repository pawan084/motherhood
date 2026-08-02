"""Prompt constants for the Aira chat turn.

Deliberately CODE, not a DB registry, for now. Sayli's DB-backed prompt
registry had a documented trap: DB rows silently shadow code constants, so
editing a prompt in code does nothing on an existing install. When the P10
admin console needs editable prompts, the override direction gets decided and
documented there — until then, what you read here is what runs.

The system prompt enforces the product's non-negotiables in text: Aira gives
no diagnosis, escalates worry to the care team, and never claims to be a
clinician. The safety gate (safety.py) is the hard enforcement layer; these
instructions are the soft one. Both exist on purpose.
"""

SYSTEM_TEMPLATE = """\
You are Aira, a warm, calm maternal-wellness companion. You support people who
are trying to conceive, pregnant, or postpartum.

About the person you're talking to:
{context_block}

How you speak:
- Warm, plain, and brief. 2-5 short sentences for most replies. No lecture.
- Reply in the person's language: {language_line}. Mirror their code-switching
  (Hinglish stays Hinglish).
- Use their name sparingly and naturally, never in every message.

What you are and are not:
- You offer general information, comfort, and practical next steps.
- You are NOT a clinician and you NEVER diagnose, dose, or contradict the
  person's care team. For anything clinical, your move is to help them prepare
  the question for their care team, not to answer it as a doctor would.
- If they describe anything that sounds like a danger sign (bleeding, severe
  headache or vision changes, reduced baby movement, severe pain, high fever,
  trouble breathing, thoughts of self-harm), tell them plainly and kindly to
  contact their care team now. Do not soften it into "maybe mention it
  sometime".
- Never invent facts about their pregnancy, records, or appointments. If you
  don't know, say so.
"""

CAUTION_ADDENDUM = """\
IMPORTANT for this turn: the person's message may carry emotional distress or a
mild health worry. Lead with empathy and support before anything practical.
Explicitly remind them, gently, that their care team is there for exactly this
and it's never an overreaction to call. Keep it short and human.
"""

_STAGE_LINES = {
    "pregnant": "They are pregnant, in gestational week {week}.",
    "postpartum": "They are postpartum, week {week} after the birth.",
    "trying_to_conceive": "They are trying to conceive.",
}

_LANGUAGE_LINES = {
    "en": "English",
    "hi": "Hindi (Devanagari script)",
    "hi-Latn": "Hinglish (romanized Hindi mixed with English)",
}


def build_system(ctx: dict | None, caution: bool = False) -> str:
    """Assemble the system prompt from the learner's care context."""
    lines = []
    if ctx:
        stage_line = _STAGE_LINES.get(ctx.get("stage") or "", "")
        if stage_line:
            lines.append(stage_line.format(week=ctx.get("week") or "unknown"))
        if ctx.get("display_name"):
            lines.append(f"Their name is {ctx['display_name']}.")
    if not lines:
        lines.append("You don't yet know their stage — it's fine to gently ask "
                     "where they are in their journey.")
    language = (ctx or {}).get("language") or "en"
    prompt = SYSTEM_TEMPLATE.format(
        context_block="\n".join(f"- {line}" for line in lines),
        language_line=_LANGUAGE_LINES.get(language, "English"),
    )
    if caution:
        prompt += "\n" + CAUTION_ADDENDUM
    return prompt


# --- action cards -------------------------------------------------------------

# The typed action cards a reply may carry (ref/mockup.png: Appointment Copilot
# card, chips under the reply; ref/diagram.png: "Dynamic action card inside
# chat"). The catalog is THE contract with the client — /config serves it so
# the client never hardcodes a mirror. Handlers for what happens when a card is
# tapped land with their tabs (P4-P7).
CARD_TYPES = {
    "check_in": "A quick mood/feeling check-in",
    "reminder": "Set a reminder (medicine, appointment, self-care)",
    "appointment_copilot": "Prepare questions for an upcoming appointment",
    "document_upload": "Upload a prescription, report, or scan",
    "wellness_session": "A short guided reset (breathing, relaxation)",
    "symptom_log": "Log a symptom, mood, or sleep entry",
    "partner_task": "Share a task with a partner",
}

CARD_SUGGESTION_PROMPT = """\
You pick follow-up action cards for a maternal-wellness chat app. Given the
user's message and the assistant's reply, choose 0-3 cards that would genuinely
help RIGHT NOW. Fewer is better; zero is common. Never invent appointment or
medical details.

Available card types:
{catalog}

Reply with JSON only:
{{"cards": [{{"type": "...", "title": "...", "subtitle": "..."}}]}}

- "type" must be one of the types above, exactly.
- "title": max 6 words, imperative ("Prepare questions", "Set a reminder").
- "subtitle": max 12 words of context, may be empty.
- Same language as the assistant's reply.

User message:
{user_text}

Assistant reply:
{reply}
"""
