"use client";

// Today — the dashboard. Everything here comes from the backend (`/v1/today`
// and `/v1/care`); there is no hardcoded "Maya / Week 24 / Dr. Ananya Mehta"
// persona any more, which is what made the old screen wrong for every user who
// wasn't 24 weeks pregnant.

import { ArrowRight, Calendar, CalendarDays, Check, ClipboardCheck, Heart, LockKeyhole, Pill, Sparkles, Upload, Wind, type LucideIcon } from "lucide-react";
import type { CareData, CareItem, TodayData } from "../aira-api";
import { greeting, JOURNEY_LABEL, type ToolName } from "./types";

const TOOL_ICON: Record<string, LucideIcon> = {
  checkin: Heart, reminder: ClipboardCheck, medicine: Pill, appointment: CalendarDays,
  upload: Upload, wellness: Wind, symptom: Heart, careplan: ClipboardCheck, support: Heart,
};

function str(v: unknown, fallback = ""): string {
  return typeof v === "string" && v.trim() ? v : fallback;
}

export default function Today({
  today, care, loading, openTool, onNavigate, onMarkTaken, onReminderDone,
}: {
  today: TodayData | null;
  care: CareData | null;
  loading: boolean;
  openTool: (t: ToolName) => void;
  onNavigate: (s: "Aira" | "Journey" | "Care") => void;
  onMarkTaken: (id: string) => void;
  onReminderDone: (id: string, done: boolean) => void;
}) {
  const weeks = today?.weeks ?? null;
  const journeyLabel = today?.journey ? JOURNEY_LABEL[today.journey] ?? "Exploring" : "Exploring";
  const name = today?.name?.trim();
  const action = today?.next_action;
  const ActionIcon = (action && TOOL_ICON[action.tool]) || Sparkles;

  const meds = care?.medicines_due ?? [];
  const appts = care?.appointments ?? [];
  const reminders = care?.reminders ?? [];
  const plan = care?.care_plan;

  return (
    <div className="dashboard-grid">
      <section className="hero-panel">
        <div className="hero-copy">
          <p className="eyebrow">
            {weeks != null ? `Week ${weeks} · ${journeyLabel}` : journeyLabel}
          </p>
          <h2>{greeting()}{name ? `, ${name}` : ""}.</h2>
          <p>
            {today?.context_line
              || "Nothing urgent needs your attention right now. Aira will surface something only when it matters."}
          </p>
          <button className="text-link" onClick={() => onNavigate("Aira")}>
            Continue with Aira <ArrowRight size={16} />
          </button>
        </div>
        <div className="week-orbit">
          {weeks != null
            ? <><strong>{weeks}</strong><small>weeks</small></>
            : <><strong>{journeyLabel.split(" ")[0]}</strong><small>your stage</small></>}
        </div>
      </section>

      <section className="panel today-plan">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Today</p>
            <h3>Your plan</h3>
          </div>
          <button onClick={() => onNavigate("Care")}>Open Care</button>
        </div>

        {action && (
          <div className="plan-item featured">
            <span className="icon-box plum"><ActionIcon size={19} /></span>
            <div>
              <small>One meaningful next action</small>
              <strong>{action.title}</strong>
              <p>{action.detail}{action.minutes ? ` · about ${action.minutes} min` : ""}</p>
            </div>
            <button onClick={() => openTool((action.tool as ToolName) || "checkin")}>
              Start
            </button>
          </div>
        )}

        {loading && !care && (
          <>
            <div className="skeleton" style={{ height: 58, marginTop: 12 }} />
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
            <button onClick={() => onMarkTaken(m.id)}>
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
            <button onClick={() => openTool("appointment")}>Prepare</button>
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
      </section>

      <aside className="right-column">
        <section className="panel insight-card">
          <span className="insight-icon"><Sparkles size={19} /></span>
          <p className="eyebrow">Where you are</p>
          <h3>{today?.context_line || "Your care context"}</h3>
          <p>
            {today?.priorities?.length
              ? `You asked Aira to focus on ${today.priorities.join(", ").toLowerCase()}.`
              : "Tell Aira what matters most and Today will stay focused on it."}
          </p>
          <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
            <button className="btn-ghost" onClick={() => onNavigate("Journey")}>Open Journey</button>
          </div>
        </section>

        <section className="panel quick-tools">
          <div className="section-heading"><h3 style={{ fontSize: 19 }}>Quick tools</h3></div>
          <div className="quick-grid">
            <button onClick={() => openTool("checkin")}><Heart size={16} /> Check in</button>
            <button onClick={() => openTool("reminder")}><ClipboardCheck size={16} /> Reminder</button>
            <button onClick={() => openTool("symptom")}><Heart size={16} /> Track</button>
            <button onClick={() => openTool("wellness")}><Wind size={16} /> Reset</button>
          </div>
          {plan && plan.total > 0 && (
            <p style={{ margin: "14px 0 0", color: "var(--muted)", fontSize: 13 }}>
              {plan.on_track} of {plan.total} reminders on track.
            </p>
          )}
        </section>

        <section className="panel privacy-note">
          <LockKeyhole size={17} />
          <div>
            <strong>Never used for advertising</strong>
            <small>You control what Aira remembers. Review or delete it any time.</small>
          </div>
        </section>
      </aside>
    </div>
  );
}
