import {
  apiFetch,
  createSafeText,
  formatDateTime,
  formatRelativeTime,
  getUserId,
  recordEvent
} from "./client-api.js";

const refreshBtn = document.getElementById("me-refresh");
const messageEl = document.getElementById("me-message");
const kpiEl = document.getElementById("me-kpis");
const timelineEl = document.getElementById("me-timeline");
const recommendationsEl = document.getElementById("me-recommendations");
const recommendationTpl = document.getElementById("me-reco-template");

if (refreshBtn && kpiEl && timelineEl && recommendationsEl && recommendationTpl) {
  refreshBtn.addEventListener("click", loadMePage);
  loadMePage();
}

async function loadMePage() {
  showMessage("");
  renderLoading();

  try {
    const userId = getUserId();
    const [events, recommendations, articles] = await Promise.all([
      apiFetch(`/api/users/${encodeURIComponent(userId)}/events?limit=30`),
      apiFetch(`/api/users/${encodeURIComponent(userId)}/recommendations?limit=6`),
      apiFetch("/api/articles")
    ]);

    const eventList = Array.isArray(events) ? events : [];
    const recoList = Array.isArray(recommendations) ? recommendations : [];
    const articleList = Array.isArray(articles) ? articles : [];
    const articleMap = new Map(articleList.map((item) => [item.id, item]));

    renderKpis(eventList);
    renderTimeline(eventList, articleMap);
    renderRecommendations(recoList);
  } catch (err) {
    showMessage(`Failed to load your feed: ${err.message}`, "error");
    renderEmpty();
  }
}

function renderKpis(events) {
  const byType = events.reduce(
    (acc, event) => {
      const type = event.type || "unknown";
      acc[type] = (acc[type] || 0) + 1;
      return acc;
    },
    {}
  );

  const cards = [
    { label: "All Events", value: events.length },
    { label: "Clicks", value: byType.click || 0 },
    { label: "Likes", value: byType.like || 0 },
    { label: "Shares", value: byType.share || 0 }
  ];

  kpiEl.replaceChildren();
  cards.forEach((card) => {
    const node = document.createElement("article");
    node.className = "kpi";
    const label = document.createElement("p");
    label.textContent = card.label;
    const value = document.createElement("strong");
    value.textContent = String(card.value);
    node.append(label, value);
    kpiEl.appendChild(node);
  });
}

function renderTimeline(events, articleMap) {
  timelineEl.replaceChildren();
  if (!events.length) {
    timelineEl.appendChild(createMuted("No activity yet. Open a few stories from Home."));
    return;
  }

  events.slice(0, 14).forEach((event) => {
    const article = articleMap.get(event.itemId);
    const title = createSafeText(article?.title, event.itemId || "Unknown article");
    const item = document.createElement("article");
    item.className = "event-item";
    const time = document.createElement("p");
    time.className = "time";
    time.textContent = formatDateTime(event.ts);

    const headline = document.createElement("strong");
    headline.textContent = title;

    const action = document.createElement("span");
    action.textContent = `Action: ${event.type || "unknown"}`;

    item.append(time, headline, action);
    timelineEl.appendChild(item);
  });
}

function renderRecommendations(items) {
  recommendationsEl.replaceChildren();
  if (!items.length) {
    recommendationsEl.appendChild(createMuted("No recommendations right now."));
    return;
  }

  items.forEach((item) => {
    const articleId = createSafeText(item.id);
    const card = recommendationTpl.content.firstElementChild.cloneNode(true);
    const img = card.querySelector(".cover");
    const meta = card.querySelector(".meta");
    const title = card.querySelector(".title");

    card.href = toArticleHref(articleId);
    img.src = createSafeText(item.imageUrl, `https://picsum.photos/seed/${articleId || "me"}/560/360`);
    img.alt = createSafeText(item.title, "recommendation");
    meta.textContent = `${createSafeText(item.source, "News")} | ${formatRelativeTime(item.publishedAt)}`;
    title.textContent = createSafeText(item.title, "Untitled");

    card.addEventListener("click", () => trackClick(articleId));

    recommendationsEl.appendChild(card);
  });
}

function renderLoading() {
  kpiEl.replaceChildren();
  timelineEl.replaceChildren();
  recommendationsEl.replaceChildren();
  kpiEl.appendChild(createMuted("Loading..."));
  timelineEl.appendChild(createMuted("Loading..."));
  recommendationsEl.appendChild(createMuted("Loading..."));
}

function renderEmpty() {
  kpiEl.replaceChildren();
  timelineEl.replaceChildren();
  recommendationsEl.replaceChildren();
  kpiEl.appendChild(createMuted("No data yet."));
  timelineEl.appendChild(createMuted("No data yet."));
  recommendationsEl.appendChild(createMuted("No data yet."));
}

function createMuted(text) {
  const node = document.createElement("p");
  node.className = "muted";
  node.textContent = text;
  return node;
}

function showMessage(text, type = "ok") {
  if (!messageEl) {
    return;
  }
  if (!text) {
    messageEl.hidden = true;
    messageEl.className = "message";
    messageEl.textContent = "";
    return;
  }
  messageEl.hidden = false;
  messageEl.className = `message ${type}`;
  messageEl.textContent = text;
}

function toArticleHref(articleId) {
  return articleId ? `/article?id=${encodeURIComponent(articleId)}` : "/";
}

function trackClick(articleId) {
  if (!articleId) {
    return;
  }
  recordEvent({ itemId: articleId, type: "click" }).catch(() => {});
}
