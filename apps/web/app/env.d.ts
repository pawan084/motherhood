// Ambient types for the Vite env vars the Aira web client reads. Self-contained
// so it type-checks without pulling in `vite/client`.
interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  readonly VITE_APP_TOKEN?: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
