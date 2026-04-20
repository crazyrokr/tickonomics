# Milestone 1: Scaffolding, Layout, Auth, API Client

**Status:** DONE
**Depends on:** Nothing (foundation milestone)
**Estimated scope:** ~15 files

## Objective

Bootstrap the `frontend/` Next.js project with the full developer infrastructure: TypeScript, Tailwind CSS v4, ESLint, Vitest, and the application shell (layout, navigation, auth, API client, WebSocket).

## Components

### 1.1 Next.js Project Scaffolding

- Create `frontend/` directory with `create-next-app` (App Router, TypeScript, Tailwind CSS, ESLint)
- Align Next.js version with `landing/` (currently Next.js 16.x)
- Tailwind CSS v4 via `@tailwindcss/postcss` (matching `landing/` pattern, no `tailwind.config.ts`)
- `tsconfig.json` with strict mode, path alias `@/*` → `./*`
- `postcss.config.mjs` with `@tailwindcss/postcss` plugin
- `eslint.config.mjs` with flat config (ESLint 9, matching `landing/`)
- `vitest.config.ts` + `vitest.setup.ts` (jsdom, React plugin, testing-library matchers)
- `globals.css` with Tailwind v4 `@import "tailwindcss"` and `@theme inline` block

**Target directory:**

```
frontend/
├── app/
│   ├── layout.tsx
│   ├── page.tsx
│   ├── login/
│   │   └── page.tsx
│   └── globals.css
├── package.json
├── next.config.ts
├── tsconfig.json
├── postcss.config.mjs
├── eslint.config.mjs
├── vitest.config.ts
├── vitest.setup.ts
└── Dockerfile
```

### 1.2 Layout Components

- `Header.tsx` — top bar with logo, connection status indicator, auth controls
- `Sidebar.tsx` — collapsible navigation with sections: Charts, KPIs, Monitoring, Config
- `ConnectionStatus.tsx` — WebSocket connection state indicator (connected/connecting/disconnected)

### 1.3 Authentication Module

- `lib/auth.ts` — OAuth2 + PKCE client implementation
- `app/login/page.tsx` — Login page with redirect to IdP (Auth0/Keycloak)
- Token management via httpOnly cookies (no localStorage)
- Optional auth bypass for single-user local deployment (`NEXT_PUBLIC_AUTH_DISABLED=true`)

### 1.4 API Client

- `lib/api.ts` — Typed HTTP client with JWT injection, base URL from `NEXT_PUBLIC_API_BASE_URL`
- `types/api.ts` — TypeScript interfaces generated from `api-contracts/openapi.yaml`
- Error handling: 401 → redirect to login, 403 → forbidden page, network → retry with backoff

### 1.5 WebSocket Infrastructure

- `lib/websocket.ts` — WebSocket manager with auto-reconnect (exponential backoff: 1s, 2s, 4s, 8s, max 30s)
- `hooks/useWebSocket.ts` — React hook wrapping WebSocket manager
- JWT token validation on WebSocket handshake
- Connection state exposed via `ConnectionStatus.tsx`

## Data Sources

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/oauth2/authorization/{provider}` | GET | OAuth2 login redirect |
| `/oauth2/callback` | GET | OAuth2 callback |
| `/ws/prices` | WS | Real-time price updates |
| `/ws/signals` | WS | Real-time signal notifications |

## Acceptance Criteria

- [ ] `npm run dev` starts the dev server without errors
- [ ] `npm run build` succeeds with zero TypeScript errors
- [ ] `npm run lint` passes
- [ ] Layout renders Header, Sidebar, and ConnectionStatus
- [ ] Login page redirects to IdP when auth is enabled
- [ ] Auth bypass works when `NEXT_PUBLIC_AUTH_DISABLED=true`
- [ ] API client sends JWT in Authorization header when available
- [ ] WebSocket connects and reconnects on disconnect
- [ ] Connection status reflects WebSocket state (connected/connecting/disconnected)
- [ ] Dockerfile produces a working production build
- [ ] Unit tests for auth module, API client, and WebSocket hook pass

## Key Decisions

1. **`frontend/` vs `dashboard/`**: Use `frontend/` as specified in the plan. The `landing/` directory is a separate static-export site for a different purpose.
2. **TanStack Query vs SWR**: Plan specifies TanStack Query. Landing uses SWR. Dashboard uses TanStack Query per the plan — richer cache invalidation and WebSocket integration.
3. **Next.js version**: Align with whatever version `landing/` uses at implementation time to keep dependency knowledge shared.
4. **Auth strategy**: Configurable bypass for local development. Production requires valid JWT.
