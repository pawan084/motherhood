/** Care: what's real today is the offline emergency profile. Medicines,
 * documents, and the care plan are P6 backend features — shown as a roadmap,
 * not as fake data pretending to work. */
import { useState } from "react";
import { ChevronRight, ClipboardCheck, FileText, Pill, Siren } from "lucide-react";

import UrgentHelp from "./UrgentHelp";

const COMING = [
  { icon: Pill, title: "Medicines", text: "Reminders and routines" },
  { icon: FileText, title: "Care Vault", text: "Prescriptions, reports and scans" },
  { icon: ClipboardCheck, title: "Care plan", text: "A few priorities, never a dashboard" },
];

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

      <div className="editorial-list">
        {COMING.map(({ icon: Icon, title, text }) => (
          <div className="coming-row" key={title}>
            <span>
              <Icon size={19} />
            </span>
            <div>
              <strong>{title}</strong>
              <small>{text}</small>
            </div>
            <em>Coming soon</em>
          </div>
        ))}
      </div>
      {urgentOpen && <UrgentHelp close={() => setUrgentOpen(false)} />}
    </main>
  );
}
