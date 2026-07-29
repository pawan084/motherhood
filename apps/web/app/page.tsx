"use client";

import { useState } from "react";
import AiraApp from "./ui/aira-app";
import Landing from "./ui/landing";
import SignIn from "./ui/sign-in";
import { useInApp } from "./ui/use-hash";

export default function Home() {
  // Whether we're in the app is part of the URL (#/app/...), so the app is
  // linkable and survives a reload instead of living in component state.
  const [insideApp, setInsideApp] = useInApp();
  // Sign-in is a transient overlay, not a route: it's optional, dismissible,
  // and nothing about the app depends on having completed it.
  const [signingIn, setSigningIn] = useState(false);

  if (insideApp) return <AiraApp onExit={() => setInsideApp(false)} />;

  return (
    <>
      <Landing enterApp={() => setInsideApp(true)} onSignIn={() => setSigningIn(true)} />
      {signingIn && (
        <SignIn
          close={() => setSigningIn(false)}
          // A signed-in user goes straight in; AiraApp re-reads /account/me and
          // will skip onboarding if this account has already completed it.
          onSignedIn={() => { setSigningIn(false); setInsideApp(true); }}
        />
      )}
    </>
  );
}
