"use client";

// Today — the proactive-companion home. The screen has ONE job: show the single
// next step Aira suggests and let the user decide. Everything else (medicines,
// appointments, reminders) is deliberately quieter, below the fold of attention.
//
// The suggestion is a DRAFT: nothing happens until the user acts on it, and
// "Not now" makes it go away for the session (a nudge you can't dismiss is a
// nag). "Why this?" keeps the proactivity transparent — Aira never nudges
// without saying why.
//
// Wiring note: the backend's `next_action` is {tool, title, detail, minutes} —
// it carries no committable payload or machine reason yet, so the primary action
// opens the matching tool to carry the suggestion out, and the "why" is derived
// honestly from the user's own stage/priorities. When the backend returns a
// concrete draft + reason, the primary becomes a true one-tap Confirm.

import { useState } from "react";
import {
  ArrowRight, Calendar, Check, ClipboardCheck, Heart, LockKeyhole,
  Pill, Play, Sparkles, Wind,
} from "lucide-react";
import type { CareData, CareItem, TodayData } from "../aira-api";
import { greeting, isToolName, JOURNEY_LABEL, type ToolName } from "./types";
import { durationLabel, videoForWeek } from "./videos-data";

function str(v: unknown, fallback = ""): string {
  return typeof v === "string" && v.trim() ? v : fallback;
}

// The primary button says exactly what it does, per tool — never a generic
// "Confirm". People act on verbs they recognise, not on system nouns.
const PRIMARY_LABEL: Record<string, string> = {
  checkin: "Start check-in",
  reminder: "Set a reminder",
  medicine: "Add medicine",
  appointment: "Prepare for it",
  upload: "Add to vault",
  wellness: "Take two minutes",
  symptom: "Log it",
  careplan: "See the plan",
  support: "Get support",
};

// Session-scoped dismissal. A stable-enough key from the suggestion itself, so
// the same nudge doesn't reappear after "Not now" — but a genuinely new
// suggestion (different tool/title) still gets through.
const DISMISS_KEY = "aira.today.dismissed";
function readDismissed(): string[] {
  if (typeof window === "undefined") return [];
  try { return JSON.parse(sessionStorage.getItem(DISMISS_KEY) || "[]"); } catch { return []; }
}

export default function Today({
  today, care, loading, openTool, onNavigate, onMarkTaken, onReminderDone,
}: {
  today: TodayData | null;
  care: CareData | null;
  loading: boolean;
  openTool: (t: ToolName) => void;
  onNavigate: (s: "Aira" | "Journey" | "Learn" | "Care") => void;
  onMarkTaken: (id: string) => void;
  onReminderDone: (id: string, done: boolean) => void;
}) {
  const [showWhy, setShowWhy] = useState(false);
  const [dismissed, setDismissed] = useState<string[]>(readDismissed);

  const weeks = today?.weeks ?? null;
  const journeyLabel = today?.journey ? JOURNEY_LABEL[today.journey] ?? "Exploring" : "Exploring";
  const name = today?.name?.trim();

  const action = today?.next_action;
  const actionKey = action ? `${action.tool}:${action.title}` : "";
  const hasStep = !!(action && action.title && !dismissed.includes(actionKey));

  const meds = care?.medicines_due ?? [];
  const appts = care?.appointments ?? [];
  const reminders = care?.reminders ?? [];
  const plan = care?.care_plan;
  const weekVideo = today?.journey === "pregnant" ? videoForWeek(weeks) : null;

  const context = weeks != null ? `Week ${weeks} · ${journeyLabel}` : journeyLabel;

  function confirm() {
    if (!action) return;
    openTool(isToolName(action.tool) ? action.tool : "checkin");
  }
  function dismiss() {
    const next = [...dismissed, actionKey];
    setDismissed(next);
    setShowWhy(false);
    try { sessionStorage.setItem(DISMISS_KEY, JSON.stringify(next)); } catch { /* private mode */ }
  }

  // An honest reason, grounded in the user's own words — never an invented signal.
  const why = today?.priorities?.length
    ? `You asked Aira to focus on ${today.priorities.join(", ").toLowerCase()}, so it starts there.`
    : weeks != null
      ? `Chosen for where you are right now — week ${weeks} of your journey.`
      : "A gentle place to start today. You're always the one who decides.";

  return (
    <div className="today">
      {/* THE ONE NEXT STEP — the hero. Aira's suggestion, awaiting your call. */}
      <section className={hasStep ? "next-step" : "next-step is-calm"}>
        <p className="next-step-context">
          {greeting()}{name ? `, ${name}` : ""} · {context}
        </p>

        {hasStep && action ? (
          <div className="draft">
            <p className="draft-tag"><Sparkles size={14} /> Aira suggests</p>
            <h2 className="draft-title">{action.title}</h2>
            <p className="draft-detail">
              {action.detail}{action.minutes ? ` · about ${action.minutes} min` : ""}
            </p>

            <div className="draft-actions">
              <button className="draft-confirm" onClick={confirm}>
                {PRIMARY_LABEL[action.tool] || "Start"} <ArrowRight size={16} />
              </button>
              <button className="draft-dismiss" onClick={dismiss}>Not now</button>
            </div>

            <button
              className="draft-why"
              onClick={() => setShowWhy((v) => !v)}
              aria-expanded={showWhy}
            >
              {showWhy ? "Hide" : "Why this?"}
            </button>
            {showWhy && <p className="draft-why-text">{why}</p>}
          </div>
        ) : (
          <div className="draft">
            <p className="draft-tag">All caught up</p>
            <h2 className="draft-title">Nothing needs you right now.</h2>
            <p className="draft-detail">
              Aira will surface one thing — only when it matters. Until then, rest.
            </p>
            <div className="draft-actions">
              <button className="draft-confirm" onClick={() => onNavigate("Aira")}>
                Talk with Aira <ArrowRight size={16} />
              </button>
            </div>
          </div>
        )}
      </section>

      {/* Everything else, quieter. */}
      <div className="today-body">
        <main className="panel also-today">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Also today</p>
              <h3>On your plate</h3>
            </div>
            <button onClick={() => onNavigate("Care")}>Open Care</button>
          </div>

          {loading && !care && (
            <>
              <div className="skeleton" style={{ height: 58, marginTop: 4 }} />
              <div className="skeleton" style={{ height: 58, marginTop: 10 }} />
            </>
          )}

          {meds.map((m: CareItem) => (
            <div className="plan-item" key={m.id}>
              <span className="icon-box lilac"><Pill size={19} /></span>
              <div>
                <small>Medicine</small>
                <strong>{str(m.name, "Medicine")}</strong>
                <p>{[str(m.dose), str(m.schedule), str(m.time)].filter(Boolean).join(" · ") || "As you set it"}</p>
              </div>
              <button
                onClick={() => onMarkTaken(m.id)}
                aria-label={`Mark ${str(m.name, "this medicine")} as taken`}
              >
                <Check size={14} /> Mark taken
              </button>
            </div>
          ))}

          {appts.slice(0, 2).map((a: CareItem) => (
            <div className="plan-item" key={a.id}>
              <span className="icon-box sage"><Calendar size={19} /></span>
              <div>
                <small>Appointment</small>
                <strong>{str(a.doctor, "Appointment")}</strong>
                <p>{[str(a.place), str(a.when)].filter(Boolean).join(" · ") || "Details not set"}</p>
              </div>
              <button
                onClick={() => openTool("appointment")}
                aria-label={`Prepare for your visit with ${str(a.doctor, "your care team")}`}
              >Prepare</button>
            </div>
          ))}

          {reminders.slice(0, 3).map((r: CareItem) => (
            <div className={r.done ? "plan-item done" : "plan-item"} key={r.id}>
              <span className="icon-box lilac"><ClipboardCheck size={19} /></span>
              <div>
                <small>Reminder</small>
                <strong>{str(r.title, "Reminder")}</strong>
                <p>{[str(r.time), str(r.repeat)].filter(Boolean).join(" · ") || "No time set"}</p>
              </div>
              <button
                onClick={() => onReminderDone(r.id, !r.done)}
                aria-pressed={!!r.done}
                aria-label={r.done
                  ? `${str(r.title, "Reminder")} is done — select to reopen it`
                  : `Mark ${str(r.title, "this reminder")} done`}
              >
                {r.done ? <><Check size={14} /> Done</> : "Mark done"}
              </button>
            </div>
          ))}

          {!loading && !meds.length && !appts.length && !reminders.length && (
            <p className="empty-row">
              Nothing scheduled yet. Add a reminder or an appointment and it will appear here.
            </p>
          )}
        </main>

        <aside className="right-column">
          <section className="panel quick-tools">
            <div className="section-heading"><h3 style={{ fontSize: 19 }}>Quick tools</h3></div>
            <div className="quick-grid">
              <button onClick={() => openTool("checkin")}><Heart size={16} /> Check in</button>
              <button onClick={() => openTool("reminder")}><ClipboardCheck size={16} /> Reminder</button>
              <button onClick={() => openTool("symptom")}><Heart size={16} /> Track</button>
              <button onClick={() => openTool("wellness")}><Wind size={16} /> Reset</button>
            </div>
            {plan && plan.total > 0 && (
              <p className="quick-plan-note">{plan.on_track} of {plan.total} reminders on track.</p>
            )}
            <button className="text-link quick-more" onClick={() => onNavigate("Journey")}>
              Open Journey <ArrowRight size={16} />
            </button>
          </section>

          {weekVideo && (
            <button className="panel your-week" onClick={() => onNavigate("Learn")}>
              <span className="your-week-play"><Play size={16} /></span>
              <span className="your-week-body">
                <small>Your week with Aira · {durationLabel(weekVideo)}</small>
                <strong>{weekVideo.title}</strong>
              </span>
            </button>
          )}

          <section className="panel privacy-note">
            <LockKeyhole size={17} />
            <div>
              <strong>Never used for advertising</strong>
              <small>You control what Aira remembers. Review or delete it any time.</small>
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
