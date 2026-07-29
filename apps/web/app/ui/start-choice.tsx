"use client";

// How to start: create an account, sign in, or carry on without one.
//
// Sign-in existed on the web already, but only as a link in the landing nav —
// so the actual first run never offered it, and someone who wanted their care
// on more than one device had to notice a header button before entering. The
// choice now sits where the decision is, between the tutorial and the chat that
// asks about their pregnancy.
//
// "Continue without an account" is the DEFAULT and is styled level with the
// others, not as a greyed-out escape hatch. Anonymous-first is deliberate in
// this product: people trying to conceive, or after a loss, often will not
// attach their name up front, and nudging them to is the wrong instinct here.
//
// It is also skippable in the other direction — Escape or "Not now" continues
// anonymously — because an account is not required for anything Aira does.

import { useEffect } from "react";
import { ArrowRight, KeyRound, UserPlus } from "lucide-react";

export default function StartChoice({
  onCreateAccount, onSignIn, onContinue,
}: {
  onCreateAccount: () => void;
  onSignIn: () => void;
  onContinue: () => void;
}) {
  // Escape continues anonymously rather than trapping someone on a screen that
  // is not a gate.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onContinue(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onContinue]);

  return (
    <div className="start-choice">
      <div className="start-choice-inner">
        <span className="tutorial-orb" aria-hidden="true" />
        <p className="landing-eyebrow"><span />HOW WOULD YOU LIKE TO START?</p>
        <h1>You can use Aira without an account.</h1>
        <p className="start-choice-sub">
          Everything works straight away. An account only exists so your care
          context follows you to another device — you can add one later from
          <strong> You</strong>, and nothing is lost if you never do.
        </p>

        <div className="start-choice-actions">
          <button className="landing-primary" onClick={onContinue}>
            Continue without an account <ArrowRight size={16} />
          </button>
          <button className="landing-secondary" onClick={onCreateAccount}>
            <UserPlus size={15} /> Create an account
          </button>
          <button className="landing-secondary" onClick={onSignIn}>
            <KeyRound size={15} /> Sign in
          </button>
        </div>

        <p className="start-choice-note">
          Wellness support — not diagnosis or emergency care.
        </p>
      </div>
    </div>
  );
}
