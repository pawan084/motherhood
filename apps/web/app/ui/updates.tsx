"use client";

// Updates — what actually needs attention.
//
// There is no notifications endpoint on the backend, and inventing a fake "3
// unread" badge is exactly the kind of decoration this rebuild is removing. So
// every row here is DERIVED from real state: medicines still due, appointments
// on file, reminders outstanding, a missing care-team number, and whether the
// safety classifier is currently degraded. If the list is empty, it says so.

import { AlertTriangle, Calendar, CheckCircle2, ClipboardCheck, Pill, ShieldCheck, Siren } from "lucide-react";
import type { CareData, EmergencyProfile } from "../aira-api";
import type { Screen, ToolName } from "./types";

type Update = {
  id: string;
  tone: "info" | "warn" | "urgent";
  icon: typeof Pill;
  title: string;
  detail: string;
  action?: { label: string; run: () => void };
};

function str(v: unknown, fallback = ""): string {
  return typeof v === "string" && v.trim() ? v : fallback;
}

export function buildUpdates(
  care: CareData | null,
  emergency: EmergencyProfile | null,
  degraded: boolean,
  openTool: (t: ToolName) => void,
  onNavigate: (s: Screen) => void,
  onMarkTaken: (id: string) => void,
): Update[] {
  const out: Update[] = [];

  // Safety first: an unreachable classifier and a missing emergency number are
  // the two things a maternal-wellness app should surface loudest.
  if (!emergency?.care_team_phone) {
    out.push({
      id: "no-care-number", tone: "urgent", icon: Siren,
      title: "Add your care team's number",
      detail: "Urgent help can only dial a number you've saved. Without it, Aira can only point you to local emergency services.",
      action: { label: "Add number", run: () => openTool("emergency") },
    });
  }
  if (degraded) {
    out.push({
      id: "degraded", tone: "warn", icon: ShieldCheck,
      title: "Screening is running keyword-only",
      detail: "The AI classifier is unavailable, so messages are checked by the deterministic safety list alone. You're still protected; subtle wording may be missed.",
    });
  }

  for (const m of care?.medicines_due ?? []) {
    out.push({
      id: `med-${m.id}`, tone: "info", icon: Pill,
      title: `${str(m.name, "Medicine")} is due`,
      detail: [str(m.dose), str(m.schedule), str(m.time)].filter(Boolean).join(" · ") || "As you set it",
      action: { label: "Mark taken", run: () => onMarkTaken(m.id) },
    });
  }
  for (const a of care?.appointments ?? []) {
    out.push({
      id: `appt-${a.id}`, tone: "info", icon: Calendar,
      title: `Visit with ${str(a.doctor, "your care team")}`,
      detail: [str(a.place), str(a.when)].filter(Boolean).join(" · ") || "Details not set",
      action: { label: "Prepare", run: () => openTool("appointment") },
    });
  }
  const open = (care?.reminders ?? []).filter((r) => !r.done);
  if (open.length) {
    out.push({
      id: "reminders", tone: "info", icon: ClipboardCheck,
      title: `${open.length} reminder${open.length === 1 ? "" : "s"} still open`,
      detail: open.map((r) => str(r.title, "Reminder")).slice(0, 3).join(" · "),
      action: { label: "Open Care", run: () => onNavigate("Care") },
    });
  }
  return out;
}

export default function Updates({ updates, loading }: { updates: Update[]; loading: boolean }) {
  return (
    <div className="content-page">
      <div className="page-head">
        <p className="eyebrow">Updates</p>
        <h2>What needs your attention.</h2>
      </div>

      {loading && !updates.length && <div className="skeleton" style={{ height: 90, marginTop: 20 }} />}

      {!loading && !updates.length && (
        <section className="panel" style={{ marginTop: 22, padding: 34, textAlign: "center" }}>
          <CheckCircle2 size={30} style={{ color: "#346147" }} />
          <h3 style={{ margin: "14px 0 6px", fontFamily: "var(--serif)", fontSize: 24, fontWeight: 500 }}>
            You&apos;re caught up.
          </h3>
          <p style={{ margin: 0, color: "var(--muted)", fontSize: 14 }}>
            Aira surfaces something here only when it genuinely matters.
          </p>
        </section>
      )}

      <div className="list-rows" style={{ marginTop: 12 }}>
        {updates.map((u) => {
          const Icon = u.icon;
          const box = u.tone === "urgent" ? "plum" : u.tone === "warn" ? "lilac" : "sage";
          return (
            <article key={u.id} className="panel" style={{ marginTop: 12, padding: 18 }}>
              <span className={`icon-box ${box}`}>
                {u.tone === "urgent" ? <AlertTriangle size={18} /> : <Icon size={18} />}
              </span>
              <div>
                <strong>{u.title}</strong>
                <small>{u.detail}</small>
              </div>
              {u.action && (
                <button className="btn-ghost" onClick={u.action.run}>{u.action.label}</button>
              )}
            </article>
          );
        })}
      </div>
    </div>
  );
}
