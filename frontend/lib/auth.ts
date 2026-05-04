const AUTH_DISABLED =
  process.env.NEXT_PUBLIC_AUTH_DISABLED === "true";

const AUTH_PROVIDER =
  process.env.NEXT_PUBLIC_AUTH_PROVIDER ?? "keycloak";

const AUTH_BASE_URL =
  process.env.NEXT_PUBLIC_AUTH_BASE_URL ?? "http://localhost:8080";

const AUTH_CLIENT_ID =
  process.env.NEXT_PUBLIC_AUTH_CLIENT_ID ?? "tickonomics-dashboard";

interface PkceState {
  codeVerifier: string;
  state: string;
}

function generateRandomString(length: number): string {
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return Array.from(array, (b) => b.toString(16).padStart(2, "0")).join("");
}

async function sha256(plain: string): Promise<ArrayBuffer> {
  const encoder = new TextEncoder();
  const data = encoder.encode(plain);
  return crypto.subtle.digest("SHA-256", data);
}

function base64UrlEncode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function generatePkceChallenge(): Promise<PkceState> {
  const codeVerifier = generateRandomString(32);
  const state = generateRandomString(16);
  return { codeVerifier, state };
}

export async function buildAuthorizationUrl(
  pkce: PkceState,
): Promise<string> {
  const hash = await sha256(pkce.codeVerifier);
  const codeChallenge = base64UrlEncode(hash);

  const params = new URLSearchParams({
    response_type: "code",
    client_id: AUTH_CLIENT_ID,
    redirect_uri: `${window.location.origin}/api/auth/callback`,
    state: pkce.state,
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
    scope: "openid profile email",
  });

  return `${AUTH_BASE_URL}/oauth2/authorization/${AUTH_PROVIDER}?${params}`;
}

export async function exchangeCodeForToken(
  code: string,
  codeVerifier: string,
): Promise<string> {
  const response = await fetch(
    `${AUTH_BASE_URL}/oauth2/token`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code,
        client_id: AUTH_CLIENT_ID,
        redirect_uri: `${window.location.origin}/api/auth/callback`,
        code_verifier: codeVerifier,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(`Token exchange failed: ${response.status}`);
  }

  const data = await response.json();
  return data.access_token;
}

export function isAuthDisabled(): boolean {
  return AUTH_DISABLED;
}

export function getLoginUrl(): string {
  return `${AUTH_BASE_URL}/oauth2/authorization/${AUTH_PROVIDER}`;
}

export function logout(): void {
  document.cookie =
    "auth_token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT";
  window.location.href = "/login";
}

export function getPkceStorageKey(state: string): string {
  return `pkce_${state}`;
}

export function savePkceState(pkce: PkceState): void {
  sessionStorage.setItem(
    getPkceStorageKey(pkce.state),
    JSON.stringify(pkce),
  );
}

export function loadPkceState(state: string): PkceState | null {
  const stored = sessionStorage.getItem(getPkceStorageKey(state));
  if (!stored) return null;
  try {
    return JSON.parse(stored) as PkceState;
  } catch {
    return null;
  }
}

export function clearPkceState(state: string): void {
  sessionStorage.removeItem(getPkceStorageKey(state));
}
