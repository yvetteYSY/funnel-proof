import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";

export default defineConfig({
  root: fileURLToPath(new URL(".", import.meta.url)),
  server: {
    port: 5173,
    proxy: {
      "/fp": "http://127.0.0.1:8080"
    }
  }
});
