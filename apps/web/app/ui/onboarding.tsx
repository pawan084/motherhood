"use client";

// Companion-guided onboarding. Aira sits beside you (the aubergine aside, same
// brand language as the Today hero) while you answer one calm question at a
// time, and reflects back what it heard before opening Today.
//
// It captures `weeks` for a pregnancy — the previous version rendered a week
// field with no value/onChange binding and never sent it, so every pregnant
// user was stored with `weeks: null` and the backend's week-banded content
// could never fire.

import { useState } from "react";
import {
  ArrowLeft, ArrowRight, BookOpen, Check, Heart, LockKeyhole, Plus, Sparkles, Users,
} from "lucide-react";
import { AiraAPI, type Journey } from "../aira-api";
import { PRESELECT_KEY } from "./landing";

const JOURNEYS: { value: Journey; label: string; detail: string; icon: typeof Heart }[] = [
  { value: "trying", label: "Trying to conceive", detail: "Planning and preconception support", icon: Heart },
  { value: "pregnant", label: "Pregnant", detail: "Week-by-week maternal guidance", icon: Sparkles },
  { value: "postpartum", label: "Postpartum", detail: "Recovery and newborn rhythm", icon: Users },
  // Offered in first-run, not hidden in settings: somebody may be arriving here
  // because of it. The detail line promises only what the app will do.
  { value: "loss", label: "After a loss", detail: "Support at your own pace, asking nothing of you", icon: Heart },
  { value: "exploring", label: "Exploring", detail: "Look around before deciding", icon: BookOpen },
];

const PRIORITIES = ["Better sleep", "Less overwhelm", "Nutrition", "Movement", "Medicine routine", "Visit preparation"];

// The aside narrates the setup: a warm framing line per step plus the tracker.
const STEPS = [
  { label: "Your journey", hint: "Let's start where you are." },
  { label: "The basics", hint: "A little about you — at your pace." },
  { label: "What matters most", hint: "So Today stays focused on you." },
];

// A journey chosen from the landing page's cards, if any. Read once at mount
// via a lazy initializer (not an effect), so the first render is already on the
// right step. Not cleared here — StrictMode double-invokes initializers, and
// clearing would make the second call return nothing; `finish` clears it.
function preselectedJourney(): Journey | "" {
  if (typeof window === "undefined") return "";
  try { return (sessionStorage.getItem(PRESELECT_KEY) as Journey) || ""; } catch { return ""; }
}

export default function Onboarding({ onDone }: { onDone: () => void }) {
  const [journey, setJourney] = useState<Journey | "">(preselectedJourney);
  // Skip "where are you right now?" when the landing page already asked.
  const [step, setStep] = useState(() => (preselectedJourney() ? 1 : 0));
  const [name, setName] = useState("");
  const [weeks, setWeeks] = useState("");
  const [language, setLanguage] = useState("English");
  const [goals, setGoals] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const toggleGoal = (g: string) =>
    setGoals(goals.includes(g) ? goals.filter((x) => x !== g)
      : goals.length < 3 ? [...goals, g] : goals);

  const finish = async () => {
    setBusy(true); setError("");
    const parsedWeeks = Number.parseInt(weeks, 10);
    try {
      try { sessionStorage.removeItem(PRESELECT_KEY); } catch { /* private mode */ }
      await AiraAPI.onboarding({
        journey: (journey || "exploring") as Journey,
        name: name.trim() || undefined,
        language,
        priorities: goals,
        // Only send a plausible pregnancy week; the backend stores it verbatim.
        weeks: journey === "pregnant" && Number.isFinite(parsedWeeks)
          && parsedWeeks > 0 && parsedWeeks <= 45 ? parsedWeeks : undefined,
      });
      onDone();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't save your setup.");
      setBusy(false);
    }
  };

  const journeyLabel = JOURNEYS.find((j) => j.value === journey)?.label ?? "Exploring";
  const summaryLine = [
    name.trim() || null,
    journeyLabel,
    journey === "pregnant" && weeks.trim() ? `~${weeks.trim()} weeks` : null,
  ].filter(Boolean).join(" · ");

  return (
    <div className="onboarding-wrap onboarding-shell">
      <aside className="onboarding-aside">
        <p className="eyebrow">Private setup</p>
        <h2 className="aside-hint">{STEPS[step].hint}</h2>

        <ol className="setup-steps">
          {STEPS.map((s, i) => (
            <li key={s.label} className={i === step ? "current" : i < step ? "done" : ""}>
              <span className="step-dot">{i < step ? <Check size={13} /> : i + 1}</span>
              {s.label}
            </li>
          ))}
        </ol>

        <div className="onboarding-progress" aria-label={`Step ${step + 1} of 3`}>
          {[0, 1, 2].map((i) => <i key={i} className={i <= step ? "done" : ""} />)}
        </div>

        <p className="aside-privacy">
          <LockKeyhole size={14} /> Private by design. This stays yours — change anything later.
        </p>
      </aside>

      <section className="panel onboarding-main">
        {error && <div className="banner error" role="alert">{error}</div>}

        {step === 0 && (
          <>
            <p className="eyebrow">1 of 3 · Your journey</p>
            <h2>Where are you right now?</h2>
            <p>Choose a starting point. Nothing here is a diagnosis — you can change it any time.</p>
            <div className="option-list">
              {JOURNEYS.map(({ value, label, detail, icon: Icon }) => (
                <button key={value} onClick={() => { setJourney(value); setStep(1); }}>
                  <span className="option-icon"><Icon size={18} /></span>
                  <span style={{ flex: 1 }}>
                    <strong>{label}</strong>
                    <small>{detail}</small>
                  </span>
                  <ArrowRight size={16} />
                </button>
              ))}
            </div>
          </>
        )}

        {step === 1 && (
          <>
            <p className="eyebrow">2 of 3 · The basics</p>
            <h2>A little about you.</h2>
            <p>Skip anything you would rather add later.</p>
            <div className="two-col">
              <label className="field">
                <span>What should Aira call you?</span>
                <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Optional" />
              </label>
              <label className="field">
                <span>Preferred language</span>
                <select value={language} onChange={(e) => setLanguage(e.target.value)}>
                  {["English", "Hindi", "Hinglish"].map((l) => <option key={l}>{l}</option>)}
                </select>
              </label>
            </div>
            {journey === "pregnant" && (
              <label className="field" style={{ marginTop: 12 }}>
                <span>How many weeks are you? <em style={{ color: "var(--muted)" }}>Optional</em></span>
                <input
                  type="number" min={1} max={45} inputMode="numeric"
                  value={weeks} onChange={(e) => setWeeks(e.target.value)}
                  placeholder="e.g. 24"
                />
                <small style={{ color: "var(--muted)", fontSize: 12 }}>
                  This is what makes your Journey content match where you actually are.
                </small>
              </label>
            )}
            <div className="row-actions" style={{ marginTop: 20 }}>
              <button className="btn-primary" onClick={() => setStep(2)}>Continue <ArrowRight size={15} /></button>
              <button className="btn-ghost" onClick={() => setStep(2)}>I&apos;ll add this later</button>
            </div>
            <button className="onboarding-back" onClick={() => setStep(0)}>
              <ArrowLeft size={14} /> Back
            </button>
          </>
        )}

        {step === 2 && (
          <>
            <p className="eyebrow">3 of 3 · Your priorities</p>
            <h2>What would feel most helpful?</h2>
            <p>Choose up to three. Aira keeps Today focused on them.</p>
            <div className="goal-grid">
              {PRIORITIES.map((g) => (
                <button key={g} className={goals.includes(g) ? "selected" : ""} onClick={() => toggleGoal(g)}>
                  {goals.includes(g) ? <Check size={15} /> : <Plus size={15} />} {g}
                </button>
              ))}
            </div>

            {/* Reflect back what Aira understood before committing — you confirm. */}
            <div className="onboarding-summary">
              <p className="eyebrow">Here&apos;s what I&apos;ve got</p>
              <p>{summaryLine}</p>
              <p>
                {goals.length
                  ? `Focusing on ${goals.join(", ").toLowerCase()}.`
                  : "No focus picked yet — you can add one any time."}
              </p>
            </div>

            <button className="btn-primary" style={{ marginTop: 18 }} onClick={finish} disabled={busy}>
              {busy ? "Setting up…" : "Open my Today"} <ArrowRight size={15} />
            </button>
            <button className="onboarding-back" onClick={() => setStep(1)}>
              <ArrowLeft size={14} /> Back
            </button>
          </>
        )}
      </section>
    </div>
  );
}
