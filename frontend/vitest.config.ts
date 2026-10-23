import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    pool: "vmThreads",
    setupFiles: ["./vitest.setup.ts"],
    globals: true,
    include: ["__tests__/**/*.test.{ts,tsx}"],
    projects: [
      {
        test: {
          name: "dom",
          pool: "vmThreads",
          include: ["__tests__/**/*.test.{ts,tsx}", "!__tests__/lib/api.test.ts"],
        },
      },
      {
        // The 401 test redefines window.location, which is a non-configurable
        // accessor in the browser-faithful vmThreads context, so it needs the
        // permissive forks pool where jsdom globals stay configurable.
        // extends: false keeps this project's include disjoint from the root
        // include (inherited projects concatenate includes).
        extends: false,
        plugins: [react()],
        resolve: {
          alias: {
            "@": path.resolve(__dirname, "."),
          },
        },
        test: {
          name: "location-mocks",
          pool: "forks",
          environment: "jsdom",
          globals: true,
          setupFiles: ["./vitest.setup.ts"],
          include: ["__tests__/lib/api.test.ts"],
        },
      },
    ],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "."),
    },
  },
});
