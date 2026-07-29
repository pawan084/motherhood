// The two clients must introduce Aira with the same words.
//
// The third card is the one that matters: "Aira doesn't diagnose, prescribe, or
// replace your care team." If that sentence is softened on one platform and not
// the other, the app makes two different promises about what it is, and the
// weaker one is the one someone will rely on. The first two cards are privacy
// claims, which is the same problem one step down.
//
// Nothing generates these — the Kotlin is a Compose list and the TypeScript is
// a React one — so this compares the strings directly and fails naming the card
// that drifted. Cheap insurance for text that is only ever read by a person on
// their first run, where nobody would notice it had changed on one side.

import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";

const KT = new URL(
  "../../android/app/src/main/java/com/aira/companion/ui/screens/TutorialScreen.kt",
  import.meta.url,
);
const TSX = new URL("../app/ui/tutorial.tsx", import.meta.url);

const STR = String.raw`"(?:[^"\\]|\\.)*"`;

/** Kotlin and TS both write long copy as adjacent quoted strings joined by `+`. */
function joined(block, field) {
  const m = new RegExp(`${field}\\s*[:=]\\s*\\n?\\s*((?:${STR}\\s*\\+?\\s*)+)`).exec(block);
  if (!m) return null;
  return [...m[1].matchAll(new RegExp(STR, "g"))]
    .map((s) => s[0].slice(1, -1))
    .join("");
}

function androidCards() {
  const src = readFileSync(KT, "utf8");
  return [...src.matchAll(/TutorialCard\(([\s\S]*?)\n {4}\),/g)].map((m) => m[1]);
}

function webCards() {
  const src = readFileSync(TSX, "utf8");
  return [...src.matchAll(/\{\s*\n\s*eyebrow:([\s\S]*?)\n {2}\},/g)].map((m) => m[1]);
}

test("both clients define the same three cards", () => {
  // Asserted before anything is compared: if a refactor breaks either parser it
  // finds zero cards, and a loop over zero cards passes every comparison below
  // while checking nothing at all.
  assert.equal(androidCards().length, 3, "could not parse the Android cards");
  assert.equal(webCards().length, 3, "could not parse the web cards");
});

test("the tutorial says the same thing on Android and on the web", () => {
  const kt = androidCards();
  const web = webCards();

  for (let i = 0; i < 3; i++) {
    for (const field of ["title", "body"]) {
      const a = joined(kt[i], field);
      const w = joined(web[i], field);
      assert.ok(a, `card ${i + 1}: no ${field} found in TutorialScreen.kt`);
      assert.ok(w, `card ${i + 1}: no ${field} found in tutorial.tsx`);
      assert.equal(
        w, a,
        `card ${i + 1} ${field} differs between the clients.\n` +
        `  android: ${a}\n      web: ${w}\n` +
        `Change both, or neither.`,
      );
    }
  }
});
