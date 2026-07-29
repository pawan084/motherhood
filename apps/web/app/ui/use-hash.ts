"use client";

// URL state for the app shell.
//
// A web app needs addressable screens: refresh should keep you where you were,
// the back button should go back a screen, and a link to Care should open Care.
// The old build kept the active screen in `useState`, so none of that worked —
// and neither does the HTML reference, which is a single stateful page.
//
// `useSyncExternalStore` is the SSR-safe way to read `location.hash`: it gives a
// server snapshot for the initial render and subscribes for back/forward, with
// no setState-in-an-effect.
//
// Landing anchors (#how, #safety, #privacy) already use the hash, so app routes
// are namespaced under `#/app/` and cannot collide with them.

import { useCallback, useSyncExternalStore } from "react";
import type { Screen } from "./types";

const SCREENS: Screen[] = ["Today", "Aira", "Journey", "Care", "Updates", "You"];
const PREFIX = "#/app/";

function subscribe(onChange: () => void) {
  window.addEventListener("hashchange", onChange);
  return () => window.removeEventListener("hashchange", onChange);
}

function currentHash(): string {
  return typeof window === "undefined" ? "" : window.location.hash;
}

/** True when the URL points at the application rather than the marketing site. */
export function useInApp(): [boolean, (v: boolean) => void] {
  const inApp = useSyncExternalStore(
    subscribe,
    () => currentHash().startsWith("#/app"),
    () => false,
  );
  const setInApp = useCallback((v: boolean) => {
    if (v) window.location.hash = "/app/today";
    // Leaving the app clears the route without pushing an empty entry, so one
    // Back press from the landing page goes wherever the user actually came from.
    else history.replaceState(null, "", window.location.pathname + window.location.search);
  }, []);
  return [inApp, setInApp];
}

/** The active screen, read from and written to the URL. */
export function useScreen(): [Screen, (s: Screen) => void] {
  const screen = useSyncExternalStore(
    subscribe,
    () => {
      const h = currentHash();
      if (!h.startsWith(PREFIX)) return "Today" as Screen;
      const slug = h.slice(PREFIX.length).toLowerCase();
      return SCREENS.find((s) => s.toLowerCase() === slug) ?? ("Today" as Screen);
    },
    () => "Today" as Screen,
  );
  const setScreen = useCallback((s: Screen) => {
    window.location.hash = `/app/${s.toLowerCase()}`;
  }, []);
  return [screen, setScreen];
}
