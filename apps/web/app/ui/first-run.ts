// What a first-time visitor sees, and in what order.
//
// Pulled out of aira-app.tsx as a plain function because the ORDER is the whole
// point and the order is what silently regresses. Inside the render body it was
// three `if`s among a dozen others, verifiable only by loading the app and
// looking — and the failure it guards against (the chat asking about someone's
// pregnancy before anything has told them what Aira is or offered them an
// account) looks completely normal on the screen where it happens.
//
// Mirrors the Android AppStage sequence deliberately:
//   Starting -> Tutorial -> Welcome -> Auth -> Onboarding -> Main
// which is what the two clients must agree on.

export type FirstRunStep =
  /** Session still resolving. Deciding now would show a returning user the
   *  tutorial for as long as the request takes. Android holds its splash for
   *  exactly this window. */
  | "loading"
  | "error"
  | "tutorial"
  /** Create an account, sign in, or carry on anonymously. */
  | "start-choice"
  /** The chat-led questions — journey, weeks, language, priorities. */
  | "onboarding"
  /** Setup is done; the app proper. */
  | "app";

export function firstRunStep(s: {
  loading: boolean;
  bootError: boolean;
  onboarded: boolean;
  tutorialSeen: boolean;
  startChosen: boolean;
}): FirstRunStep {
  if (s.loading) return "loading";
  if (s.bootError) return "error";
  // Onboarded outranks everything below: a returning user is never shown the
  // introduction again, and never asked how they would like to start.
  if (s.onboarded) return "app";
  if (!s.tutorialSeen) return "tutorial";
  if (!s.startChosen) return "start-choice";
  return "onboarding";
}
