"use client";

// Care — everything the user has actually recorded, from `/v1/care` and the
// emergency profile. Empty states are real empty states: the previous version
// showed "Dr. Ananya Mehta · 4 documents · 1 medicine due" to every user
// regardless of what they had entered.

import { useState } from "react";
import { AlertTriangle, Calendar, Check, ClipboardCheck, FileText, Heart, Pencil, Phone, Pill, Plus, Siren, Trash2, X } from "lucide-react";
import type { CareData, CareItem, EmergencyProfile } from "../aira-api";
import { telHref } from "../aira-api";
import { formatDate, type ToolName } from "./types";

function str(v: unknown, fallback = ""): string {
  return typeof v === "string" && v.trim() ? v : fallback;
}

/**
 * Rename or remove a row, inline.
 *
 * Every care kind used to be create-only, so a typo in a doctor's name was
 * permanent and a cancelled appointment sat on Today forever. Editing here is
 * deliberately limited to the row's main label — the field people actually
 * mistype — with the full form still available through the tool sheet.
 *
 * Delete asks first. These are small rows next to each other and the action
 * can't be undone.
 */
function RowActions({
  item, label, field, onRename, onDelete,
}: {
  item: CareItem;
  label: string;
  field: string;
  onRename: (id: string, field: string, value: string) => void;
  onDelete: (id: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [value, setValue] = useState(label);

  if (editing) {
    return (
      <span style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <input
          className="input-inline"
          value={value}
          autoFocus
          aria-label={`Rename ${label}`}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && value.trim()) { onRename(item.id, field, value.trim()); setEditing(false); }
            if (e.key === "Escape") { setValue(label); setEditing(false); }
          }}
        />
        <button
          className="btn-ghost" disabled={!value.trim()}
          onClick={() => { onRename(item.id, field, value.trim()); setEditing(false); }}
        >Save</button>
        <button className="btn-ghost" aria-label="Cancel editing"
                onClick={() => { setValue(label); setEditing(false); }}><X size={14} /></button>
      </span>
    );
  }

  if (confirming) {
    return (
      <span style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <small style={{ color: "var(--muted)" }}>Remove?</small>
        <button className="btn-danger" onClick={() => onDelete(item.id)}>Yes, remove</button>
        <button className="btn-ghost" onClick={() => setConfirming(false)}>Keep</button>
      </span>
    );
  }

  return (
    <span style={{ display: "flex", gap: 4 }}>
      <button className="btn-ghost" aria-label={`Edit ${label}`} onClick={() => setEditing(true)}>
        <Pencil size={14} />
      </button>
      <button className="btn-ghost" aria-label={`Remove ${label}`} onClick={() => setConfirming(true)}>
        <Trash2 size={14} />
      </button>
    </span>
  );
}

export default function Care({
  care, emergency, timeline, loading, openTool, onMarkTaken, onReminderDone,
  onRename, onDelete,
}: {
  care: CareData | null;
  emergency: EmergencyProfile | null;
  timeline: CareItem[];
  loading: boolean;
  openTool: (t: ToolName) => void;
  onMarkTaken: (id: string) => void;
  onReminderDone: (id: string, done: boolean) => void;
  onRename: (id: string, field: string, value: string) => void;
  onDelete: (id: string) => void;
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
              {/* Three sections each had a button announcing only as "Add", so a
                  screen reader gave no way to tell them apart. The visible label
                  stays short; the accessible one says which list it adds to. */}
              <button onClick={() => openTool("appointment")} aria-label="Add an appointment">
                <Plus size={13} /> Add
              </button>
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
                  {/* Row actions repeat once per item, so the accessible name
                      carries the item — otherwise a list of appointments is
                      just "Prepare, Prepare, Prepare". */}
                  <span style={{ display: "flex", gap: 4, alignItems: "center" }}>
                    <button
                      className="btn-ghost"
                      onClick={() => openTool("appointment")}
                      aria-label={`Prepare for your visit with ${str(a.doctor, "your care team")}`}
                    >Prepare</button>
                    <RowActions item={a} label={str(a.doctor, "Appointment")} field="doctor"
                                onRename={onRename} onDelete={onDelete} />
                  </span>
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
              <button onClick={() => openTool("medicine")} aria-label="Add a medicine">
                <Plus size={13} /> Add
              </button>
            </div>
            <div className="list-rows">
              {meds.map((m: CareItem) => (
                <div key={m.id}>
                  <span className="icon-box lilac"><Pill size={18} /></span>
                  <div>
                    <strong>{str(m.name, "Medicine")}</strong>
                    <small>{[str(m.dose), str(m.schedule), str(m.time)].filter(Boolean).join(" · ") || "As you set it"}</small>
                  </div>
                  <span style={{ display: "flex", gap: 4, alignItems: "center" }}>
                    <button
                      className="btn-ghost"
                      onClick={() => onMarkTaken(m.id)}
                      aria-label={`Mark ${str(m.name, "this medicine")} as taken`}
                    >
                      <Check size={14} /> Taken
                    </button>
                    {/* The safety one: a medicine the care team stopped used to
                        stay listed as due with no way to remove it. */}
                    <RowActions item={m} label={str(m.name, "Medicine")} field="name"
                                onRename={onRename} onDelete={onDelete} />
                  </span>
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
              <button onClick={() => openTool("reminder")} aria-label="Add a reminder">
                <Plus size={13} /> Add
              </button>
            </div>
            <div className="list-rows">
              {reminders.map((r: CareItem) => (
                <div key={r.id}>
                  <span className="icon-box lilac"><ClipboardCheck size={18} /></span>
                  <div>
                    <strong>{str(r.title, "Reminder")}</strong>
                    <small>{[str(r.time), str(r.repeat)].filter(Boolean).join(" · ") || "No time set"}</small>
                  </div>
                  <span style={{ display: "flex", gap: 4, alignItems: "center" }}>
                    <button
                      className="btn-ghost"
                      onClick={() => onReminderDone(r.id, !r.done)}
                      aria-pressed={!!r.done}
                      aria-label={r.done
                        ? `${str(r.title, "Reminder")} is done — select to reopen it`
                        : `Mark ${str(r.title, "this reminder")} done`}
                    >
                      {r.done ? <><Check size={14} /> Done</> : "Mark done"}
                    </button>
                    <RowActions item={r} label={str(r.title, "Reminder")} field="title"
                                onRename={onRename} onDelete={onDelete} />
                  </span>
                </div>
              ))}
              {!loading && !reminders.length && <p className="empty-row">No reminders yet.</p>}
            </div>
          </section>

          {/* The timeline the tools have always named. Check-ins and symptom
              logs were written and never shown again — someone tracking
              symptoms to raise at an appointment had nothing to bring. */}
          <section className="panel" style={{ padding: 24 }}>
            <div className="section-heading">
              <h3>Your timeline</h3>
              <button onClick={() => openTool("symptom")} aria-label="Log a symptom">
                <Plus size={13} /> Log
              </button>
            </div>
            <div className="list-rows">
              {timeline.map((t: CareItem) => (
                <div key={t.id}>
                  <span className={t.kind === "symptom" ? "icon-box lilac" : "icon-box sage"}>
                    {t.kind === "symptom" ? <AlertTriangle size={18} /> : <Heart size={18} />}
                  </span>
                  <div>
                    <strong>
                      {t.kind === "symptom"
                        ? str(t.what, "Symptom")
                        : `Feeling ${str(t.feeling, "noted").toLowerCase()}`}
                    </strong>
                    <small>
                      {[
                        formatDate(t.created),
                        str(t.severity),
                        str(t.started),
                        typeof t.sleep_hours === "number" ? `${t.sleep_hours}h sleep` : "",
                        str(t.note),
                      ].filter(Boolean).join(" · ")}
                    </small>
                  </div>
                  <RowActions
                    item={t}
                    label={t.kind === "symptom" ? str(t.what, "Symptom") : "this check-in"}
                    field={t.kind === "symptom" ? "what" : "note"}
                    onRename={onRename}
                    onDelete={onDelete}
                  />
                </div>
              ))}
              {!timeline.length && (
                <p className="empty-row">
                  Nothing logged yet. Check-ins and symptoms you record appear here,
                  so you can look back — or show someone — later.
                </p>
              )}
            </div>
            <div className="note-line" style={{ marginTop: 16 }}>
              Logging is tracking, not diagnosis. Bring anything that worries you to
              your care team rather than waiting for a pattern.
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
