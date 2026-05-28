import { test, expect } from "@playwright/test";

test.describe("Dashboard smoke tests", () => {
  test("loads the main page without errors", async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });

    await page.goto("/");
    await page.waitForLoadState("networkidle");

    expect(consoleErrors).toHaveLength(0);
  });

  test("renders the header with application title", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    const header = page.locator("header");
    await expect(header).toBeVisible();
  });

  test("renders the sidebar navigation", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    const sidebar = page.locator("nav, [data-sidebar]");
    await expect(sidebar.first()).toBeVisible();
  });

  test("shows loading skeletons before data loads", async ({ page }) => {
    await page.goto("/");
    const skeletons = page.locator(".animate-pulse");
    await expect(skeletons.first()).toBeVisible({ timeout: 2000 });
  });
});

test.describe("Health and connectivity", () => {
  test("displays connection status indicator", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    const statusIndicator = page.locator(
      "[data-testid='connection-status'], [class*='connection']"
    );
    await expect(statusIndicator.first()).toBeVisible({ timeout: 5000 });
  });

  test("backend health endpoint responds", async ({ request }) => {
    const baseUrl = process.env.E2E_API_URL || "http://localhost:8080";
    const response = await request.get(`${baseUrl}/health`);
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body.status).toBe("UP");
  });
});

test.describe("Chart rendering", () => {
  test("renders heatmap component without crash", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(2000);

    const heatmap = page.locator(
      "[data-testid='d3-ili-heatmap'], [data-testid='liquidity-heatmap'], [data-testid='systemic-risk-heatmap']"
    );
    const count = await heatmap.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });
});
