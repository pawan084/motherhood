import react from "@vitejs/plugin-react";
import { resolve } from "node:path";
import { defineConfig } from "vite";

// The API base is configured via VITE_API_URL (defaults to the local backend
// in src/api.ts). No proxy: the backend speaks CORS for the dev origin.
//
// Two entries: the learner app (index.html) and the admin console
// (admin.html at /admin.html). Same origin in dev; production should serve
// the admin from a separate origin/host (TODO.md).
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, "index.html"),
        admin: resolve(__dirname, "admin.html"),
      },
    },
  },
});
