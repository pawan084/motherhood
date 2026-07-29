"use client";

// The three cards someone sees before Aira asks them anything.
//
// Android has had this since the first-run work; the web went from the landing
// page straight into a chat that opens by asking where you are in your journey.
// That is a personal question from a product you have not been told anything
// about yet, and the answer shapes everything afterwards.
//
// The copy is deliberately identical to TutorialScreen.kt. Two clients that
// describe the same product differently is how a promise drifts, and the third
// card is a safety statement — "Aira doesn't diagnose" — which must not be
// softer in one place than the other.
//
// Skippable, and shown once per browser. Someone who skips has still been shown
// the disclaimer card's heading on the way past.

import { useState } from "react";
import { ArrowRight, HeartPulse, Lock } from "lucide-react";

export const TUTORIAL_SEEN_KEY = "aira.tutorial_seen";

/** Read once at mount via a lazy initializer, matching `onboarding.tsx`. */
export function tutorialSeen(): boolean {
  if (typeof window === "undefined") return true; // never flash it during SSR
  try { return localStorage.getItem(TUTORIAL_SEEN_KEY) === "1"; } catch { return false; }
}

export function markTutorialSeen() {
  try { localStorage.setItem(TUTORIAL_SEEN_KEY, "1"); } catch { /* private mode */ }
}

type Card = {
  eyebrow: string;
  title: string;
  body: string;
  Icon: typeof Lock | null;
  accent: "lilac" | "sage";
};

const CARDS: Card[] = [
  {
    eyebrow: "WHAT AIRA IS",
    title: "The care between care.",
    body:
      "Appointments are short and far apart. Aira is for everything in between — " +
      "questions at odd hours, reminders that matter, and one clear next step " +
      "instead of a feed to keep up with.",
    Icon: null, // the orb carries the first card, so it leads with identity
    accent: "lilac",
  },
  {
    eyebrow: "PRIVATE BY DESIGN",
    title: "Your context stays yours.",
    body:
      "Aira remembers only what helps, you can read or delete any of it, and you " +
      "can export everything at any time. Your health data is never used for " +
      "advertising — that one isn't a setting you have to find.",
    Icon: Lock,
    accent: "lilac",
  },
  {
    eyebrow: "NOT A DOCTOR",
    title: "Wellness support, not diagnosis.",
    body:
      "Aira doesn't diagnose, prescribe, or replace your care team. Every message " +
      "is screened first, and anything urgent goes straight to your care team " +
      "rather than to another AI answer.",
    Icon: HeartPulse,
    accent: "sage",
  },
];

export default function Tutorial({ onFinish }: { onFinish: () => void }) {
  const [index, setIndex] = useState(0);
  const card = CARDS[index];
  const last = index === CARDS.length - 1;

  const finish = () => { markTutorialSeen(); onFinish(); };

  return (
    <div className="tutorial">
      <div className="tutorial-card-wrap">
        <div className={`tutorial-card ${card.accent}`}>
          {card.Icon ? (
            <span className="tutorial-icon" aria-hidden="true"><card.Icon size={22} /></span>
          ) : (
            <span className="tutorial-orb" aria-hidden="true" />
          )}
          <p className="tutorial-eyebrow">{card.eyebrow}</p>
          {/* aria-live so advancing the card announces the new one: without it
              a screen-reader user hears the button they pressed and nothing
              about what changed on screen. */}
          <div aria-live="polite">
            <h2>{card.title}</h2>
            <p className="tutorial-body">{card.body}</p>
          </div>
        </div>

        <div className="tutorial-foot">
          <div className="tutorial-dots" role="presentation">
            {CARDS.map((c, i) => (
              <span key={c.eyebrow} className={i === index ? "dot on" : "dot"} />
            ))}
          </div>
          <p className="sr-only" aria-live="polite">
            Card {index + 1} of {CARDS.length}
          </p>
          <div className="tutorial-actions">
            <button className="btn-ghost" onClick={finish}>
              {last ? "Skip" : "Skip intro"}
            </button>
            <button
              className="landing-primary"
              onClick={() => (last ? finish() : setIndex(index + 1))}
            >
              {last ? "Get started" : "Next"} <ArrowRight size={16} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
