import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// Plain Vite config. The Figma Make preview plugins were dropped on import:
// this folder is now maintained in GitHub, not re-exported from Make.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5173 },
});
