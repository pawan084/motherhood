"use client";
import { useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { api, type PromptRow } from "@/lib/api";

type PromptsResp = { items: PromptRow[] };

export default function PromptsPage() {
  const [items, setItems] = useState<PromptRow[] | null>(null);
  const [error, setError] = useState("");
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  function load() {
    api<PromptsResp>("/prompts")
      .then((r) => {
        setItems(r.items);
        setDrafts(Object.fromEntries(r.items.map((p) => [p.key, p.text])));
      })
      .catch((e) => setError(e.message));
  }

  useEffect(() => {
    load();
  }, []);

  async function save(key: string) {
    setError("");
    try {
      await api(`/prompts/${key}`, {
        method: "PUT",
        body: JSON.stringify({ text: drafts[key] ?? "" }),
      });
      load();
    } catch (e: any) {
      setError(e.message);
    }
  }

  async function reset(key: string) {
    setError("");
    try {
      await api(`/prompts/${key}/reset`, { method: "POST" });
      load();
    } catch (e: any) {
      setError(e.message);
    }
  }

  return (
    <Shell title="Prompts">
      <p className="mb-4 rounded-xl border border-amber/40 bg-amber/10 px-4 py-3 text-sm text-amber">
        ⚠ These prompts drive live safety classification and replies — edit carefully.
      </p>
      {error && <p className="mb-4 text-urgent">{error}</p>}
      {!items ? (
        <p className="text-ink-muted">Loading…</p>
      ) : (
        <div className="space-y-6">
          {items.map((p) => (
            <section key={p.key} className="card space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-sm font-semibold text-aubergine">{p.key}</span>
                  {!p.is_default && <span className="pill bg-amber/15 text-amber">edited</span>}
                  {p.owner_only && (
                    <span className="pill bg-aubergine/10 text-aubergine">safety control</span>
                  )}
                </div>
                <div className="text-xs text-ink-muted">
                  {p.updated_by ? `by ${p.updated_by}` : ""}
                </div>
              </div>
              <textarea
                className="input min-h-[10rem] font-mono text-xs"
                value={drafts[p.key] ?? ""}
                readOnly={!p.can_edit}
                aria-readonly={!p.can_edit}
                onChange={(e) => setDrafts((d) => ({ ...d, [p.key]: e.target.value }))}
              />
              <div className="flex items-center gap-3">
                {/* Told before typing, not after Save.
                    This prompt is where "you are NOT a doctor" lives, and the
                    classifier's is the AI half of the safety gate. Offering a
                    Save that the server will refuse means writing a replacement
                    and losing it — on the two prompts where that matters most. */}
                {p.can_edit ? (
                  <button className="btn" onClick={() => save(p.key)}>Save</button>
                ) : (
                  <p className="text-xs text-ink-muted">
                    This prompt decides how every reply is framed and screened, so only an
                    owner can change it. Ask one, or use <span className="font-mono">Reset
                    to default</span> if it needs putting back.
                  </p>
                )}
                {p.has_default && (
                  <button className="btn-ghost" onClick={() => reset(p.key)}>Reset to default</button>
                )}
              </div>
            </section>
          ))}
          {items.length === 0 && <p className="text-ink-muted">No prompts.</p>}
        </div>
      )}
    </Shell>
  );
}
