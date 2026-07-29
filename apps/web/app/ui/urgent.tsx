"use client";

// The urgent-care handoff.
//
// Reached two ways: the header/sidebar button, and — the one that matters — a
// RED result from the server safety gate, which arrives with `reply: null` so
// no AI answer is ever shown alongside it. The number comes from the user's
// emergency profile; when there isn't one the button routes to setting it
// rather than dialling a placeholder.

import { useEffect, useRef, useState } from "react";
import { FileText, Phone, Siren, X } from "lucide-react";
import { AiraAPI, telHref, type UrgentHelp } from "../aira-api";

export default function Urgent({
  payload, close, openEmergencyProfile,
}: {
  payload: UrgentHelp | null;
  close: () => void;
  openEmergencyProfile: () => void;
}) {
  // A red turn already carries the numbers, so they are derived straight from
  // the payload. Only a manual open has to go and fetch the profile.
  const [fetched, setFetched] = useState<{ care: string | null; contact: string | null } | null>(null);

  useEffect(() => {
    if (payload) return;
    AiraAPI.emergencyProfile()
      .then((ep) => setFetched({
        care: ep.care_team_phone ?? null,
        contact: ep.emergency_contact_phone ?? null,
      }))
      .catch(() => undefined);
  }, [payload]);

  const carePhone = payload ? payload.care_team.phone ?? null : fetched?.care ?? null;
  const contactPhone = payload
    ? payload.emergency_contact?.phone ?? null
    : fetched?.contact ?? null;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") close(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  // Put focus on the call action, not on the page behind the dialog.
  //
  // This is the screen where that matters most: it opens on a RED safety
  // result, and it opened with focus left wherever the user had been — so
  // someone using a keyboard or a screen reader was told (by aria-modal) that
  // the page behind is inert while still standing on it, and had to tab
  // forward through an inert screen to reach the number for their care team.
  const dialogRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const call = dialogRef.current?.querySelector<HTMLElement>('a[href^="tel:"], button:not([aria-label^="Close"])');
    (call ?? dialogRef.current)?.focus();
    return () => opener?.focus?.();
  }, []);

  const careHref = telHref(carePhone);
  const contactHref = telHref(contactPhone);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label="Urgent help">
      <div className="urgent-modal" ref={dialogRef} tabIndex={-1}>
        <button
          onClick={close}
          aria-label="Close urgent help"
          style={{
            position: "absolute", top: 18, right: 18, width: 34, height: 34,
            display: "grid", placeItems: "center", border: "1px solid var(--line)",
            borderRadius: "50%", background: "#fff", color: "var(--muted)",
          }}
        ><X size={16} /></button>

        <span className="urgent-symbol"><Siren size={30} /></span>
        <p className="eyebrow" style={{ color: "var(--red)" }}>Urgent help</p>
        <h1>{payload?.headline ?? "Please contact your care team now."}</h1>
        <p>
          {payload?.message
            ?? "Do not wait for an AI response if you feel seriously unwell or are worried about your baby."}
        </p>

        <div className="urgent-actions">
          {careHref ? (
            <a className="call-button" href={careHref}>
              <Phone size={17} /> Call {payload?.care_team.name || "care team"}
            </a>
          ) : (
            <button className="call-button" onClick={openEmergencyProfile}>
              <Phone size={17} /> Add a care-team number
            </button>
          )}
          {contactHref && (
            <a className="btn-ghost" href={contactHref} style={{ textDecoration: "none" }}>
              <Phone size={15} /> Call emergency contact
            </a>
          )}
          <button className="btn-ghost" onClick={openEmergencyProfile}>
            <FileText size={15} /> Open emergency profile
          </button>
          <p style={{ margin: "6px 0 0", color: "var(--muted)", fontSize: 13 }}>
            If you cannot reach them, contact your local emergency services.
          </p>
          <button className="btn-ghost" style={{ border: 0 }} onClick={close}>
            I&apos;m safe for now
          </button>
        </div>
      </div>
    </div>
  );
}
