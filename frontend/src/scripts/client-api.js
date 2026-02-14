const API_BASE_KEY = "reco.apiBase";
const USER_ID_KEY = "reco.userId";

export function getApiBase() {
  if (typeof window !== "undefined") {
    const configured = normalize(window.__RECO_API_BASE__);
    if (configured) {
      return configured;
    }
    const host = window.location.hostname;
    const port = window.location.port;
    // Astro dev server runs on 4321. Backend stays on 8080.
    if (port === "4321") {
      return `${window.location.protocol}//${host}:8080`;
    }
  }
  const saved = safeStorageGet(API_BASE_KEY);
  if (saved) {
    return normalize(saved);
  }
  return "";
}

export function getUserId() {
  const saved = safeStorageGet(USER_ID_KEY);
  if (saved) {
    return saved;
  }
  const generated = `u-${randomToken(8)}`;
  safeStorageSet(USER_ID_KEY, generated);
  return generated;
}

export async function apiFetch(path, options = {}) {
  const base = getApiBase().replace(/\/+$/, "");
  const url = base ? `${base}${path}` : path;
  const response = await fetch(url, options);
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const payload = await response.json();
      if (payload && payload.error) {
        message = payload.error;
      }
    } catch (_err) {
      // ignore non-json response body
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

export async function recordEvent({ userId = getUserId(), itemId, type }) {
  return apiFetch("/api/events", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId,
      itemId,
      type,
      ts: Date.now()
    })
  });
}

export function formatRelativeTime(ts) {
  if (!ts) {
    return "just now";
  }
  const value = Number(ts);
  if (Number.isNaN(value)) {
    return "just now";
  }
  const deltaHours = Math.round((Date.now() - value) / 3600000);
  if (deltaHours < 1) {
    return "just now";
  }
  if (deltaHours < 24) {
    return `${deltaHours}h ago`;
  }
  const days = Math.round(deltaHours / 24);
  return `${days}d ago`;
}

export function formatDateTime(ts) {
  if (!ts) {
    return "-";
  }
  const value = Number(ts);
  if (Number.isNaN(value)) {
    return "-";
  }
  const date = new Date(value);
  return date.toLocaleString();
}

export function createSafeText(text, fallback = "") {
  const normalized = normalize(text);
  return normalized || fallback;
}

export function normalize(value) {
  return value == null ? "" : String(value).trim();
}

function randomToken(size) {
  const alphabet = "23456789abcdefghijkmnpqrstuvwxyz";
  if (typeof crypto !== "undefined" && crypto.getRandomValues) {
    const arr = new Uint8Array(size);
    crypto.getRandomValues(arr);
    return Array.from(arr, (v) => alphabet[v % alphabet.length]).join("");
  }
  return Math.random().toString(36).slice(2, 2 + size);
}

function safeStorageGet(key) {
  try {
    return window.localStorage.getItem(key);
  } catch (_err) {
    return null;
  }
}

function safeStorageSet(key, value) {
  try {
    window.localStorage.setItem(key, value);
  } catch (_err) {
    // ignore in restricted modes
  }
}
