"use client";

// You — profile, the personalisation switch, and the two data rights.
//
// The personalisation toggle writes to the server consent ledger; the backend
// checks that consent inside `memory.context_summary` before any remembered
// context reaches the reply prompt. It is not local UI state pretending to be a
// setting, which is what it used to be.

import { useEffect, useState } from "react";
import { Brain, Download, LockKeyhole, LifeBuoy, LogIn, LogOut, Siren, Trash2, Users } from "lucide-react";
import {
  AiraAPI, clearSession, downloadAccountArchive, signOut, VOICES,
  type ConsentFeature, type Journey, type Prefs, type User,
} from "../aira-api";
import { JOURNEY_LABEL, type ToolName } from "./types";

const JOURNEYS: Journey[] = ["trying", "pregnant", "postpartum", "loss", "exploring"];

export default function You({
  user, consent, openTool, onProfileSaved, onConsentChanged, onDeleted,
  onSignIn, onSignedOut, weeksReported, journeyIsPregnant, onWeeksSaved,
}: {
  user: User | null;
  consent: ConsentFeature[];
  openTool: (t: ToolName) => void;
  /** The week last TYPED by the person, which is what this editor prefills
   *  with. Prefilling from the counted-forward week would push the date on by
   *  however long it had been, every time anyone pressed save. */
  weeksReported: number | null;
  journeyIsPregnant: boolean;
  onWeeksSaved: () => void;
  onProfileSaved: (u: User) => void;
  onConsentChanged: (f: ConsentFeature[]) => void;
  onDeleted: () => void;
  onSignIn: () => void;
  onSignedOut: () => void;
}) {
  const [name, setName] = useState(user?.name ?? "");
  const [journey, setJourney] = useState<Journey>((user?.journey || "exploring") as Journey);
  const [language, setLanguage] = useState(user?.language ?? "English");
  const [savingProfile, setSavingProfile] = useState(false);
  const [busy, setBusy] = useState<"" | "export" | "delete" | "signout">("");
  const [note, setNote] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [prefs, setPrefs] = useState<Prefs | null>(null);

  const personalisation = consent.find((c) => c.key === "personalization");
  const partnerAccess = consent.find((c) => c.key === "partner_access");

  useEffect(() => { AiraAPI.prefs().then(setPrefs).catch(() => undefined); }, []);

  const runSignOut = async () => {
    setBusy("signout");
    setNote("");
    try {
      await signOut();
      onSignedOut();
    } catch {
      // signOut() clears the local token even when the server call fails, so
      // this browser is signed out either way; the caller reloads regardless.
      onSignedOut();
    }
  };

  const chooseVoice = async (voice: string) => {
    const previous = prefs;
    setPrefs((p) => (p ? { ...p, voice } : p));   // optimistic; reverted on failure
    try {
      setPrefs(await AiraAPI.setPrefs({ voice }));
    } catch {
      setPrefs(previous);
      setNote("Couldn't save your voice preference.");
    }
  };

  // The week, correctable.
  //
  // Android has had this since care context existed; web never called the
  // endpoint, so a week mistyped during onboarding was permanent on this
  // client. Found by the client-contract test, which noticed `weeks_reported`
  // arriving here and being read by nobody.
  const [weeks, setWeeks] = useState<string>(
    weeksReported != null ? String(weeksReported) : "");
  const [savingWeeks, setSavingWeeks] = useState(false);
  useEffect(() => {
    setWeeks(weeksReported != null ? String(weeksReported) : "");
  }, [weeksReported]);

  const saveWeeks = async () => {
    const n = Number(weeks);
    if (!Number.isInteger(n) || n < 1 || n > 45) {
      setNote("Enter a week between 1 and 45.");
      return;
    }
    setSavingWeeks(true);
    setNote("");
    try {
      await AiraAPI.updateCareContext({ weeks: n });
      onWeeksSaved();
      setNote("Week updated.");
    } catch (e) {
      setNote(e instanceof Error ? e.message : "Couldn't update your week.");
    } finally {
      setSavingWeeks(false);
    }
  };

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
      await downloadAccountArchive();
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
              {["English", "Hindi", "Hinglish"].map((l) => <option key={l}>{l}</option>)}
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

        {/* Only for a pregnancy. A week is not a thing the other journeys have,
            and offering the field to them would be the same fabrication as the
            hardcoded "Week 24" this codebase already removed. */}
        {journeyIsPregnant && (
          <div style={{ marginTop: 16 }}>
            <label className="field">
              <span>How many weeks</span>
              <input value={weeks} onChange={(e) => setWeeks(e.target.value)}
                     inputMode="numeric" placeholder="e.g. 24" />
            </label>
            <p style={{ margin: "8px 0 0", color: "var(--muted)", fontSize: 12 }}>
              Aira counts forward from the week you enter, so you only ever need
              to correct it.
            </p>
            <button className="btn-ghost" style={{ marginTop: 10 }}
                    onClick={saveWeeks} disabled={savingWeeks}>
              {savingWeeks ? "Saving…" : "Update week"}
            </button>
          </div>
        )}
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
          <span className="icon-box lilac"><Siren size={18} /></span>
          <span><strong>Emergency profile</strong><small>Care team and trusted contact</small></span>
        </button>
        {/* The partner feature shipped Android-only, so a mother on the web
            could not share anything and a partner on the web could not redeem
            a code she sent from her phone. */}
        <button onClick={() => openTool("partner")}>
          <span className="icon-box lilac"><Users size={18} /></span>
          <span>
            <strong>Partner access</strong>
            <small>
              {partnerAccess?.granted ? "On — share or revoke" : "Off — nothing is shared"}
            </small>
          </span>
        </button>
      </div>

      {personalisation && (
        <div className="control-row">
          <div>
            <strong>AI personalisation</strong>
            <small>
              When off, nothing saved under &ldquo;What Aira remembers&rdquo; shapes
              a reply — though Aira still follows this conversation and knows your
              journey. Your saved items are kept so you can review or delete them
              yourself.
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

      {/* Previously an inert row with a static icon, duplicating the language
          selector above it. The voice choice is now stored for real — but
          spoken replies genuinely don't exist yet, so the row says that rather
          than letting the setting imply a feature the build doesn't have. */}
      {prefs && (
        <section className="panel" style={{ marginTop: 18, padding: 24 }}>
          <div className="section-heading"><h3>Voice</h3></div>
          <p style={{ margin: "0 0 14px", color: "var(--muted)", fontSize: 13, lineHeight: 1.6 }}>
            Spoken replies aren&apos;t available in this build. Your choice is
            saved and will apply as soon as they are — the composer&apos;s mic
            stays disabled until then.
          </p>
          <div className="choice-row">
            {VOICES.map((v) => (
              <button
                key={v}
                className={prefs.voice === v ? "selected" : ""}
                onClick={() => chooseVoice(v)}
                aria-pressed={prefs.voice === v}
              >{v}</button>
            ))}
          </div>
        </section>
      )}

      {note && <div className="banner" role="status" style={{ marginTop: 18 }}>{note}</div>}

      {/* Account.
          Signing in and out existed nowhere inside the web app: the only way in
          was the landing page's dialog, and there was no way out at all — on a
          shared computer, a signed-in session simply stayed signed in. */}
      <section className="panel" style={{ marginTop: 18, padding: 24 }}>
        <div className="section-heading"><h3>Account</h3></div>
        {user?.kind === "account" ? (
          <>
            <p style={{ margin: "0 0 14px", color: "var(--muted)", fontSize: 14, lineHeight: 1.7 }}>
              Signed in as <strong style={{ color: "var(--ink)" }}>{user.email}</strong>. Your care
              context follows this account to any device you sign in on.
            </p>
            <button className="btn-ghost" onClick={runSignOut} disabled={busy !== ""}>
              <LogOut size={15} /> {busy === "signout" ? "Signing out…" : "Sign out"}
            </button>
            <p style={{ margin: "10px 0 0", color: "var(--muted)", fontSize: 13, lineHeight: 1.6 }}>
              Signing out ends every session on every device, and leaves this browser using
              Aira anonymously again.
            </p>
          </>
        ) : (
          <>
            <p style={{ margin: "0 0 14px", color: "var(--muted)", fontSize: 14, lineHeight: 1.7 }}>
              You&apos;re using Aira without an account. Everything you&apos;ve added lives in this
              browser — an account carries it to your other devices, and means clearing your
              browser data can&apos;t take it with it.
            </p>
            <button className="btn-ghost" onClick={onSignIn}>
              <LogIn size={15} /> Create an account or sign in
            </button>
          </>
        )}
      </section>

      <section className="danger-zone">
        <h4>Your data</h4>
        <p>
          Download everything Aira holds for you — your records as JSON and your
          documents as the original files, in one zip. Or erase it. Deletion
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
