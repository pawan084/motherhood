"use client";
import { useEffect, useMemo, useState } from "react";
import Shell from "@/components/Shell";
import { api, roleAtLeast, type VideoAdminItem, type VideosAdminResp } from "@/lib/api";

// The catalog's own lifecycles. A topic is only playable in the apps once it is
// approved AND published (see backend videos.py `_resolved`).
const REVIEW_STATUSES = ["pending", "in_review", "approved", "changes_requested"];
const PUBLISH_STATUSES = ["planned", "script_draft", "clinical_review", "approved", "produced", "published"];

function reviewPill(s: string): string {
  if (s === "approved") return "bg-sage/20 text-sage-deep";
  if (s === "changes_requested") return "bg-urgent/15 text-urgent";
  if (s === "in_review") return "bg-amber/15 text-amber";
  return "bg-lilac-mist text-aubergine"; // pending
}
function statusPill(s: string): string {
  if (s === "published") return "bg-sage/20 text-sage-deep";
  if (s === "produced" || s === "approved") return "bg-amber/15 text-amber";
  return "bg-lilac-mist text-aubergine";
}

type Draft = { review_status: string; status: string };

export default function VideosPage() {
  const [items, setItems] = useState<VideoAdminItem[] | null>(null);
  const [summary, setSummary] = useState<VideosAdminResp["summary"] | null>(null);
  const [error, setError] = useState("");
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [saved, setSaved] = useState<Record<string, boolean>>({});
  const [cat, setCat] = useState("all");
  const [pendingOnly, setPendingOnly] = useState(false);
  const [q, setQ] = useState("");
  const canEdit = roleAtLeast("support");

  useEffect(() => {
    api<VideosAdminResp>("/videos")
      .then((r) => {
        setItems(r.items);
        setSummary(r.summary);
        setDrafts(Object.fromEntries(r.items.map((v) => [v.id, {
          review_status: v.clinical_review.status, status: v.status,
        }])));
      })
      .catch((e) => setError(e.message));
  }, []);

  const categories = useMemo(() => {
    const seen = new Map<string, string>();
    (items ?? []).forEach((v) => seen.set(v.category, v.category_label));
    return [...seen].map(([key, label]) => ({ key, label }));
  }, [items]);

  const shown = (items ?? []).filter((v) => {
    if (cat !== "all" && v.category !== cat) return false;
    if (pendingOnly && v.clinical_review.status === "approved") return false;
    if (q && !(`${v.title} ${v.description}`.toLowerCase().includes(q.trim().toLowerCase()))) return false;
    return true;
  });

  function patchDraft(id: string, field: keyof Draft, value: string) {
    setDrafts((d) => ({ ...d, [id]: { ...d[id], [field]: value } }));
  }

  async function save(id: string) {
    setError("");
    try {
      const d = drafts[id];
      const { entry } = await api<{ entry: VideoAdminItem }>(`/videos/${id}/review`, {
        method: "POST",
        body: JSON.stringify({ review_status: d.review_status, status: d.status }),
      });
      setItems((its) => (its ? its.map((v) => (v.id === id ? entry : v)) : its));
      setSaved((s) => ({ ...s, [id]: true }));
      setTimeout(() => setSaved((s) => ({ ...s, [id]: false })), 2000);
      api<VideosAdminResp>("/videos").then((r) => setSummary(r.summary)).catch(() => undefined);
    } catch (e: any) {
      setError(e.message);
    }
  }

  return (
    <Shell title="Videos">
      {error && <p className="mb-4 text-urgent">{error}</p>}
      <p className="mb-4 text-sm text-ink-muted">
        The educational video catalog. Advance a topic through clinical review and
        publish it here — a topic only becomes playable in the apps once it is
        <b> approved</b> and <b> published</b>. Urgent topics route users to their care
        team rather than play as reassurance.
        {!canEdit && <b> Your role is read-only.</b>}
      </p>

      {summary && (
        <div className="mb-6 flex flex-wrap gap-3 text-sm">
          <span className="pill bg-lilac-mist text-aubergine">{summary.total} topics</span>
          <span className="pill bg-sage/20 text-sage-deep">{summary.published} published</span>
          <span className="pill bg-amber/15 text-amber">{summary.pending} pending review</span>
          <span className="pill bg-urgent/15 text-urgent">{summary.urgent} urgent</span>
        </div>
      )}

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select className="input max-w-[14rem]" value={cat} onChange={(e) => setCat(e.target.value)}>
          <option value="all">All categories</option>
          {categories.map((c) => <option key={c.key} value={c.key}>{c.label}</option>)}
        </select>
        <input className="input max-w-[16rem]" placeholder="Search topics" value={q}
               onChange={(e) => setQ(e.target.value)} />
        <label className="flex items-center gap-2 text-sm text-ink-muted">
          <input type="checkbox" checked={pendingOnly} onChange={(e) => setPendingOnly(e.target.checked)} />
          Needs review
        </label>
      </div>

      {!items ? (
        <p className="text-ink-muted">Loading…</p>
      ) : (
        <div className="space-y-4">
          {shown.map((v) => {
            const d = drafts[v.id];
            if (!d) return null;
            return (
              <section key={v.id} className="card space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="pill bg-lilac-mist text-aubergine">{v.category_label}</span>
                    {v.safety_level === "urgent" && <span className="pill bg-urgent/15 text-urgent">urgent</span>}
                    <span className={`pill ${statusPill(v.status)}`}>{v.status}</span>
                    <span className={`pill ${reviewPill(v.clinical_review.status)}`}>{v.clinical_review.status}</span>
                    {v.playable && <span className="pill bg-sage/20 text-sage-deep">playable</span>}
                  </div>
                  <span className="font-mono text-xs text-ink-muted">{v.id}</span>
                </div>
                <div>
                  <p className="font-semibold text-ink">{v.title}</p>
                  <p className="text-sm text-ink-muted">{v.description}</p>
                </div>
                {v.clinical_review.reviewed_by && (
                  <p className="text-xs text-ink-muted">
                    Last reviewed by {v.clinical_review.reviewed_by}
                    {v.clinical_review.specialties.length
                      ? ` · needs ${v.clinical_review.specialties.join(", ")}` : ""}
                  </p>
                )}
                <div className="flex flex-wrap items-end gap-3">
                  <label className="text-xs text-ink-muted">
                    Clinical review
                    <select className="input mt-1 block max-w-[12rem]" value={d.review_status}
                            disabled={!canEdit}
                            onChange={(e) => patchDraft(v.id, "review_status", e.target.value)}>
                      {REVIEW_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </label>
                  <label className="text-xs text-ink-muted">
                    Publish stage
                    <select className="input mt-1 block max-w-[12rem]" value={d.status}
                            disabled={!canEdit}
                            onChange={(e) => patchDraft(v.id, "status", e.target.value)}>
                      {PUBLISH_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </label>
                  <button className="btn" onClick={() => save(v.id)} disabled={!canEdit}>Save</button>
                  {saved[v.id] && <span className="pb-2 text-sm text-sage-deep">Saved</span>}
                </div>
              </section>
            );
          })}
          {shown.length === 0 && <p className="text-ink-muted">No topics match.</p>}
        </div>
      )}
    </Shell>
  );
}
