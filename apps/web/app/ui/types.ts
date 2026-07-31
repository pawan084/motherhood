// Shared vocabulary for the web application shell.

export type Screen = "Today" | "Aira" | "Journey" | "Learn" | "Care" | "Updates" | "You";

// The in-conversation tools. `tool` strings also arrive from the backend on a
// chat action_card, so unknown values must degrade rather than crash — see
// `isToolName` below.
export type ToolName =
  | "checkin"
  | "reminder"
  | "medicine"
  | "appointment"
  | "upload"
  | "wellness"
  | "symptom"
  | "memory"
  | "privacy"
  | "careplan"
  | "support"
  | "emergency"
  | "partner";

const TOOL_NAMES: ToolName[] = [
  "checkin", "reminder", "medicine", "appointment", "upload", "wellness",
  "symptom", "memory", "privacy", "careplan", "support", "emergency", "partner",
];

/** The backend's action_card.tool is a free string from the model's JSON, so a
 *  new or hallucinated value must not open a broken sheet. */
export function isToolName(v: unknown): v is ToolName {
  return typeof v === "string" && (TOOL_NAMES as string[]).includes(v);
}

export const JOURNEY_LABEL: Record<string, string> = {
  trying: "Trying to conceive",
  pregnant: "Pregnant",
  postpartum: "Postpartum",
  loss: "After a loss",
  exploring: "Exploring",
};

export function formatDate(ts: number | null | undefined): string {
  if (!ts) return "—";
  return new Date(ts * 1000).toLocaleDateString(undefined, {
    day: "numeric", month: "short", year: "numeric",
  });
}

export function greeting(): string {
  const h = new Date().getHours();
  if (h < 12) return "Good morning";
  if (h < 18) return "Good afternoon";
  return "Good evening";
}
