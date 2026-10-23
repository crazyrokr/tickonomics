import "@testing-library/jest-dom/vitest";
import { webcrypto } from "node:crypto";

// The jsdom window crypto in vmThreads contexts lacks WebCrypto's `subtle`;
// patch it in place with node's webcrypto so app code can digest.
if (!globalThis.crypto?.subtle) {
  Object.defineProperty(globalThis.crypto, "subtle", {
    value: webcrypto.subtle,
    configurable: true,
    writable: true,
  });
}

class MockIntersectionObserver {
  readonly root: Element | null = null;
  readonly rootMargin = "";
  readonly thresholds: ReadonlyArray<number> = [];
  private callback: IntersectionObserverCallback;

  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback;
  }

  observe() {
    const el = document.createElement("div");
    const rect = el.getBoundingClientRect();
    this.callback(
      [
        {
          isIntersecting: true,
          target: el,
          boundingClientRect: rect,
          intersectionRatio: 1,
          intersectionRect: rect,
          rootBounds: rect,
          time: Date.now(),
        },
      ] as unknown as IntersectionObserverEntry[],
      this as unknown as IntersectionObserver
    );
  }

  unobserve() {}
  disconnect() {}
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}

globalThis.IntersectionObserver =
  MockIntersectionObserver as unknown as typeof IntersectionObserver;

if (typeof window.matchMedia !== "function") {
  window.matchMedia = (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}
