import { test, expect, type Page, type Route } from "@playwright/test";

const FIXTURES: Record<string, unknown> = {
  "/api/v1/kpi/ili": {
    value: 0.742,
    status: "VALID",
    activeWeights: { rrp: 0.4, spread: 0.35, vol: 0.25 },
    excludedComponents: [],
    timestamp: "2026-06-13T10:00:00Z",
  },
  "/api/v1/kpi/ili/history": [
    "2026-05-19", "2026-05-20", "2026-05-21", "2026-05-22", "2026-05-23",
    "2026-05-24", "2026-05-25", "2026-05-26", "2026-05-27", "2026-05-28",
    "2026-05-29", "2026-05-30", "2026-05-31", "2026-06-01", "2026-06-02",
    "2026-06-03", "2026-06-04", "2026-06-05", "2026-06-06", "2026-06-07",
  ].map((timestamp, i) => ({ timestamp, value: 0.6 + i * 0.01, status: "VALID" })),
  "/api/v1/kpi/liquidity-stress": {
    value: 0.31,
    trend: "STABLE",
    timestamp: "2026-06-13T10:00:00Z",
  },
  "/api/v1/kpi/repo-equity-beta": [
    { symbol: "SPY", beta: 1.21, lastUpdate: "2026-06-13T10:00:00Z" },
    { symbol: "QQQ", beta: 1.45, lastUpdate: "2026-06-13T10:00:00Z" },
  ],
  "/api/v1/kpi/rrp-drain": {
    velocity: -1.5e9,
    dayOverDayChange: -2e8,
    trend: "DECELERATING",
    history: [],
  },
  "/api/v1/kpi/volatility-regime": {
    regime: "NORMAL",
    upperBand: 4520,
    lowerBand: 4400,
    currentPrice: 4461,
    timestamp: "2026-06-13T10:00:00Z",
  },
  "/api/v1/kpi/correlation-matrix": [
    { source: "RRP", target: "SPY", correlation: -0.42, pValue: 0.01, sampleSize: 252, aicLagOrder: 3 },
  ],
  "/api/v1/kpi/systemic-risk-heatmap": {
    triPartyGcfSpread: 0.12,
    sofrPctlRange: 0.05,
    tgcrBgcrSpread: 0.02,
    tgaBalanceChange: -5e9,
    timestamp: "2026-06-13T10:00:00Z",
  },
  "/api/v1/health": {
    sources: [
      { name: "FRED", healthy: true, lastSync: "2026-06-13T10:00:00Z", latencyMs: 42 },
      { name: "Yahoo Finance", healthy: true, lastSync: "2026-06-13T10:00:00Z", latencyMs: 18 },
    ],
    proxyDivergence: {
      tbillSofrCorrelation5d: 0.94,
      divergenceScore: 0.12,
      dislocated: false,
      timestamp: "2026-06-13T10:00:00Z",
    },
    timescaleDb: { connected: true, compressionStatus: "active", aggregateLagSeconds: 3 },
    circuitBreakers: { yahooFinance: "CLOSED", finhubWs: "CLOSED", analyticsWorker: "CLOSED" },
  },
  "/api/v1/config": [{ key: "ili.threshold", value: 0.5 }],
  "/api/v1/config/history": [
    {
      id: "h1",
      timestamp: "2026-06-13T09:00:00Z",
      author: "system",
      auditReason: "Scheduled recalibration",
      diff: "threshold: 0.45 -> 0.5",
    },
  ],
  "/api/v1/signals": [
    {
      id: "s1",
      timestamp: "2026-06-13T09:30:00Z",
      statusCode: "ACTIONABLE",
      direction: "LONG",
      symbol: "SPY",
      iliValue: 0.742,
    },
  ],
};

async function mockBackend(page: Page) {
  await page.route(/localhost:8080/, async (route: Route) => {
    const request = route.request();
    if (request.method() === "OPTIONS") {
      await route.fulfill({
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, PUT, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
      });
      return;
    }

    const path = new URL(request.url()).pathname;
    const body = FIXTURES[path] ?? [];
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      headers: { "Access-Control-Allow-Origin": "*" },
      body: JSON.stringify(body),
    });
  });
}

function collectAppErrors(page: Page, errors: string[]) {
  page.on("console", (msg) => {
    if (msg.type() !== "error") return;
    const text = msg.text();
    // The signals WebSocket has no backend in the e2e environment; its
    // connection failure is expected (Chromium and Firefox phrase it
    // differently) and excluded from the error gate.
    if (
      text.includes("WebSocket") ||
      text.includes("ws://") ||
      text.includes("establish a connection to the server")
    ) {
      return;
    }
    errors.push(text);
  });
  page.on("pageerror", (err) => errors.push(err.message));
}

// Wait for a client-rendered element so the app has hydrated, the mocked
// /kpi/ili fetch has resolved, and click handlers are attached.
async function pageReady(page: Page) {
  await page.goto("/");
  await expect(page.getByTestId("ili-status-badge")).toBeVisible({ timeout: 15000 });
}

test.beforeEach(async ({ page }) => {
  await mockBackend(page);
});

test.describe("Analytics dashboard", () => {
  test("renders chrome and Overview section by default with no app errors", async ({ page }) => {
    const errors: string[] = [];
    collectAppErrors(page, errors);

    await pageReady(page);
    await expect(page.locator("header")).toBeVisible();
    await expect(page.getByTestId("section-tabs")).toBeVisible();
    await expect(page.getByTestId("tab-overview")).toHaveAttribute("aria-selected", "true");
    await expect(page.getByTestId("section-overview")).toBeVisible();
    await expect(page.getByTestId("ili-status-badge")).toHaveText("VALID");

    expect(errors).toEqual([]);
  });

  test("switching to Charts mounts the Charts section and unmounts the Overview chart", async ({ page }) => {
    await pageReady(page);
    await expect(page.getByTestId("price-ili-chart")).toBeVisible();

    await page.getByTestId("tab-charts").click();
    await expect(page.getByTestId("section-charts")).toBeVisible();
    // BacktestHeatmap renders this title from its empty state (no Perspective
    // WASM dependency), a stable signal that the Charts section mounted.
    await expect(page.getByText("Time Sensitivity Heatmap")).toBeVisible();
    await expect(page.getByTestId("price-ili-chart")).toHaveCount(0);
  });

  test("switching to Config shows the configuration editor view", async ({ page }) => {
    await pageReady(page);
    await page.getByTestId("tab-config").click();
    await expect(page.getByTestId("section-config")).toBeVisible();
    await expect(page.getByTestId("config-readonly")).toBeVisible();
  });

  test("KPIs section renders the repo/equity beta table from the mocked endpoint", async ({ page }) => {
    await pageReady(page);
    await page.getByTestId("tab-kpis").click();
    await expect(page.getByTestId("section-kpis")).toBeVisible();
    await expect(page.getByTestId("beta-SPY")).toBeVisible();
  });

  test("Risk and Trading sections mount without errors", async ({ page }) => {
    const errors: string[] = [];
    collectAppErrors(page, errors);

    await pageReady(page);
    await page.getByTestId("tab-risk").click();
    await expect(page.getByTestId("section-risk")).toBeVisible();
    await page.getByTestId("tab-trading").click();
    await expect(page.getByTestId("section-trading")).toBeVisible();

    expect(errors).toEqual([]);
  });
});
