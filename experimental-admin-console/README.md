# SIF Unity Broker Admin Console (Experimental)

Standalone React admin UI for the SIF Unity Broker. Talks to the gateway at `http://localhost:8080`.

## Quick Start

```bash
npm install
npm run dev
```

Open http://localhost:5173 in your browser.

## Configuration

Gateway URL is set in `.env`:

```
VITE_GATEWAY_URL=http://localhost:8080
```

## Auth

Enter your admin session token and shared secret on the login page. Credentials are held in memory only (not persisted to localStorage or cookies). The first API call validates them against the broker.

## Tech Stack

- React 18 + TypeScript
- Vite
- Tailwind CSS
- React Router
- crypto-js (HMAC-SHA256)
