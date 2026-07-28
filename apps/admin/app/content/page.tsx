"use client";
import { useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { api, type ContentEntry } from "@/lib/api";

type ContentResp = { items: ContentEntry[] };

function statusPill(status: string): string {
  if (status === "published") return "bg-sage/20 text-sage-deep";
  if (status === "draft") return "bg-amber/15 text-amber";
  return "bg-lilac-mist text-aubergine";
}

export default function ContentPage() {
  const [items, setItems] = useState<ContentEntry[] | null>(null);
  const [error, setError] = useState("");
  const [drafts, setDrafts] = useState<Record<string, { title: string; body: string; status: string }>>({});
  const [saved, setSaved] = useState<Record<string, boolean>>({});

  useEffect(() => {
    api<ContentResp>("/content")
      .then((r) => {
        setItems(r.items);
        setDrafts(
          Object.fromEntries(
            r.items.map((e) => [e.key, { title: e.title, body: e.body, status: e.status }])
          )
        );
      })
      .catch((e) => setError(e.message));
  }, []);

  function patchDraft(key: string, field: "title" | "body" | "status", value: string) {
    setDrafts((d) => ({ ...d, [key]: { ...d[key], [field]: value } }));
  }

  async function save(key: string) {
    setError("");
    try {
      const d = drafts[key];
      const { entry } = await api<{ entry: ContentEntry }>(`/content/${key}`, {
        method: "PATCH",
        body: JSON.stringify({ title: d.title, body: d.body, status: d.status }),
      });
      setItems((items) => (items ? items.map((e) => (e.key === key ? entry : e)) : items));
      setDrafts((dd) => ({ ...dd, [key]: { title: entry.title, body: entry.body, status: entry.status } }));
      setSaved((s) => ({ ...s, [key]: true }));
      setTimeout(() => setSaved((s) => ({ ...s, [key]: false })), 2000);
    } catch (e: any) {
      setError(e.message);
    }
  }

  return (
    <Shell title="Content">
      {error && <p className="mb-4 text-urgent">{error}</p>}
      <p className="mb-6 text-sm text-ink-muted">
        Published title and body are served to users on the Journey screen. Leave a
        field blank to fall back to the reviewed in-code copy, and set an entry to
        <b> Draft</b> to take an edit out of the app without deleting it. The weekly
        pregnancy headline is derived from the user&apos;s week and is not editable here.
      </p>
      {!items ? (
        <p className="text-ink-muted">Loading…</p>
      ) : (
        <div className="space-y-6">
          {items.map((e) => {
            const d = drafts[e.key];
            if (!d) return null;
            return (
              <section key={e.key} className="card space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm font-semibold text-aubergine">{e.key}</span>
                    <span className="pill bg-lilac-mist text-aubergine">{e.journey}</span>
                    <span className={`pill ${statusPill(e.status)}`}>{e.status}</span>
                  </div>
                  <div className="text-xs text-ink-muted">
                    v{e.version}
                    {e.reviewed_by && ` · reviewed by ${e.reviewed_by}`}
                  </div>
                </div>
                <input
                  className="input"
                  value={d.title}
                  onChange={(ev) => patchDraft(e.key, "title", ev.target.value)}
                  placeholder="Title"
                />
                <textarea
                  className="input min-h-[8rem]"
                  value={d.body}
                  onChange={(ev) => patchDraft(e.key, "body", ev.target.value)}
                  placeholder="Body — blank falls back to the in-code default"
                />
                {e.journey === "pregnant" && (
                  <p className="text-xs text-ink-muted">
                    A body set here replaces the week-band copy for every pregnant
                    user. Clear it to restore week-by-week text.
                  </p>
                )}
                <div className="flex items-center gap-3">
                  <select
                    className="input max-w-[10rem]"
                    value={d.status}
                    onChange={(ev) => patchDraft(e.key, "status", ev.target.value)}
                  >
                    <option value="draft">Draft</option>
                    <option value="published">Published</option>
                  </select>
                  <button className="btn" onClick={() => save(e.key)}>Save</button>
                  {saved[e.key] && <span className="text-sm text-sage-deep">Saved</span>}
                </div>
              </section>
            );
          })}
          {items.length === 0 && <p className="text-ink-muted">No content entries.</p>}
        </div>
      )}
    </Shell>
  );
}
