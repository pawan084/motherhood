"use client";

// The in-conversation tools, as modal sheets.
//
// These now WRITE. In the previous build every "Save" was `setSaved(true)` and a
// 900ms timer — the sheets looked functional and persisted nothing. Each tool
// here either calls a real endpoint or plainly says it isn't wired up yet.

import { useEffect, useState } from "react";
import { AlertTriangle, Brain, CalendarDays, Clock, FileText, Heart, LifeBuoy, LockKeyhole, Pill, ScanLine, Siren, Trash2, Wind, X, type LucideIcon } from "lucide-react";
import { AiraAPI, telHref, type ConsentFeature, type EmergencyProfile, type MemoryItem } from "../aira-api";
import { formatDate, type ToolName } from "./types";

const META: Record<ToolName, { title: string; eyebrow: string; icon: LucideIcon }> = {
  checkin: { title: "How are you?", eyebrow: "Daily check-in", icon: Heart },
  reminder: { title: "Create a reminder", eyebrow: "Aira tool", icon: Clock },
  medicine: { title: "Add a medicine", eyebrow: "Care routine", icon: Pill },
  appointment: { title: "Add an appointment", eyebrow: "Visit copilot", icon: CalendarDays },
  upload: { title: "Add to Care Vault", eyebrow: "Private document", icon: ScanLine },
  wellness: { title: "A two-minute reset", eyebrow: "Guided wellness", icon: Wind },
  symptom: { title: "Log a symptom", eyebrow: "Track, don't diagnose", icon: AlertTriangle },
  memory: { title: "What Aira remembers", eyebrow: "Care context", icon: Brain },
  privacy: { title: "Privacy centre", eyebrow: "Your data, your control", icon: LockKeyhole },
  careplan: { title: "Your care plan", eyebrow: "This week", icon: FileText },
  support: { title: "Help & feedback", eyebrow: "Human support", icon: LifeBuoy },
  emergency: { title: "Emergency profile", eyebrow: "Available offline", icon: Siren },
};

export default function ToolSheet({
  tool, close, onSaved,
}: { tool: ToolName; close: () => void; onSaved: () => void }) {
  const info = META[tool];
  const Icon = info.icon;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState("");

  // Escape closes the sheet — a modal that can only be dismissed by hitting a
  // small ✕ is a keyboard trap.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") close(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  const run = async (fn: () => Promise<unknown>, message: string) => {
    setBusy(true); setError(""); setDone("");
    try {
      await fn();
      setDone(message);
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={close} role="dialog" aria-modal="true" aria-label={info.title}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="icon-box lilac"><Icon size={18} /></span>
          <div>
            <p>{info.eyebrow}</p>
            <h2>{info.title}</h2>
          </div>
          <button onClick={close} aria-label={`Close ${info.title}`}><X size={17} /></button>
        </div>
        <div className="modal-body">
          {error && <div className="banner error"><AlertTriangle size={15} /> {error}</div>}
          {done && <div className="note-line">{done}</div>}
          <Body tool={tool} busy={busy} run={run} close={close} />
        </div>
      </div>
    </div>
  );
}

type RunFn = (fn: () => Promise<unknown>, message: string) => Promise<void>;

function Body({ tool, busy, run, close }: {
  tool: ToolName; busy: boolean; run: RunFn; close: () => void;
}) {
  switch (tool) {
    case "checkin": return <CheckinTool busy={busy} run={run} />;
    case "reminder": return <ReminderTool busy={busy} run={run} />;
    case "medicine": return <MedicineTool busy={busy} run={run} />;
    case "appointment": return <AppointmentTool busy={busy} run={run} />;
    case "symptom": return <SymptomTool busy={busy} run={run} />;
    case "upload": return <UploadTool busy={busy} run={run} />;
    case "memory": return <MemoryTool />;
    case "privacy": return <PrivacyTool />;
    case "emergency": return <EmergencyTool busy={busy} run={run} />;
    case "support": return <SupportTool busy={busy} run={run} />;
    case "wellness": return <WellnessTool close={close} />;
    case "careplan": return <CarePlanTool />;
  }
}

function CheckinTool({ busy, run }: { busy: boolean; run: RunFn }) {
  const [feeling, setFeeling] = useState("");
  const [sleep, setSleep] = useState(7);
  const [note, setNote] = useState("");
  return (
    <>
      <div className="field">
        <span>How are you feeling right now?</span>
        <div className="choice-row">
          {["Good", "Okay", "Tired", "Low", "Unwell"].map((f) => (
            <button key={f} className={feeling === f ? "selected" : ""} onClick={() => setFeeling(f)}>{f}</button>
          ))}
        </div>
      </div>
      <label className="field">
        <span>Last night&apos;s sleep: {sleep}h</span>
        <input type="range" min={0} max={12} step={0.5} value={sleep}
               onChange={(e) => setSleep(Number(e.target.value))} />
      </label>
      <label className="field">
        <span>Anything else? (optional)</span>
        <textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="A private note for your timeline" />
      </label>
      <button className="btn-primary" disabled={busy || !feeling}
              onClick={() => run(() => AiraAPI.addCheckin({ feeling, sleep_hours: sleep, note }), "Check-in saved.")}>
        {busy ? "Saving…" : "Save check-in"}
      </button>
    </>
  );
}

function ReminderTool({ busy, run }: { busy: boolean; run: RunFn }) {
  const [title, setTitle] = useState("");
  const [time, setTime] = useState("20:00");
  const [repeat, setRepeat] = useState("Daily");
  return (
    <>
      <label className="field">
        <span>Remind me to</span>
        <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Take prenatal vitamin" />
      </label>
      <div className="two-col">
        <label className="field"><span>Time</span>
          <input type="time" value={time} onChange={(e) => setTime(e.target.value)} /></label>
        <label className="field"><span>Repeat</span>
          <select value={repeat} onChange={(e) => setRepeat(e.target.value)}>
            <option>Daily</option><option>Weekdays</option><option>Once</option>
          </select></label>
      </div>
      <button className="btn-primary" disabled={busy || !title.trim()}
              onClick={() => run(() => AiraAPI.addReminder({ title, time, repeat }), "Reminder set.")}>
        {busy ? "Saving…" : "Set reminder"}
      </button>
    </>
  );
}

function MedicineTool({ busy, run }: { busy: boolean; run: RunFn }) {
  const [name, setName] = useState("");
  const [dose, setDose] = useState("");
  const [time, setTime] = useState("20:00");
  return (
    <>
      <label className="field"><span>Medicine</span>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Prenatal vitamin" /></label>
      <div className="two-col">
        <label className="field"><span>Dose</span>
          <input value={dose} onChange={(e) => setDose(e.target.value)} placeholder="e.g. 1 tablet" /></label>
        <label className="field"><span>Time</span>
          <input type="time" value={time} onChange={(e) => setTime(e.target.value)} /></label>
      </div>
      <div className="note-line">
        Aira organises reminders for a routine you were already given. It never starts,
        stops or changes medication — that&apos;s your care team&apos;s decision.
      </div>
      <button className="btn-primary" disabled={busy || !name.trim()}
              onClick={() => run(() => AiraAPI.addMedicine({ name, dose, time, schedule: "Daily" }), "Medicine added.")}>
        {busy ? "Saving…" : "Add medicine"}
      </button>
    </>
  );
}

function AppointmentTool({ busy, run }: { busy: boolean; run: RunFn }) {
  const [doctor, setDoctor] = useState("");
  const [place, setPlace] = useState("");
  const [when, setWhen] = useState("");
  const [notes, setNotes] = useState("");
  return (
    <>
      <label className="field"><span>Who are you seeing?</span>
        <input value={doctor} onChange={(e) => setDoctor(e.target.value)} placeholder="e.g. Dr Mehta" /></label>
      <div className="two-col">
        <label className="field"><span>Where</span>
          <input value={place} onChange={(e) => setPlace(e.target.value)} placeholder="Clinic or hospital" /></label>
        <label className="field"><span>When</span>
          <input type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} /></label>
      </div>
      <label className="field"><span>Questions to bring (optional)</span>
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
                  placeholder="Anything you want to remember to ask" /></label>
      <button className="btn-primary" disabled={busy || !doctor.trim()}
              onClick={() => run(() => AiraAPI.addAppointment({ doctor, place, when, notes }), "Appointment saved.")}>
        {busy ? "Saving…" : "Save appointment"}
      </button>
    </>
  );
}

function SymptomTool({ busy, run }: { busy: boolean; run: RunFn }) {
  const [what, setWhat] = useState("");
  const [severity, setSeverity] = useState("Mild");
  const [started, setStarted] = useState("Today");
  const [pattern, setPattern] = useState("Comes and goes");
  return (
    <>
      <label className="field"><span>What changed?</span>
        <input value={what} onChange={(e) => setWhat(e.target.value)} placeholder="e.g. headache, swelling, nausea" /></label>
      <div className="field">
        <span>How noticeable is it?</span>
        <div className="choice-row">
          {["Mild", "Moderate", "Severe"].map((s) => (
            <button key={s} className={severity === s ? "selected" : ""} onClick={() => setSeverity(s)}>{s}</button>
          ))}
        </div>
      </div>
      <div className="two-col">
        <label className="field"><span>Started</span>
          <select value={started} onChange={(e) => setStarted(e.target.value)}>
            <option>Today</option><option>Yesterday</option><option>This week</option>
          </select></label>
        <label className="field"><span>Pattern</span>
          <select value={pattern} onChange={(e) => setPattern(e.target.value)}>
            <option>Comes and goes</option><option>Constant</option><option>Not sure</option>
          </select></label>
      </div>
      <div className={severity === "Severe" ? "note-line danger" : "note-line warn"}>
        <AlertTriangle size={15} />
        {severity === "Severe"
          ? "Severe, sudden or worrying symptoms need your care team now — don't wait for Aira."
          : "Logging a symptom is tracking, not diagnosis. If it feels worrying, contact your care team."}
      </div>
      <button className="btn-primary" disabled={busy || !what.trim()}
              onClick={() => run(() => AiraAPI.addSymptom({ what, severity, started, pattern }), "Added to your timeline.")}>
        {busy ? "Saving…" : "Add to timeline"}
      </button>
    </>
  );
}

function UploadTool({ busy, run }: { busy: boolean; run: RunFn }) {
  const [file, setFile] = useState<File | null>(null);
  const [kind, setKind] = useState("Prescription");
  return (
    <>
      <div className="field">
        <span>What is it?</span>
        <div className="choice-row">
          {["Prescription", "Lab report", "Scan", "Other"].map((k) => (
            <button key={k} className={kind === k ? "selected" : ""} onClick={() => setKind(k)}>{k}</button>
          ))}
        </div>
      </div>
      <label className="field">
        <span>Choose a file (PDF, JPG or PNG · max 20 MB)</span>
        <input type="file" accept=".pdf,.jpg,.jpeg,.png"
               onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
      </label>
      <div className="note-line">
        This build stores the file&apos;s details only — name, type and size. Byte storage
        and reading anything out of a document are separate integrations, and a
        document is never used in answers unless you approve it.
      </div>
      <button className="btn-primary" disabled={busy || !file}
              onClick={() => run(() => AiraAPI.uploadDocument(file!, kind), "Added to your Care Vault.")}>
        {busy ? "Uploading…" : "Save to Care Vault"}
      </button>
    </>
  );
}

function MemoryTool() {
  const [items, setItems] = useState<MemoryItem[] | null>(null);
  const [error, setError] = useState("");
  const load = () => AiraAPI.memory().then((r) => setItems(r.items)).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  if (error) return <div className="banner error">{error}</div>;
  if (!items) return <div className="skeleton" style={{ height: 90 }} />;
  if (!items.length) {
    return <p>Aira hasn&apos;t stored anything about you yet. Anything it does keep will appear here, and you can remove it.</p>;
  }
  return (
    <>
      <p>Only approved items shape future answers. Turning one off keeps it here but stops it being used.</p>
      <div className="list-rows">
        {items.map((m) => (
          <div key={m.id}>
            <span className="icon-box lilac"><Brain size={16} /></span>
            <div>
              <strong>{m.label}</strong>
              <small>{m.value} · added {formatDate(m.created)}</small>
            </div>
            <span style={{ display: "flex", gap: 8 }}>
              <button
                className={m.approved ? "switch on" : "switch"}
                aria-label={`Use "${m.label}" in answers`}
                aria-pressed={m.approved}
                onClick={() => AiraAPI.setMemoryApproved(m.id, !m.approved).then(load)}
              ><i /></button>
              <button className="btn-ghost" aria-label={`Forget ${m.label}`}
                      onClick={() => AiraAPI.forgetMemory(m.id).then(load)}>
                <Trash2 size={14} />
              </button>
            </span>
          </div>
        ))}
      </div>
    </>
  );
}

function PrivacyTool() {
  const [features, setFeatures] = useState<ConsentFeature[] | null>(null);
  const [history, setHistory] = useState<{ feature: string; granted: boolean; ts: number }[]>([]);
  const [error, setError] = useState("");
  const load = () => {
    AiraAPI.consent().then((r) => setFeatures(r.features)).catch((e) => setError(e.message));
    AiraAPI.consentHistory().then((r) => setHistory(r.history)).catch(() => undefined);
  };
  useEffect(() => { load(); }, []);

  if (error) return <div className="banner error">{error}</div>;
  if (!features) return <div className="skeleton" style={{ height: 120 }} />;
  return (
    <>
      <p>Every change is recorded in an append-only consent ledger, so you can see exactly what you agreed to and when.</p>
      <div className="list-rows">
        {features.map((f) => (
          <div key={f.key}>
            <div style={{ gridColumn: "1 / 3" }}>
              <strong>{f.label}</strong>
              <small>{f.locked ? "Permanently off — health data is never an ad product." : f.granted ? "On" : "Off"}</small>
            </div>
            <button
              className={f.granted ? "switch on" : "switch"}
              disabled={f.locked}
              aria-label={`Toggle ${f.label}`}
              aria-pressed={f.granted}
              onClick={() => AiraAPI.setConsent(f.key, !f.granted).then(load)}
            ><i /></button>
          </div>
        ))}
      </div>
      {history.length > 0 && (
        <>
          <p style={{ marginTop: 8, fontWeight: 650, color: "var(--ink)" }}>Consent history</p>
          <div className="list-rows">
            {history.slice(0, 8).map((h, i) => (
              <div key={i} style={{ gridTemplateColumns: "1fr auto" }}>
                <div><strong>{h.feature}</strong><small>{formatDate(h.ts)}</small></div>
                <span style={{ color: h.granted ? "#346147" : "var(--muted)", fontSize: 12, fontWeight: 700 }}>
                  {h.granted ? "granted" : "revoked"}
                </span>
              </div>
            ))}
          </div>
        </>
      )}
    </>
  );
}

function EmergencyTool({ busy, run }: { busy: boolean; run: RunFn }) {
  const [p, setP] = useState<EmergencyProfile | null>(null);
  useEffect(() => { AiraAPI.emergencyProfile().then(setP).catch(() => setP({})); }, []);
  if (!p) return <div className="skeleton" style={{ height: 140 }} />;
  // The cast is needed because a computed key of type `keyof T` widens the
  // spread result to an index signature, which no longer matches T.
  const set = (k: keyof EmergencyProfile) => (e: { target: { value: string } }) =>
    setP({ ...p, [k]: e.target.value } as EmergencyProfile);
  const href = telHref(p.care_team_phone);
  return (
    <>
      <p>These details are what Urgent help dials. Without a number, Aira can only point you to local emergency services.</p>
      <div className="two-col">
        <label className="field"><span>Care team</span>
          <input value={p.care_team_name ?? ""} onChange={set("care_team_name")} placeholder="Clinic or doctor" /></label>
        <label className="field"><span>Care team phone</span>
          <input value={p.care_team_phone ?? ""} onChange={set("care_team_phone")} placeholder="+91…" inputMode="tel" /></label>
      </div>
      <div className="two-col">
        <label className="field"><span>Emergency contact</span>
          <input value={p.emergency_contact_name ?? ""} onChange={set("emergency_contact_name")} placeholder="Name" /></label>
        <label className="field"><span>Contact phone</span>
          <input value={p.emergency_contact_phone ?? ""} onChange={set("emergency_contact_phone")} placeholder="+91…" inputMode="tel" /></label>
      </div>
      <div className="two-col">
        <label className="field"><span>Hospital</span>
          <input value={p.hospital ?? ""} onChange={set("hospital")} /></label>
        <label className="field"><span>Blood group</span>
          <input value={p.blood_group ?? ""} onChange={set("blood_group")} /></label>
      </div>
      <label className="field"><span>Allergies and notes</span>
        <textarea value={p.allergies ?? ""} onChange={set("allergies")} /></label>
      {href && <a className="call-button" href={href}>Call care team now</a>}
      <button className="btn-primary" disabled={busy}
              onClick={() => run(() => AiraAPI.putEmergencyProfile(p), "Emergency profile saved.")}>
        {busy ? "Saving…" : "Save emergency profile"}
      </button>
    </>
  );
}

function SupportTool({ busy, run }: { busy: boolean; run: RunFn }) {
  const [kind, setKind] = useState("clinical");
  const [message, setMessage] = useState("");
  return (
    <>
      <p>If something Aira said worried you, tell us. Reports go straight to the review queue next to the safety flags.</p>
      <div className="field">
        <span>What kind of concern?</span>
        <div className="choice-row">
          {[["clinical", "Clinical"], ["safety", "Safety"], ["technical", "Technical"]].map(([v, l]) => (
            <button key={v} className={kind === v ? "selected" : ""} onClick={() => setKind(v)}>{l}</button>
          ))}
        </div>
      </div>
      <label className="field"><span>What happened?</span>
        <textarea value={message} onChange={(e) => setMessage(e.target.value)}
                  placeholder="Describe the answer that concerned you" /></label>
      <button className="btn-primary" disabled={busy || !message.trim()}
              onClick={() => run(() => AiraAPI.reportAnswer(message), "Thank you — a human will review this.")}>
        {busy ? "Sending…" : "Send report"}
      </button>
      <div className="note-line warn">
        <AlertTriangle size={15} />
        For anything urgent, use Urgent help or contact your local emergency services — not this form.
      </div>
    </>
  );
}

function WellnessTool({ close }: { close: () => void }) {
  const [left, setLeft] = useState(120);
  const [running, setRunning] = useState(false);
  // "Finished" is derived rather than pushed into state by an effect, so the
  // timer never has to call setState synchronously while rendering.
  const finished = left === 0;
  const ticking = running && !finished;

  useEffect(() => {
    if (!ticking) return;
    const id = window.setTimeout(() => setLeft((s) => Math.max(0, s - 1)), 1000);
    return () => window.clearTimeout(id);
  }, [ticking, left]);

  const mm = String(Math.floor(left / 60));
  const ss = String(left % 60).padStart(2, "0");
  return (
    <>
      <p style={{ textAlign: "center" }}>
        {ticking ? "Breathe in… and soften."
          : finished ? "That's two minutes. Well done."
          : "Two quiet minutes, whenever you need them."}
      </p>
      <div style={{ textAlign: "center", fontFamily: "var(--serif)", fontSize: 54, color: "var(--aubergine)" }}>
        {mm}:{ss}
      </div>
      <div className="row-actions" style={{ justifyContent: "center" }}>
        <button className="btn-primary" onClick={() => {
          if (finished) { setLeft(120); setRunning(true); return; }
          setRunning(!running);
        }}>
          {ticking ? "Pause" : finished ? "Again" : "Begin"}
        </button>
        <button className="btn-ghost" onClick={close}>Done</button>
      </div>
    </>
  );
}

function CarePlanTool() {
  const [plan, setPlan] = useState<{ total: number; on_track: number } | null>(null);
  useEffect(() => { AiraAPI.care().then((c) => setPlan(c.care_plan)).catch(() => undefined); }, []);
  if (!plan) return <div className="skeleton" style={{ height: 80 }} />;
  const pct = plan.total ? Math.round((plan.on_track / plan.total) * 100) : 0;
  return (
    <>
      <p>{plan.total === 0
        ? "You haven't set any reminders yet. Your care plan builds itself from what you add."
        : `${plan.on_track} of ${plan.total} reminders are on track.`}</p>
      {plan.total > 0 && <div className="progress-line"><i style={{ width: `${pct}%` }} /></div>}
    </>
  );
}

export type { ToolName } from "./types";
