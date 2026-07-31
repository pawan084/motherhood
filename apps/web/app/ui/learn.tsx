"use client";

// Learn — Aira's educational video library, served by GET /v1/videos. The server
// resolves the caller's journey + gestational week (from their profile + care
// context) and returns the scoped library plus the pregnant caller's current
// week-by-week "Your Week" video. No media is produced yet, so playback shows an
// honest "in production" state; you can browse, filter, and save for later.
//
// Safety: urgent topics never offer AI reassurance. Their detail routes to the
// care team / urgent help instead, matching the server-side safety gate.

import { useEffect, useState } from "react";
import { Bookmark, BookmarkCheck, Clock, FileText, Play, Search, Siren, Sparkles, X } from "lucide-react";
import { AiraAPI, videoDurationLabel, type VideosResponse, type VideoTopic } from "../aira-api";

const WEEK_DISMISS_KEY = "aira.learn.week_dismissed";
function readDismissedWeeks(): string[] {
  if (typeof window === "undefined") return [];
  try { return JSON.parse(sessionStorage.getItem(WEEK_DISMISS_KEY) || "[]"); } catch { return []; }
}

export default function Learn({
  onOpenChat, onUrgent,
}: {
  onOpenChat: () => void;
  onUrgent: () => void;
}) {
  const [data, setData] = useState<VideosResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<Set<string>>(new Set());
  const [cat, setCat] = useState<string>("all");
  const [q, setQ] = useState("");
  const [selected, setSelected] = useState<VideoTopic | null>(null);
  const [dismissedWeeks, setDismissedWeeks] = useState<string[]>(readDismissedWeeks);

  useEffect(() => {
    let alive = true;
    AiraAPI.videos()
      .then((r) => { if (alive) { setData(r); setSaved(new Set(r.saved_ids)); } })
      .catch((e) => { if (alive) setError(e instanceof Error ? e.message : "Couldn't load videos."); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []);

  const isSaved = (id: string) => saved.has(id);
  async function toggleSave(id: string) {
    const wasSaved = saved.has(id);
    setSaved((prev) => {
      const next = new Set(prev);
      if (wasSaved) next.delete(id); else next.add(id);
      return next;
    });
    try {
      if (wasSaved) await AiraAPI.unsaveVideo(id); else await AiraAPI.saveVideo(id);
    } catch {
      // Roll back a save that didn't land, so the star never lies about the server.
      setSaved((prev) => {
        const next = new Set(prev);
        if (wasSaved) next.add(id); else next.delete(id);
        return next;
      });
    }
  }

  const weekVideo = data?.week_video ?? null;
  const showWeek = weekVideo && !dismissedWeeks.includes(weekVideo.id);
  function dismissWeek() {
    if (!weekVideo) return;
    const next = [...dismissedWeeks, weekVideo.id];
    setDismissedWeeks(next);
    try { sessionStorage.setItem(WEEK_DISMISS_KEY, JSON.stringify(next)); } catch { /* private */ }
  }

  const categories = data?.categories ?? [];
  const query = q.trim().toLowerCase();
  const list = (data?.items ?? []).filter((v) => {
    if (cat === "saved") return isSaved(v.id);
    if (cat !== "all" && v.category !== cat) return false;
    if (query && !(`${v.title} ${v.description}`.toLowerCase().includes(query))) return false;
    return true;
  });

  return (
    <div className="content-page learn">
      {showWeek && weekVideo && (
        <section className="learn-featured">
          <p className="draft-tag"><Sparkles size={14} /> Your week with Aira</p>
          <h2 className="draft-title">{weekVideo.title}</h2>
          <p className="draft-detail">{weekVideo.description} · {videoDurationLabel(weekVideo)}</p>
          <div className="draft-actions">
            {/* Named for what it opens. The backend computes `playable` —
                published AND clinically approved — and nothing in the catalogue
                is either yet. A play glyph on a button that opens a description
                promises a video that does not exist; the honest state was three
                lines above it in the page copy the whole time. */}
            <button className="draft-confirm" onClick={() => setSelected(weekVideo)}>
              {weekVideo.playable
                ? <><Play size={16} /> Watch</>
                : <><FileText size={16} /> What it covers</>}
            </button>
            <button className="draft-dismiss" onClick={() => toggleSave(weekVideo.id)}>
              {isSaved(weekVideo.id) ? "Saved" : "Save for later"}
            </button>
            <button className="draft-dismiss" onClick={dismissWeek}>Not now</button>
          </div>
        </section>
      )}

      <div className="learn-head">
        <p className="eyebrow">Learn</p>
        <h2>Short guided videos</h2>
        <p>
          Clinician-reviewed topics for where you are — in production now. Save any for
          when they&apos;re ready. Wellness support, not medical advice.
        </p>
      </div>

      {error && <div className="banner error" role="alert">{error}</div>}

      {!error && (
        <div className="learn-controls">
          <label className="learn-search">
            <Search size={16} />
            <input
              value={q} onChange={(e) => setQ(e.target.value)}
              placeholder="Search topics" aria-label="Search video topics"
            />
          </label>
          <div className="learn-chips" role="tablist" aria-label="Filter by category">
            <button className={cat === "all" ? "active" : ""} onClick={() => setCat("all")}>All</button>
            <button className={cat === "saved" ? "active" : ""} onClick={() => setCat("saved")}>
              Saved{saved.size ? ` (${saved.size})` : ""}
            </button>
            {categories.map((c) => (
              <button key={c.key} className={cat === c.key ? "active" : ""} onClick={() => setCat(c.key)}>
                {c.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {loading ? (
        <div className="learn-grid">
          {[0, 1, 2, 3, 4, 5].map((i) => <div key={i} className="skeleton" style={{ height: 190, borderRadius: 16 }} />)}
        </div>
      ) : (
        <div className="learn-grid">
          {list.map((v) => (
            <article
              key={v.id} className="video-card"
              onClick={() => setSelected(v)}
              role="button" tabIndex={0}
              onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); setSelected(v); } }}
            >
              <div className={`video-poster ${v.category}`}>
                {v.playable
                  ? <Play size={22} />
                  : <span className="poster-pending">In production</span>}
              </div>
              <div className="video-card-body">
                <div className="video-card-tags">
                  <span className="vtag">{v.category_label}</span>
                  {v.safety_level === "urgent" && <span className="vtag urgent">Urgent · see your care team</span>}
                </div>
                <h3>{v.title}</h3>
                <p>{v.description}</p>
                <div className="video-card-foot">
                  <span className="vdur"><Clock size={13} /> {videoDurationLabel(v)}</span>
                  <button
                    className="vsave"
                    onClick={(e) => { e.stopPropagation(); toggleSave(v.id); }}
                    aria-pressed={isSaved(v.id)}
                    aria-label={isSaved(v.id) ? `Remove ${v.title} from saved` : `Save ${v.title}`}
                  >
                    {isSaved(v.id) ? <><BookmarkCheck size={14} /> Saved</> : <><Bookmark size={14} /> Save</>}
                  </button>
                </div>
              </div>
            </article>
          ))}
          {!error && list.length === 0 && (
            <p className="empty-row">
              {cat === "saved" ? "Nothing saved yet. Save a topic and it will wait for you here."
                : "No topics match that yet."}
            </p>
          )}
        </div>
      )}

      <p className="learn-disclaimer">
        Every video is clinician-reviewed before it&apos;s published. Aira is wellness support,
        not diagnosis or emergency care.
      </p>

      {selected && (
        <div className="modal-backdrop" onClick={() => setSelected(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <div className="modal-head">
              <div>
                <p>{selected.category_label}</p>
                <h2>{selected.title}</h2>
              </div>
              <button onClick={() => setSelected(null)} aria-label="Close"><X size={16} /></button>
            </div>
            <div className="modal-body">
              <div className="video-stage">
                {/* "Ready to watch" with nothing to open is the same inert
                    control as a play glyph on an unfilmed topic. `playable`
                    already requires a media_url, so this checks both and the
                    link is the thing that makes the claim true. */}
                {selected.playable && selected.media_url ? (
                  <a className="video-watch" href={selected.media_url}
                     target="_blank" rel="noreferrer">
                    <Play size={26} /> Watch
                  </a>
                ) : (
                  <span>In production — this video isn&apos;t ready to play yet.</span>
                )}
              </div>

              {selected.safety_level === "urgent" ? (
                <div className="video-urgent">
                  <strong>This one is about knowing when to get help — not something to watch and wait on.</strong>
                  <p>
                    If you&apos;re experiencing this now, contact your care team or your local
                    emergency number. Aira won&apos;t try to reassure you through it.
                  </p>
                  <button className="btn-danger" onClick={onUrgent}>
                    <Siren size={15} /> Get urgent help
                  </button>
                </div>
              ) : (
                <>
                  <div>
                    <p className="vkicker">What it covers</p>
                    <p>{selected.description}</p>
                  </div>
                  <p className="video-meta">
                    <span><Clock size={13} /> {videoDurationLabel(selected)}</span>
                    <span>{selected.languages.map((l) => l.toUpperCase()).join(" · ")}</span>
                  </p>
                  <div className="video-actions">
                    <button className="btn-ghost" onClick={() => toggleSave(selected.id)}>
                      {isSaved(selected.id) ? <><BookmarkCheck size={15} /> Saved</> : <><Bookmark size={15} /> Save for later</>}
                    </button>
                    {selected.in_app_actions.includes("ask_aira") && (
                      <button className="btn-primary" onClick={onOpenChat}>
                        <Sparkles size={15} /> Ask Aira about this
                      </button>
                    )}
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
