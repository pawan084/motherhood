/** Care: everything here is backed by real endpoints now — appointments,
 * medicines (with taken-today), the Care Vault (upload/download/delete),
 * the care plan, and the offline emergency profile.
 *
 * Downloads go through fetch (the auth header travels with it) into a blob
 * URL — a plain <a href> to the API would arrive without credentials.
 */
import { FormEvent, useEffect, useRef, useState } from "react";
import {
  CalendarDays,
  Check,
  ChevronRight,
  ClipboardCheck,
  Download,
  FileText,
  Pill,
  Plus,
  Siren,
  Trash2,
  Upload,
  X,
} from "lucide-react";

import {
  addAppointment,
  addMedicine,
  addPlanItem,
  Appointment,
  CareDocument,
  deleteAppointment,
  deleteDocument,
  deleteMedicine,
  deletePlanItem,
  fetchDocumentBlob,
  listAppointments,
  listDocuments,
  listMedicines,
  listPlan,
  markMedicineTaken,
  Medicine,
  PlanItem,
  togglePlanItem,
  uploadDocument,
} from "../api";
import UrgentHelp from "./UrgentHelp";

const DOC_KINDS = [
  { value: "prescription", label: "Prescription" },
  { value: "lab_report", label: "Lab report" },
  { value: "scan", label: "Scan" },
  { value: "other", label: "Other" },
];

function Appointments() {
  const [items, setItems] = useState<Appointment[]>([]);
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const [when, setWhen] = useState("");
  const [location, setLocation] = useState("");
  const load = () => void listAppointments().then(setItems);
  useEffect(load, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !when) return;
    await addAppointment({ title, when_ts: new Date(when).getTime() / 1000, location });
    setTitle("");
    setWhen("");
    setLocation("");
    setAdding(false);
    load();
  };

  const upcoming = items.filter((a) => a.when_ts * 1000 > Date.now() - 3600_000);

  return (
    <section className="care-section">
      <div className="care-section-head">
        <span>
          <CalendarDays size={16} /> Appointments
        </span>
        <button aria-label="Add appointment" onClick={() => setAdding(!adding)}>
          {adding ? <X size={16} /> : <Plus size={16} />}
        </button>
      </div>
      {adding && (
        <form className="care-add" onSubmit={(e) => void submit(e)}>
          <input placeholder="Title (e.g. Antenatal check)" value={title} onChange={(e) => setTitle(e.target.value)} />
          <input type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
          <input placeholder="Location (optional)" value={location} onChange={(e) => setLocation(e.target.value)} />
          <button className="primary-action" disabled={!title.trim() || !when}>
            Add appointment
          </button>
        </form>
      )}
      {upcoming.length === 0 && !adding && <p className="care-empty">No upcoming appointments saved.</p>}
      {upcoming.map((a) => (
        <div className="care-row" key={a.id}>
          <div className="care-date">
            <strong>{new Date(a.when_ts * 1000).getDate()}</strong>
            <small>{new Date(a.when_ts * 1000).toLocaleString(undefined, { month: "short" })}</small>
          </div>
          <div>
            <strong>{a.title}</strong>
            <small>
              {new Date(a.when_ts * 1000).toLocaleString(undefined, {
                weekday: "short",
                hour: "2-digit",
                minute: "2-digit",
              })}
              {a.location && ` · ${a.location}`}
            </small>
          </div>
          <button
            aria-label={`Delete ${a.title}`}
            onClick={() => void deleteAppointment(a.id).then(load)}
          >
            <Trash2 size={15} />
          </button>
        </div>
      ))}
    </section>
  );
}

function Medicines() {
  const [items, setItems] = useState<Medicine[]>([]);
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [dose, setDose] = useState("");
  const [time, setTime] = useState("");
  const load = () => void listMedicines().then(setItems);
  useEffect(load, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    await addMedicine({ name, dose, time_of_day: time || undefined });
    setName("");
    setDose("");
    setTime("");
    setAdding(false);
    load();
  };

  return (
    <section className="care-section">
      <div className="care-section-head">
        <span>
          <Pill size={16} /> Medicines
        </span>
        <button aria-label="Add medicine" onClick={() => setAdding(!adding)}>
          {adding ? <X size={16} /> : <Plus size={16} />}
        </button>
      </div>
      {adding && (
        <form className="care-add" onSubmit={(e) => void submit(e)}>
          <input placeholder="Name (e.g. Prenatal vitamin)" value={name} onChange={(e) => setName(e.target.value)} />
          <input placeholder="Dose (optional)" value={dose} onChange={(e) => setDose(e.target.value)} />
          <input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
          <button className="primary-action" disabled={!name.trim()}>
            Add medicine
          </button>
        </form>
      )}
      {items.length === 0 && !adding && <p className="care-empty">No medicines saved.</p>}
      {items.map((m) => (
        <div className="care-row" key={m.id}>
          <button
            className={m.taken_today ? "taken-dot taken" : "taken-dot"}
            aria-label={m.taken_today ? `${m.name} taken today` : `Mark ${m.name} taken`}
            disabled={m.taken_today}
            onClick={() => void markMedicineTaken(m.id).then(load)}
          >
            <Check size={14} />
          </button>
          <div>
            <strong>{m.name}</strong>
            <small>
              {[m.dose, m.time_of_day].filter(Boolean).join(" · ") || "As advised"}
            </small>
          </div>
          <button aria-label={`Delete ${m.name}`} onClick={() => void deleteMedicine(m.id).then(load)}>
            <Trash2 size={15} />
          </button>
        </div>
      ))}
      {items.length > 0 && (
        <p className="trust-note">Aira organises reminders but never starts, stops, or changes medication.</p>
      )}
    </section>
  );
}

function Vault() {
  const [items, setItems] = useState<CareDocument[]>([]);
  const [kind, setKind] = useState("prescription");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);
  const load = () => void listDocuments().then(setItems);
  useEffect(load, []);

  const upload = async (file: File) => {
    setBusy(true);
    setError("");
    try {
      await uploadDocument(file, kind);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed.");
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const download = async (doc: CareDocument) => {
    const blob = await fetchDocumentBlob(doc.id);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = doc.filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <section className="care-section">
      <div className="care-section-head">
        <span>
          <FileText size={16} /> Care Vault
        </span>
      </div>
      <div className="vault-upload">
        <select value={kind} onChange={(e) => setKind(e.target.value)} aria-label="Document type">
          {DOC_KINDS.map((k) => (
            <option key={k.value} value={k.value}>
              {k.label}
            </option>
          ))}
        </select>
        <button className="secondary-action" disabled={busy} onClick={() => fileRef.current?.click()}>
          <Upload size={15} /> {busy ? "Uploading…" : "Upload"}
        </button>
        <input
          ref={fileRef}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png"
          hidden
          onChange={(e) => e.target.files?.[0] && void upload(e.target.files[0])}
        />
      </div>
      {error && <p className="field-error">{error}</p>}
      {items.length === 0 && <p className="care-empty">PDF, JPG or PNG. Stored privately, deletable any time.</p>}
      {items.map((d) => (
        <div className="care-row" key={d.id}>
          <div>
            <strong>{d.filename}</strong>
            <small>
              {DOC_KINDS.find((k) => k.value === d.kind)?.label ?? d.kind} ·{" "}
              {(d.size / 1024).toFixed(0)} KB
            </small>
          </div>
          <button aria-label={`Download ${d.filename}`} onClick={() => void download(d)}>
            <Download size={15} />
          </button>
          <button aria-label={`Delete ${d.filename}`} onClick={() => void deleteDocument(d.id).then(load)}>
            <Trash2 size={15} />
          </button>
        </div>
      ))}
    </section>
  );
}

function CarePlan() {
  const [items, setItems] = useState<PlanItem[]>([]);
  const [title, setTitle] = useState("");
  const load = () => void listPlan().then(setItems);
  useEffect(load, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    await addPlanItem(title);
    setTitle("");
    load();
  };

  return (
    <section className="care-section">
      <div className="care-section-head">
        <span>
          <ClipboardCheck size={16} /> Care plan
        </span>
      </div>
      <form className="plan-add" onSubmit={(e) => void submit(e)}>
        <input placeholder="Add a priority…" value={title} onChange={(e) => setTitle(e.target.value)} />
        <button aria-label="Add plan item" disabled={!title.trim()}>
          <Plus size={16} />
        </button>
      </form>
      {items.map((p) => (
        <div className="care-row" key={p.id}>
          <button
            className={p.done ? "taken-dot taken" : "taken-dot"}
            aria-label={p.done ? `Mark ${p.title} not done` : `Mark ${p.title} done`}
            onClick={() => void togglePlanItem(p.id).then(load)}
          >
            <Check size={14} />
          </button>
          <div>
            <strong className={p.done ? "done-text" : ""}>{p.title}</strong>
          </div>
          <button aria-label={`Delete ${p.title}`} onClick={() => void deletePlanItem(p.id).then(load)}>
            <Trash2 size={15} />
          </button>
        </div>
      ))}
    </section>
  );
}

export default function CareScreen() {
  const [urgentOpen, setUrgentOpen] = useState(false);
  return (
    <main className="screen-content care">
      <span className="eyebrow">Care</span>
      <h1>Everything for your care, in one place.</h1>

      <button className="emergency-card" onClick={() => setUrgentOpen(true)}>
        <span>
          <Siren size={19} />
        </span>
        <div>
          <strong>Emergency profile</strong>
          <small>Stored on this device · works offline</small>
        </div>
        <ChevronRight size={17} />
      </button>

      <Appointments />
      <Medicines />
      <Vault />
      <CarePlan />
      {urgentOpen && <UrgentHelp close={() => setUrgentOpen(false)} />}
    </main>
  );
}
