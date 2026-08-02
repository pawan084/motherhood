/** You: the real controls — edit care context (the same PUT the onboarding
 * uses), language, and the standing privacy commitments. Memory review,
 * partner access, and export/delete are P4/P7 backend features, shown as
 * roadmap. */
import { useState } from "react";
import { Check, EyeOff, LockKeyhole, ShieldCheck } from "lucide-react";

import { putCareContext } from "../api";
import { useApp } from "../state";

const LANGUAGES = [
  { value: "en", label: "English" },
  { value: "hi", label: "हिन्दी" },
  { value: "hi-Latn", label: "Hinglish" },
];

const STAGE_TITLES: Record<string, string> = {
  trying_to_conceive: "Trying to conceive",
  pregnant: "Pregnant",
  postpartum: "Postpartum",
};

export default function YouScreen({ onRedoSetup }: { onRedoSetup: () => void }) {
  const { context, setContext } = useApp();
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  if (!context) return null;

  const changeLanguage = async (language: string) => {
    setSaving(true);
    setSaved(false);
    try {
      const ctx = await putCareContext({
        stage: context.stage,
        due_date: context.due_date ?? undefined,
        birth_date: context.birth_date ?? undefined,
        display_name: context.display_name,
        language,
      });
      setContext(ctx);
      setSaved(true);
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="screen-content you">
      <section className="profile-card">
        <div className="profile-avatar">{(context.display_name || "A").slice(0, 1).toUpperCase()}</div>
        <div>
          <h1>{context.display_name || "Your profile"}</h1>
          <p>
            {STAGE_TITLES[context.stage]}
            {context.week !== null && ` · Week ${context.week}`}
          </p>
        </div>
      </section>

      <div className="settings-block">
        <label className="field">
          <span>Language {saving ? "· saving…" : saved ? "· saved" : ""}</span>
          <select value={context.language} onChange={(e) => void changeLanguage(e.target.value)} disabled={saving}>
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value}>
                {l.label}
              </option>
            ))}
          </select>
        </label>
        <button className="secondary-action" onClick={onRedoSetup}>
          Update my journey details
        </button>
      </div>

      <div className="privacy-note">
        <ShieldCheck size={17} />
        <span>
          <strong>Never used for advertising</strong>
          <small>Your health data is not an ad product.</small>
        </span>
      </div>
      <div className="privacy-note">
        <LockKeyhole size={17} />
        <span>
          <strong>Guest by default</strong>
          <small>Your data is keyed to this device until you sign in.</small>
        </span>
      </div>
      <div className="privacy-note">
        <EyeOff size={17} />
        <span>
          <strong>Coming: full data controls</strong>
          <small>Review what Aira remembers, export, and delete — with the memory features.</small>
        </span>
      </div>
      <div className="privacy-note ok">
        <Check size={17} />
        <span>
          <strong>Every message is safety-checked</strong>
          <small>Urgent signs route to your care team, not to an AI reply.</small>
        </span>
      </div>
    </main>
  );
}
