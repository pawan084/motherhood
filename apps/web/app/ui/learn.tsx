"use client";

// Learn — Aira's educational video library. The topics come from the clinician-
// review-gated catalog (see videos-data.ts). No media is produced yet, so this is
// honest about being a preview of what's in production: you can browse, filter,
// and save topics for when they're ready. The week-timed "Your Week with Aira"
// video is featured for pregnant users (suggest-only — Watch/Save/Not now).
//
// Safety: urgent topics never offer AI reassurance. Their detail routes to the
// user's care team / urgent help instead, matching the server-side safety gate.

import { useState } from "react";
import { Bookmark, BookmarkCheck, Clock, Play, Search, Siren, Sparkles, X } from "lucide-react";
import type { TodayData } from "../aira-api";
import {
  durationLabel, videoForWeek, videosForJourney, VIDEO_CATEGORIES, type VideoTopic,
} from "./videos-data";

const SAVED_KEY = "aira.saved_videos";
const WEEK_DISMISS_KEY = "aira.learn.week_dismissed";

function readSaved(): string[] {
  if (typeof window === "undefined") return [];
  try { return JSON.parse(localStorage.getItem(SAVED_KEY) || "[]"); } catch { return []; }
}

export default function Learn({
  today, onOpenChat, onUrgent,
}: {
  today: TodayData | null;
  onOpenChat: () => void;
  onUrgent: () => void;
}) {
  const journey = today?.journey ?? "exploring";
  const weeks = today?.weeks ?? null;

  const [saved, setSaved] = useState<string[]>(readSaved);
  const [cat, setCat] = useState<string>("all");
  const [q, setQ] = useState("");
  const [selected, setSelected] = useState<VideoTopic | null>(null);
  const [weekDismissed, setWeekDismissed] = useState<boolean>(() => {
    if (typeof window === "undefined") return false;
    const wv = videoForWeek(today?.weeks ?? null);
    try { return !!wv && sessionStorage.getItem(WEEK_DISMISS_KEY) === wv.id; } catch { return false; }
  });

  const isSaved = (id: string) => saved.includes(id);
  function toggleSave(id: string) {
    const next = isSaved(id) ? saved.filter((x) => x !== id) : [...saved, id];
    setSaved(next);
    try { localStorage.setItem(SAVED_KEY, JSON.stringify(next)); } catch { /* private mode */ }
  }

  const weekVideo = journey === "pregnant" ? videoForWeek(weeks) : null;
  function dismissWeek() {
    if (weekVideo) { try { sessionStorage.setItem(WEEK_DISMISS_KEY, weekVideo.id); } catch { /* private */ } }
    setWeekDismissed(true);
  }

  const query = q.trim().toLowerCase();
  const list = videosForJourney(journey).filter((v) => {
    if (cat === "saved") return isSaved(v.id);
    if (cat !== "all" && v.category !== cat) return false;
    if (query && !(`${v.title} ${v.description}`.toLowerCase().includes(query))) return false;
    return true;
  });

  return (
    <div className="content-page learn">
      {weekVideo && !weekDismissed && (
        <section className="learn-featured">
          <p className="draft-tag"><Sparkles size={14} /> Your week with Aira</p>
          <h2 className="draft-title">{weekVideo.title}</h2>
          <p className="draft-detail">{weekVideo.description} · {durationLabel(weekVideo)}</p>
          <div className="draft-actions">
            <button className="draft-confirm" onClick={() => setSelected(weekVideo)}>
              <Play size={16} /> Preview
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
            Saved{saved.length ? ` (${saved.length})` : ""}
          </button>
          {VIDEO_CATEGORIES.map((c) => (
            <button key={c.key} className={cat === c.key ? "active" : ""} onClick={() => setCat(c.key)}>
              {c.label}
            </button>
          ))}
        </div>
      </div>

      <div className="learn-grid">
        {list.map((v) => (
          <article
            key={v.id} className="video-card"
            onClick={() => setSelected(v)}
            role="button" tabIndex={0}
            onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); setSelected(v); } }}
          >
            <div className={`video-poster ${v.category}`}><Play size={22} /></div>
            <div className="video-card-body">
              <div className="video-card-tags">
                <span className="vtag">{v.categoryLabel}</span>
                {v.safety === "urgent" && <span className="vtag urgent">Urgent · see your care team</span>}
              </div>
              <h3>{v.title}</h3>
              <p>{v.description}</p>
              <div className="video-card-foot">
                <span className="vdur"><Clock size={13} /> {durationLabel(v)}</span>
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
        {list.length === 0 && (
          <p className="empty-row">
            {cat === "saved" ? "Nothing saved yet. Save a topic and it will wait for you here."
              : "No topics match that yet."}
          </p>
        )}
      </div>

      <p className="learn-disclaimer">
        Every video is clinician-reviewed before it&apos;s published. Aira is wellness support,
        not diagnosis or emergency care.
      </p>

      {selected && (
        <div className="modal-backdrop" onClick={() => setSelected(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
            <div className="modal-head">
              <div>
                <p>{selected.categoryLabel}</p>
                <h2>{selected.title}</h2>
              </div>
              <button onClick={() => setSelected(null)} aria-label="Close"><X size={16} /></button>
            </div>
            <div className="modal-body">
              <div className="video-stage">
                <Play size={26} />
                <span>In production — this video isn&apos;t ready to play yet.</span>
              </div>

              {selected.safety === "urgent" ? (
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
                    <span><Clock size={13} /> {durationLabel(selected)}</span>
                    <span>{selected.languages.map((l) => l.toUpperCase()).join(" · ")}</span>
                  </p>
                  <div className="video-actions">
                    <button className="btn-ghost" onClick={() => toggleSave(selected.id)}>
                      {isSaved(selected.id) ? <><BookmarkCheck size={15} /> Saved</> : <><Bookmark size={15} /> Save for later</>}
                    </button>
                    {selected.actions.includes("ask_aira") && (
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
