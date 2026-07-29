"use client";

import AiraApp from "./ui/aira-app";
import Landing from "./ui/landing";
import { useInApp } from "./ui/use-hash";

export default function Home() {
  // Whether we're in the app is part of the URL (#/app/...), so the app is
  // linkable and survives a reload instead of living in component state.
  const [insideApp, setInsideApp] = useInApp();
  return insideApp
    ? <AiraApp onExit={() => setInsideApp(false)} />
    : <Landing enterApp={() => setInsideApp(true)} />;
}
