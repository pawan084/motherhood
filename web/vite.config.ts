import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// The API base is configured via VITE_API_URL (defaults to the local backend
// in src/api.ts). No proxy: the backend speaks CORS for the dev origin.
export default defineConfig({
  plugins: [react()],
});
