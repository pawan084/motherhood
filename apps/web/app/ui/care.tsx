"use client";

// Care — everything the user has actually recorded, from `/v1/care` and the
// emergency profile. Empty states are real empty states: the previous version
// showed "Dr. Ananya Mehta · 4 documents · 1 medicine due" to every user
// regardless of what they had entered.

import { Calendar, Check, ClipboardCheck, FileText, Phone, Pill, Plus, Siren } from "lucide-react";
import type { CareData, CareItem, EmergencyProfile } from "../aira-api";
import { telHref } from "../aira-api";
import type { ToolName } from "./types";

function str(v: unknown, fallback = ""): string {
  return typeof v === "string" && v.trim() ? v : fallback;
}

export default function Care({
  care, emergency, loading, openTool, onMarkTaken, onReminderDone,
}: {
  care: CareData | null;
  emergency: EmergencyProfile | null;
  loading: boolean;
  openTool: (t: ToolName) => void;
  onMarkTaken: (id: string) => void;
  onReminderDone: (id: string, done: boolean) => void;
}) {
  const appts = care?.appointments ?? [];
  const meds = care?.medicines_due ?? [];
  const reminders = care?.reminders ?? [];
  const docs = care?.documents_count ?? 0;
  const careHref = telHref(emergency?.care_team_phone);

  return (
    <div className="content-page">
      <div className="page-head">
        <p className="eyebrow">Care</p>
        <h2>Everything ready when you need it.</h2>
      </div>

      <div className="stat-row">
        <div><strong>{meds.length}</strong><span>Medicines due</span></div>
        <div><strong>{appts.length}</strong><span>Appointments</span></div>
        <div><strong>{docs}</strong><span>Documents in the vault</span></div>
      </div>

      <div className="care-grid">
        <div style={{ display: "grid", gap: 22 }}>
          <section className="panel" style={{ padding: 24 }}>
            <div className="section-heading">
              <h3>Appointments</h3>
              <button onClick={() => openTool("appointment")}><Plus size={13} /> Add</button>
            </div>
            <div className="list-rows">
              {loading && !care && <div className="skeleton" style={{ height: 56 }} />}
              {appts.map((a: CareItem) => (
                <div key={a.id}>
                  <span className="icon-box sage"><Calendar size={18} /></span>
                  <div>
                    <strong>{str(a.doctor, "Appointment")}</strong>
                    <small>{[str(a.place), str(a.when)].filter(Boolean).join(" · ") || "Details not set"}</small>
                  </div>
                  <button className="btn-ghost" onClick={() => openTool("appointment")}>Prepare</button>
                </div>
              ))}
              {!loading && !appts.length && (
                <p className="empty-row">No appointments yet. Add one and Aira will help you prepare questions.</p>
              )}
            </div>
          </section>

          <section className="panel" style={{ padding: 24 }}>
            <div className="section-heading">
              <h3>Medicines</h3>
              <button onClick={() => openTool("medicine")}><Plus size={13} /> Add</button>
            </div>
            <div className="list-rows">
              {meds.map((m: CareItem) => (
                <div key={m.id}>
                  <span className="icon-box lilac"><Pill size={18} /></span>
                  <div>
                    <strong>{str(m.name, "Medicine")}</strong>
                    <small>{[str(m.dose), str(m.schedule), str(m.time)].filter(Boolean).join(" · ") || "As you set it"}</small>
                  </div>
                  <button className="btn-ghost" onClick={() => onMarkTaken(m.id)}>
                    <Check size={14} /> Taken
                  </button>
                </div>
              ))}
              {!loading && !meds.length && <p className="empty-row">Nothing due right now.</p>}
            </div>
            <div className="note-line" style={{ marginTop: 16 }}>
              Aira can organise reminders but never starts, stops or changes medication.
            </div>
          </section>

          <section className="panel" style={{ padding: 24 }}>
            <div className="section-heading">
              <h3>Reminders</h3>
              <button onClick={() => openTool("reminder")}><Plus size={13} /> Add</button>
            </div>
            <div className="list-rows">
              {reminders.map((r: CareItem) => (
                <div key={r.id}>
                  <span className="icon-box lilac"><ClipboardCheck size={18} /></span>
                  <div>
                    <strong>{str(r.title, "Reminder")}</strong>
                    <small>{[str(r.time), str(r.repeat)].filter(Boolean).join(" · ") || "No time set"}</small>
                  </div>
                  <button
                    className="btn-ghost"
                    onClick={() => onReminderDone(r.id, !r.done)}
                    aria-pressed={!!r.done}
                  >
                    {r.done ? <><Check size={14} /> Done</> : "Mark done"}
                  </button>
                </div>
              ))}
              {!loading && !reminders.length && <p className="empty-row">No reminders yet.</p>}
            </div>
          </section>
        </div>

        <aside className="care-side">
          <section className="panel" style={{ padding: 22 }}>
            <div className="section-heading"><h3 style={{ fontSize: 20 }}>Care Vault</h3></div>
            <p style={{ margin: 0, color: "var(--muted)", fontSize: 13, lineHeight: 1.6 }}>
              {docs > 0
                ? `${docs} document${docs === 1 ? "" : "s"} stored privately.`
                : "Prescriptions, reports and scans, kept private to you."}
            </p>
            <button className="btn-ghost" style={{ marginTop: 14, width: "100%" }} onClick={() => openTool("upload")}>
              <FileText size={15} /> Add a document
            </button>
            <p style={{ margin: "12px 0 0", color: "var(--muted)", fontSize: 12 }}>
              Files are never used in answers unless you approve them.
            </p>
          </section>

          <section className="panel" style={{ padding: 22 }}>
            <div className="section-heading"><h3 style={{ fontSize: 20 }}>Emergency profile</h3></div>
            <div className="list-rows" style={{ marginBottom: 14 }}>
              <div style={{ gridTemplateColumns: "1fr auto", paddingTop: 0, borderTop: 0 }}>
                <div><strong>Care team</strong><small>{emergency?.care_team_name || "Not set"}</small></div>
              </div>
              <div style={{ gridTemplateColumns: "1fr auto" }}>
                <div><strong>Hospital</strong><small>{emergency?.hospital || "Not set"}</small></div>
              </div>
              <div style={{ gridTemplateColumns: "1fr auto" }}>
                <div><strong>Emergency contact</strong><small>{emergency?.emergency_contact_name || "Not set"}</small></div>
              </div>
            </div>
            {careHref ? (
              <a className="call-button" href={careHref}><Phone size={16} /> Call care team</a>
            ) : (
              <button className="btn-ghost" style={{ width: "100%" }} onClick={() => openTool("emergency")}>
                <Siren size={15} /> Add a care-team number
              </button>
            )}
            <button className="btn-ghost" style={{ marginTop: 10, width: "100%" }} onClick={() => openTool("emergency")}>
              Edit emergency details
            </button>
          </section>
        </aside>
      </div>
    </div>
  );
}
