/** The Urgent Help takeover + the offline emergency profile.
 *
 * The copy comes from the backend's gate payload when a turn escalated, or
 * from a static fallback when the user opened it directly from the header —
 * this screen must work with NO backend at all, which is also why the
 * emergency profile lives in localStorage.
 */
import { useState } from "react";
import { FileText, Phone, Siren, X } from "lucide-react";

import { UrgentHelpPayload } from "../api";
import { EmergencyProfile, loadEmergencyProfile, saveEmergencyProfile } from "../state";

const FALLBACK: UrgentHelpPayload = {
  headline: "Please contact your care team now.",
  body: "Do not wait for an AI response if you feel seriously unwell or are worried about your baby.",
  actions: [],
};

function EmergencyProfileSheet({ close }: { close: () => void }) {
  const [profile, setProfile] = useState<EmergencyProfile>(loadEmergencyProfile);
  const [saved, setSaved] = useState(false);
  const set = (key: keyof EmergencyProfile) => (e: { target: { value: string } }) => {
    setSaved(false);
    setProfile((p) => ({ ...p, [key]: e.target.value }));
  };
  return (
    <div className="sheet-layer" role="dialog" aria-modal="true" aria-label="Emergency profile">
      <button className="sheet-scrim" onClick={close} aria-label="Close" />
      <section className="tool-sheet">
        <header>
          <div>
            <p>Available offline · stored only on this device</p>
            <h2>Emergency profile</h2>
          </div>
          <button onClick={close} aria-label="Close emergency profile">
            <X size={18} />
          </button>
        </header>
        <div className="sheet-content">
          {(
            [
              ["name", "Your name"],
              ["stageLine", "Stage (e.g. Pregnant · Week 24)"],
              ["doctor", "Doctor"],
              ["hospital", "Hospital / clinic"],
              ["phone", "Care team phone"],
              ["notes", "Notes (allergies, medicines)"],
            ] as [keyof EmergencyProfile, string][]
          ).map(([key, label]) => (
            <label className="field" key={key}>
              <span>{label}</span>
              <input value={profile[key]} onChange={set(key)} />
            </label>
          ))}
          <button
            className="primary-action"
            onClick={() => {
              saveEmergencyProfile(profile);
              setSaved(true);
            }}
          >
            {saved ? "Saved on this device" : "Save"}
          </button>
        </div>
      </section>
    </div>
  );
}

export default function UrgentHelp({ payload, close }: { payload?: UrgentHelpPayload; close: () => void }) {
  const [showProfile, setShowProfile] = useState(false);
  const copy = payload ?? FALLBACK;
  const phone = loadEmergencyProfile().phone.trim();

  return (
    <div className="urgent-screen" role="alertdialog" aria-label="Urgent help">
      <button className="urgent-close" onClick={close} aria-label="Close urgent help">
        <X size={20} />
      </button>
      <span className="urgent-symbol">
        <Siren size={30} />
      </span>
      <span className="card-kicker danger-text">Urgent help</span>
      <h1>{copy.headline}</h1>
      <p>{copy.body}</p>
      {phone ? (
        <a className="danger-action" href={`tel:${phone}`}>
          <Phone size={17} /> Call care team
        </a>
      ) : (
        <button className="danger-action" onClick={() => setShowProfile(true)}>
          <Phone size={17} /> Add a care-team number
        </button>
      )}
      <button className="danger-outline" onClick={() => setShowProfile(true)}>
        <FileText size={17} /> Open emergency profile
      </button>
      <button className="card-link" onClick={close}>
        I'm safe for now
      </button>
      {showProfile && <EmergencyProfileSheet close={() => setShowProfile(false)} />}
    </div>
  );
}
