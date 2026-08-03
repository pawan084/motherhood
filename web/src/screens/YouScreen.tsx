/** You: the real controls — edit care context (the same PUT the onboarding
 * uses), language, "What Aira remembers" (read AND delete — deletion takes
 * effect on the very next chat turn), and the standing privacy commitments.
 * Partner access and export are P7 backend features, shown as roadmap. */
import { useEffect, useState } from "react";
import { Brain, Check, EyeOff, LockKeyhole, ShieldCheck, X } from "lucide-react";

import { forgetAllMemory, forgetMemoryItem, getMemory, MemoryItem, putCareContext } from "../api";
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

const KIND_LABELS: Record<string, string> = {
  fact: "Fact",
  concern: "Concern",
  symptom: "Symptom",
  preference: "Preference",
};

function MemorySection() {
  const [items, setItems] = useState<MemoryItem[] | null>(null);
  const [failed, setFailed] = useState(false);

  const load = () => {
    getMemory()
      .then(setItems)
      .catch(() => setFailed(true));
  };
  useEffect(load, []);

  const forget = async (id: string) => {
    setItems((cur) => cur?.filter((i) => i.id !== id) ?? null);
    await forgetMemoryItem(id).catch(load); // on failure, re-sync with truth
  };

  return (
    <section className="memory-section">
      <div className="memory-head">
        <Brain size={17} />
        <div>
          <strong>What Aira remembers</strong>
          <small>From your conversations. Remove anything, any time.</small>
        </div>
      </div>
      {failed && <p className="field-error">Couldn't load memories right now.</p>}
      {items && items.length === 0 && (
        <p className="memory-empty">Nothing yet — Aira remembers small helpful facts as you chat.</p>
      )}
      {items?.map((item) => (
        <div className="memory-row" key={item.id}>
          <em>{KIND_LABELS[item.kind] ?? item.kind}</em>
          <span>{item.content}</span>
          <button aria-label={`Forget "${item.content}"`} onClick={() => void forget(item.id)}>
            <X size={15} />
          </button>
        </div>
      ))}
      {items && items.length > 0 && (
        <button
          className="card-link"
          onClick={() => {
            void forgetAllMemory().then(() => setItems([]));
          }}
        >
          Forget everything
        </button>
      )}
    </section>
  );
}

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
      <MemorySection />

      <div className="privacy-note">
        <EyeOff size={17} />
        <span>
          <strong>Coming: export & partner access</strong>
          <small>Download your data and share tasks with a partner — with the P7 features.</small>
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
