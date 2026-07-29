// The order of the first run.
//
// The client asked for the tutorial and the login choice to come before the
// chat. Getting that right once is easy; keeping it right is what this is for —
// the regression does not throw or fail to build, it just quietly asks someone
// about their pregnancy before telling them what Aira is or offering them an
// account, and it looks completely normal on the screen where it happens.
//
// This imports the same function aira-app.tsx renders from, so the component
// and the test cannot drift apart.

import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";

// The source is a .ts file with no runtime dependencies and one exported
// function, so it is compiled here rather than adding a build step for one
// test. If that ever stops being true, this fails loudly rather than silently
// testing something else.
const src = readFileSync(new URL("../app/ui/first-run.ts", import.meta.url), "utf8");
const js = src
  .replace(/export type FirstRunStep =[\s\S]*?\| "app";/, "")
  .replace(/: \{[\s\S]*?\}\): FirstRunStep/, ")")
  .replace("export function firstRunStep(s", "function firstRunStep(s");
const firstRunStep = new Function(`${js}; return firstRunStep;`)();

const FRESH = {
  loading: false, bootError: false, onboarded: false,
  tutorialSeen: false, startChosen: false,
};

test("a brand-new visitor is introduced before being asked anything", () => {
  assert.equal(firstRunStep(FRESH), "tutorial");
});

test("the login choice comes after the tutorial and before the chat", () => {
  const afterTutorial = { ...FRESH, tutorialSeen: true };
  assert.equal(firstRunStep(afterTutorial), "start-choice");

  const afterChoice = { ...afterTutorial, startChosen: true };
  assert.equal(firstRunStep(afterChoice), "onboarding");
});

test("the chat is never the first thing a new visitor sees", () => {
  // The whole point, stated as the thing that must not happen: with nothing
  // seen and nothing chosen, no combination of the other flags may land on the
  // journey questions.
  for (const onboarded of [false]) {
    for (const loading of [true, false]) {
      const step = firstRunStep({ ...FRESH, loading, onboarded });
      assert.notEqual(step, "onboarding",
        `reached onboarding with loading=${loading}`);
    }
  }
});

test("a returning user sees neither the tutorial nor the choice", () => {
  // `onboarded` comes from the server, so this holds across devices and after
  // clearing local storage — the tutorial flag being missing must not put a
  // long-standing user back through setup.
  const returning = { ...FRESH, onboarded: true };
  assert.equal(firstRunStep(returning), "app");
  assert.equal(firstRunStep({ ...returning, tutorialSeen: false }), "app");
});

test("nothing is decided until the session has resolved", () => {
  // Deciding while `me` is still in flight would show a returning user the
  // tutorial for the length of the request. Android holds its splash screen for
  // exactly this window.
  assert.equal(firstRunStep({ ...FRESH, loading: true }), "loading");
  assert.equal(firstRunStep({ ...FRESH, loading: true, onboarded: true }), "loading");
});

test("a backend that cannot be reached is not mistaken for a new user", () => {
  // The app refuses to run on placeholder content, so an unreachable backend
  // must surface as an error rather than as a first run that would collect
  // answers with nowhere to send them.
  assert.equal(firstRunStep({ ...FRESH, bootError: true }), "error");
});
