"use client";

// Journey — journey-aware content from `/v1/journey`. Pregnancy copy is
// week-banded server-side, and a postpartum user never receives fetal-week
// cards, so nothing here needs to guess at the reader's stage.

import { Activity, CalendarDays, Heart, Phone, Play } from "lucide-react";
import { videoDurationLabel, type JourneyData, type VideoTopic } from "../aira-api";
import { JOURNEY_LABEL, type Screen } from "./types";

const SECTION_ICONS = [Activity, Heart, CalendarDays];

// Full-term is 40 weeks; the bar is a rough sense of progress, not a countdown.
const TERM_WEEKS = 40;

export default function Journey({
  journey, loading, weekVideo, onNavigate,
}: {
  journey: JourneyData | null;
  loading: boolean;
  weekVideo?: VideoTopic | null;
  onNavigate?: (s: Screen) => void;
}) {
  if (loading && !journey) {
    return (
      <div className="content-page">
        <div className="skeleton" style={{ height: 220, borderRadius: 18 }} />
        <div className="editorial-grid">
          <div className="skeleton" style={{ height: 150 }} />
          <div className="skeleton" style={{ height: 150 }} />
          <div className="skeleton" style={{ height: 150 }} />
        </div>
      </div>
    );
  }

  const weeks = journey?.weeks ?? null;
  const label = journey?.journey ? JOURNEY_LABEL[journey.journey] ?? "Exploring" : "Exploring";
  const pct = weeks != null ? Math.min(100, Math.round((weeks / TERM_WEEKS) * 100)) : null;

  return (
    <div className="content-page">
      <section className="panel journey-hero">
        <div>
          <p className="eyebrow">{label}</p>
          <h2>{journey?.title || "Your journey"}</h2>
          <p>{journey?.body || "Only the changes most useful to understand now."}</p>
          {pct != null && (
            <>
              <div className="progress-line"><i style={{ width: `${pct}%` }} /></div>
              <small style={{ color: "var(--muted)", fontSize: 12 }}>
                Week {weeks} of about {TERM_WEEKS} · every pregnancy runs to its own clock
              </small>
            </>
          )}
        </div>
        <div className="week-orbit large">
          {weeks != null
            ? <><strong>{weeks}</strong><small>weeks</small></>
            : <><strong>{label.split(" ")[0]}</strong><small>your stage</small></>}
        </div>
      </section>

      {weekVideo && (
        <button className="panel journey-video" onClick={() => onNavigate?.("Learn")}>
          <span className="journey-video-play"><Play size={18} /></span>
          <span className="journey-video-body">
            <small>Your week with Aira · {videoDurationLabel(weekVideo)}</small>
            <strong>{weekVideo.title}</strong>
          </span>
          <span className="journey-video-cta">Watch in Learn</span>
        </button>
      )}

      {/* What is worth a call rather than a wait, for this week.

          Placed here rather than on Today: reference material you want to find
          when you are worried, not another card competing for attention on a
          calm morning. Every signal it names is already one the safety gate
          ends a chat turn on — this says them before you need them. */}
      {journey?.call_tip && (
        <section className="panel call-tip" style={{ padding: 24, marginTop: 20 }}>
          <p className="eyebrow"><Phone size={14} /> {journey.call_tip.title}</p>
          <p style={{ margin: "10px 0 0" }}>{journey.call_tip.body}</p>
          <p className="call-tip-provenance">
            {journey.call_tip.reviewed
              ? "Reviewed by your care provider's team."
              : "General guidance, not reviewed by a clinician — your care team's advice comes first."}
          </p>
        </section>
      )}

      {journey?.this_week && (
        <section className="panel" style={{ padding: 26, marginTop: 20 }}>
          <p className="eyebrow">This week</p>
          <h3 style={{ margin: "8px 0 0", fontFamily: "var(--serif)", fontSize: 26, fontWeight: 500 }}>
            {journey.this_week}
          </h3>
        </section>
      )}

      <div className="editorial-grid">
        {(journey?.sections ?? []).map((s, i) => {
          const Icon = SECTION_ICONS[i % SECTION_ICONS.length];
          return (
            <article className="panel" key={`${s.title}-${i}`}>
              <span className="icon-box lilac"><Icon size={19} /></span>
              <h4>{s.title}</h4>
              <p>{s.text}</p>
            </article>
          );
        })}
      </div>

      <p style={{ marginTop: 26, color: "var(--muted)", fontSize: 13 }}>
        Aira is wellness support, not diagnosis or emergency care.
      </p>
    </div>
  );
}
