/** You: the real controls — edit care context (the same PUT the onboarding
 * uses), language, "What Aira remembers" (read AND delete — deletion takes
 * effect on the very next chat turn), the AI-personalisation consent (real
 * effect: off = no memories in prompts, no extraction), data export, and
 * delete-everything (works for guests). Partner access remains deferred —
 * it needs its own consent-flow design, not a rushed one. */
import { useEffect, useState } from "react";
import { Brain, Check, Download, LockKeyhole, ShieldCheck, Trash2, Users, X } from "lucide-react";

import {
  deleteLearnerData,
  exportData,
  forgetAllMemory,
  forgetMemoryItem,
  getConsents,
  getMemory,
  MemoryItem,
  putCareContext,
  putConsents,
} from "../api";
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

function PrivacyControls() {
  const { refresh } = useApp();
  const [personalise, setPersonalise] = useState<boolean | null>(null);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    void getConsents().then((c) => setPersonalise(c.ai_personalisation));
  }, []);

  const toggle = async () => {
    if (personalise === null) return;
    const next = !personalise;
    setPersonalise(next); // optimistic
    const saved = await putConsents({ ai_personalisation: next }).catch(() => null);
    if (saved) setPersonalise(saved.ai_personalisation);
  };

  const download = async () => {
    const blob = await exportData();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `aira-export-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const wipe = async () => {
    await deleteLearnerData();
    setConfirming(false);
    await refresh(); // context is gone -> the app returns to onboarding
  };

  return (
    <section className="memory-section">
      <div className="memory-head">
        <LockKeyhole size={17} />
        <div>
          <strong>Privacy controls</strong>
          <small>Real switches, not decoration.</small>
        </div>
      </div>
      <div className="control-line">
        <span>
          <strong>AI personalisation</strong>
          <small>Off: Aira stops using and collecting memories. Existing ones stay until you delete them.</small>
        </span>
        <button
          className={personalise ? "switch on" : "switch"}
          role="switch"
          aria-checked={personalise ?? false}
          aria-label="Toggle AI personalisation"
          disabled={personalise === null}
          onClick={() => void toggle()}
        >
          <i />
        </button>
      </div>
      <button className="secondary-action" onClick={() => void download()}>
        <Download size={15} /> Download my data (JSON)
      </button>
      {confirming ? (
        <div className="wipe-confirm">
          <p>Delete your care context, memories, medicines, documents and plan from Aira? This cannot be undone.</p>
          <div>
            <button className="danger-action" onClick={() => void wipe()}>
              <Trash2 size={15} /> Delete everything
            </button>
            <button className="card-link" onClick={() => setConfirming(false)}>
              Keep my data
            </button>
          </div>
        </div>
      ) : (
        <button className="card-link danger-text" onClick={() => setConfirming(true)}>
          Delete all my data
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
      <PrivacyControls />

      <div className="privacy-note">
        <Users size={17} />
        <span>
          <strong>Coming: partner access</strong>
          <small>Sharing tasks with a partner needs its own consent flow — it's being designed, not rushed.</small>
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
