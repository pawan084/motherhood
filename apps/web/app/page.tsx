"use client";

import AiraApp from "./ui/aira-app";
import Landing from "./ui/landing";
import { useInApp } from "./ui/use-hash";

export default function Home() {
  // Whether we're in the app is part of the URL (#/app/...), so the app is
  // linkable and survives a reload instead of living in component state.
  const [insideApp, setInsideApp] = useInApp();

  if (insideApp) return <AiraApp onExit={() => setInsideApp(false)} />;

  // "Sign in" now goes to the dedicated /login page rather than a modal overlay.
  // (The in-app sign-in — from the You screen — still uses the modal.)
  return (
    <Landing
      enterApp={() => setInsideApp(true)}
      onSignIn={() => { window.location.href = "/login"; }}
    />
  );
}
