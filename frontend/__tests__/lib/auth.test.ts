import { describe, it, expect, beforeEach } from "vitest";
import {
  generatePkceChallenge,
  buildAuthorizationUrl,
  isAuthDisabled,
  getLoginUrl,
  savePkceState,
  loadPkceState,
  clearPkceState,
  getPkceStorageKey,
} from "@/lib/auth";

describe("auth", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  describe("generatePkceChallenge", () => {
    it("returns codeVerifier and state strings", async () => {
      const pkce = await generatePkceChallenge();
      expect(pkce.codeVerifier).toBeDefined();
      expect(pkce.state).toBeDefined();
      expect(typeof pkce.codeVerifier).toBe("string");
      expect(typeof pkce.state).toBe("string");
    });

    it("generates unique values on each call", async () => {
      const a = await generatePkceChallenge();
      const b = await generatePkceChallenge();
      expect(a.codeVerifier).not.toBe(b.codeVerifier);
      expect(a.state).not.toBe(b.state);
    });
  });

  describe("buildAuthorizationUrl", () => {
    it("returns URL with required OAuth2 parameters", async () => {
      const pkce = { codeVerifier: "test-verifier", state: "test-state" };
      const url = await buildAuthorizationUrl(pkce);
      const parsed = new URL(url);

      expect(parsed.searchParams.get("response_type")).toBe("code");
      expect(parsed.searchParams.get("state")).toBe("test-state");
      expect(parsed.searchParams.get("code_challenge_method")).toBe("S256");
      expect(parsed.searchParams.get("code_challenge")).toBeTruthy();
    });
  });

  describe("isAuthDisabled", () => {
    it("returns boolean", () => {
      expect(typeof isAuthDisabled()).toBe("boolean");
    });
  });

  describe("getLoginUrl", () => {
    it("returns a URL string", () => {
      const url = getLoginUrl();
      expect(url).toContain("/oauth2/authorization/");
    });
  });

  describe("PKCE state storage", () => {
    it("round-trips PKCE state through sessionStorage", () => {
      const pkce = { codeVerifier: "abc123", state: "xyz789" };
      savePkceState(pkce);

      const loaded = loadPkceState(pkce.state);
      expect(loaded).toEqual(pkce);
    });

    it("returns null for unknown state", () => {
      expect(loadPkceState("nonexistent")).toBeNull();
    });

    it("clears PKCE state", () => {
      const pkce = { codeVerifier: "abc123", state: "xyz789" };
      savePkceState(pkce);
      clearPkceState(pkce.state);
      expect(loadPkceState(pkce.state)).toBeNull();
    });

    it("generates correct storage key", () => {
      expect(getPkceStorageKey("mystate")).toBe("pkce_mystate");
    });
  });
});
