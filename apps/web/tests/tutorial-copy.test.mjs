// The first-run tutorial makes three promises. This pins the two that are
// claims about what Aira IS, rather than copy.
//
// The third card is the one that matters: "Aira doesn't diagnose, prescribe, or
// replace your care team." The second is a privacy claim, which is the same
// problem one step down: "your health data is never used for advertising" is
// either true of the product or it is a lie printed on the first screen.
//
// This used to compare web against the Android client's Compose list, so a
// sentence softened on one platform and not the other failed the build. The
// native clients are gone; the promises are not. So the sentences are pinned
// HERE, verbatim, and the check survives having one client — or three.
//
// When the React Native client lands, the honest shape is to export these three
// cards from a single shared module both clients import, and reduce this file to
// asserting the module's content. Two hand-kept copies is what this test was
// written to police, and it can only police copies it can see.

import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";

const TSX = new URL("../app/ui/tutorial.tsx", import.meta.url);

const STR = String.raw`"(?:[^"\\]|\\.)*"`;

/** Long copy is written as adjacent quoted strings joined by `+`. */
function joined(block, field) {
  const m = new RegExp(`${field}\\s*[:=]\\s*\\n?\\s*((?:${STR}\\s*\\+?\\s*)+)`).exec(block);
  if (!m) return null;
  return [...m[1].matchAll(new RegExp(STR, "g"))]
    .map((s) => s[0].slice(1, -1))
    .join("");
}

function webCards() {
  const src = readFileSync(TSX, "utf8");
  return [...src.matchAll(/\{\s*\n\s*eyebrow:([\s\S]*?)\n {2}\},/g)].map((m) => m[1]);
}

test("the tutorial still defines three cards", () => {
  // Asserted before anything is compared: if a refactor breaks the parser it
  // finds zero cards, and a loop over zero cards passes every comparison below
  // while checking nothing at all.
  assert.equal(webCards().length, 3, "could not parse the web cards");
});

// Exact sentences, not keywords. A keyword check passes on "Aira doesn't
// usually diagnose", which is the specific way this copy would decay.
const PROMISES = {
  2: "Aira remembers only what helps, you can read or delete any of it, and you " +
     "can export everything at any time. Your health data is never used for " +
     "advertising — that one isn't a setting you have to find.",
  3: "Aira doesn't diagnose, prescribe, or replace your care team. Every message " +
     "is screened first, and anything urgent goes straight to your care team " +
     "rather than to another AI answer.",
};

test("the tutorial's promises are not softened", () => {
  const cards = webCards();

  for (const [n, expected] of Object.entries(PROMISES)) {
    const body = joined(cards[Number(n) - 1], "body");
    assert.ok(body, `card ${n}: no body found in tutorial.tsx`);
    assert.equal(
      body, expected,
      `card ${n}'s promise changed.\n` +
      `  expected: ${expected}\n` +
      `    actual: ${body}\n` +
      "This is a claim about what Aira is, not copy. If the product changed, " +
      "change this test deliberately; if it didn't, put the sentence back.",
    );
  }
});
