/** The landing page: shown before a device credential exists (first visit).
 * Copy adapted from ref/aira-react-web's landing, kept honest — every claim
 * here ("safety checked", "no ads", deletable data) is a real, implemented
 * behavior, not aspiration.
 *
 * The demo row swaps this browser onto a seeded persona (dev/demo builds
 * only — the endpoint doesn't exist in production, and the row hides itself
 * if the call would fail). */
import { ArrowRight, EyeOff, LockKeyhole, ShieldCheck } from "lucide-react";

const PERSONAS = [
  { key: "trying_to_conceive", label: "Trying to conceive", who: "Divya's demo" },
  { key: "pregnant", label: "Pregnant", who: "Maya · week 24" },
  { key: "postpartum", label: "Postpartum", who: "Asha · week 4" },
];

export default function Landing({ onStart, onDemo }: {
  onStart: () => void;
  onDemo: (persona: string) => void;
}) {
  return (
    <main className="landing">
      <span className="brand-orb" aria-hidden="true" />
      <h1>Your private AI companion for motherhood.</h1>
      <p className="landing-lead">
        One calm conversation for trying to conceive, pregnancy, postpartum — and
        everything between.
      </p>

      <button className="primary-action landing-cta" onClick={onStart}>
        Start with Aira <ArrowRight size={17} />
      </button>

      <div className="trust-row">
        <span><ShieldCheck size={15} /> Every message safety-checked</span>
        <span><LockKeyhole size={15} /> Private by design</span>
        <span><EyeOff size={15} /> Never used for ads</span>
      </div>

      <section className="demo-row">
        <span className="card-kicker">Explore a demo persona</span>
        <div>
          {PERSONAS.map((p) => (
            <button key={p.key} onClick={() => onDemo(p.key)}>
              <strong>{p.label}</strong>
              <small>{p.who}</small>
            </button>
          ))}
        </div>
      </section>

      <p className="landing-foot">
        Wellness support, not diagnosis or emergency care. If you feel seriously
        unwell, contact your care team — don't wait for an app.
      </p>
    </main>
  );
}
