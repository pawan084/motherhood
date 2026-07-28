import type { Config } from "tailwindcss";

// Aira's palette — aubergine / ivory / sage / lilac, matching the apps.
const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ivory: "#f8f4ee",
        paper: "#fffdf9",
        aubergine: { DEFAULT: "#4a234b", soft: "#755176", deep: "#311733" },
        lilac: { DEFAULT: "#e9ddea", mist: "#f3ecf3" },
        sage: { DEFAULT: "#8fa58e", deep: "#49634f" },
        ink: { DEFAULT: "#211d20", muted: "#716a70" },
        line: "#e6ddd9",
        urgent: "#cc3d36",
        amber: "#ae7423",
      },
    },
  },
  plugins: [],
};
export default config;
