"use client";

// You — profile, the personalisation switch, and the two data rights.
//
// The personalisation toggle writes to the server consent ledger; the backend
// checks that consent inside `memory.context_summary` before any remembered
// context reaches the reply prompt. It is not local UI state pretending to be a
// setting, which is what it used to be.

import { useState } from "react";
import { Brain, Download, LockKeyhole, LifeBuoy, Languages, Trash2, Users } from "lucide-react";
import { AiraAPI, clearSession, downloadJson, type ConsentFeature, type Journey, type User } from "../aira-api";
import { JOURNEY_LABEL, type ToolName } from "./types";

const JOURNEYS: Journey[] = ["trying", "pregnant", "postpartum", "exploring"];

export default function You({
  user, consent, openTool, onProfileSaved, onConsentChanged, onDeleted,
}: {
  user: User | null;
  consent: ConsentFeature[];
  openTool: (t: ToolName) => void;
  onProfileSaved: (u: User) => void;
  onConsentChanged: (f: ConsentFeature[]) => void;
  onDeleted: () => void;
}) {
  const [name, setName] = useState(user?.name ?? "");
  const [journey, setJourney] = useState<Journey>((user?.journey || "exploring") as Journey);
  const [language, setLanguage] = useState(user?.language ?? "English");
  const [savingProfile, setSavingProfile] = useState(false);
  const [busy, setBusy] = useState<"" | "export" | "delete">("");
  const [note, setNote] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);

  const personalisation = consent.find((c) => c.key === "personalization");

  const saveProfile = async () => {
    setSavingProfile(true);
    setNote("");
    try {
      const { user: u } = await AiraAPI.updateProfile({ name, journey, language });
      onProfileSaved(u);
      setNote("Profile saved.");
    } catch (e) {
      setNote(e instanceof Error ? e.message : "Couldn't save your profile.");
    } finally {
      setSavingProfile(false);
    }
  };

  const toggleConsent = async (feature: string, granted: boolean) => {
    try {
      const r = await AiraAPI.setConsent(feature, granted);
      onConsentChanged(r.features);
    } catch {
      setNote("Couldn't update that setting.");
    }
  };

  const runExport = async () => {
    setBusy("export");
    setNote("");
    try {
      downloadJson("aira-data-export.json", await AiraAPI.exportAccount());
      setNote("Your data was downloaded to this device.");
    } catch (e) {
      setNote(e instanceof Error ? e.message : "Export failed.");
    } finally {
      setBusy("");
    }
  };

  const runDelete = async () => {
    if (!confirmDelete) { setConfirmDelete(true); setNote(""); return; }
    setBusy("delete");
    try {
      await AiraAPI.deleteAccount();
      clearSession();          // the old token is dead the moment this returns
      onDeleted();
    } catch (e) {
      setNote(e instanceof Error ? e.message : "Deletion failed.");
      setBusy("");
    }
  };

  return (
    <div className="content-page">
      <div className="page-head">
        <p className="eyebrow">You</p>
        <h2>Your care, your control.</h2>
      </div>

      <section className="panel" style={{ marginTop: 22, padding: 24 }}>
        <div className="section-heading"><h3>Profile</h3></div>
        <div className="two-col">
          <label className="field">
            <span>Name</span>
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="What should Aira call you?" />
          </label>
          <label className="field">
            <span>Preferred language</span>
            <select value={language} onChange={(e) => setLanguage(e.target.value)}>
              {["English", "Hindi", "Hinglish", "Spanish"].map((l) => <option key={l}>{l}</option>)}
            </select>
          </label>
        </div>
        <label className="field" style={{ marginTop: 12 }}>
          <span>Where you are</span>
          <select value={journey} onChange={(e) => setJourney(e.target.value as Journey)}>
            {JOURNEYS.map((j) => <option key={j} value={j}>{JOURNEY_LABEL[j]}</option>)}
          </select>
        </label>
        <p style={{ margin: "10px 0 0", color: "var(--muted)", fontSize: 12 }}>
          Changing this changes the guidance you receive — a postpartum profile never shows pregnancy-week content.
        </p>
        <button className="btn-primary" style={{ marginTop: 16 }} onClick={saveProfile} disabled={savingProfile}>
          {savingProfile ? "Saving…" : "Save profile"}
        </button>
      </section>

      <div className="settings-grid">
        <button onClick={() => openTool("memory")}>
          <span className="icon-box lilac"><Brain size={18} /></span>
          <span><strong>What Aira remembers</strong><small>Review or forget care context</small></span>
        </button>
        <button onClick={() => openTool("privacy")}>
          <span className="icon-box lilac"><LockKeyhole size={18} /></span>
          <span><strong>Privacy &amp; consent</strong><small>Permissions and consent history</small></span>
        </button>
        <button onClick={() => openTool("support")}>
          <span className="icon-box lilac"><LifeBuoy size={18} /></span>
          <span><strong>Help &amp; feedback</strong><small>Report an answer that worried you</small></span>
        </button>
        <button onClick={() => openTool("emergency")}>
          <span className="icon-box lilac"><Users size={18} /></span>
          <span><strong>Emergency profile</strong><small>Care team and trusted contact</small></span>
        </button>
      </div>

      {personalisation && (
        <div className="control-row">
          <div>
            <strong>AI personalisation</strong>
            <small>
              When off, nothing Aira remembers is used to shape replies. Your saved
              items are kept so you can review or delete them yourself.
            </small>
          </div>
          <button
            className={personalisation.granted ? "switch on" : "switch"}
            onClick={() => toggleConsent("personalization", !personalisation.granted)}
            aria-label="Toggle AI personalisation"
            aria-pressed={personalisation.granted}
          ><i /></button>
        </div>
      )}

      <div className="control-row">
        <div>
          <strong>Language &amp; voice</strong>
          <small>Voice conversation isn&apos;t wired up in this build.</small>
        </div>
        <Languages size={19} style={{ color: "var(--muted)" }} />
      </div>

      {note && <div className="banner" style={{ marginTop: 18 }}>{note}</div>}

      <section className="danger-zone">
        <h4>Your data</h4>
        <p>
          Export everything Aira holds for you as a JSON file, or erase it. Deletion
          is immediate and cannot be undone — it removes your profile, conversations,
          care items, memory, consent history and safety records.
        </p>
        <div className="row-actions">
          <button className="btn-ghost" onClick={runExport} disabled={busy !== ""}>
            <Download size={15} /> {busy === "export" ? "Preparing…" : "Download my data"}
          </button>
          <button className="btn-danger" onClick={runDelete} disabled={busy !== ""}>
            <Trash2 size={15} />
            {confirmDelete
              ? "Tap again to permanently delete"
              : busy === "delete" ? "Deleting…" : "Delete all my data"}
          </button>
        </div>
      </section>
    </div>
  );
}
