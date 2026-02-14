import {
  apiFetch,
  createSafeText,
  formatRelativeTime,
  getUserId,
  recordEvent
} from "./client-api.js";

const TOPIC_HINTS = {
  politics: ["politic", "government", "election", "senate", "congress", "policy", "white house"],
  business: ["business", "market", "economy", "finance", "stock", "trade", "company"],
  technology: ["technology", "tech", "ai", "software", "startup", "chip", "internet", "app"],
  sports: ["sport", "game", "league", "player", "coach", "team", "match", "nfl", "nba", "mlb"],
  culture: ["culture", "music", "movie", "film", "art", "festival", "book", "media"],
  lifestyle: ["lifestyle", "health", "travel", "food", "fashion", "wellness", "home"]
};

const headlineEl = document.getElementById("home-headline");
const trendingEl = document.getElementById("home-trending");
const feedEl = document.getElementById("home-feed");
const refreshBtn = document.getElementById("home-refresh");
const cardTpl = document.getElementById("home-feed-card-template");
const briefDateEl = document.getElementById("home-brief-date");
const briefListEl = document.getElementById("home-brief-list");
const channelLinks = [...document.querySelectorAll(".channel-link[data-topic]")];

const activeTopic = readTopicFromUrl();
const searchText = readQueryFromUrl();

let recommendedArticles = [];
let listedArticles = [];

if (headlineEl && trendingEl && feedEl && refreshBtn && cardTpl) {
  refreshBtn.addEventListener("click", loadHome);
  bindChannelLinks();
  updateChannelHighlight();
  loadHome();
}

function bindChannelLinks() {
  channelLinks.forEach((link) => {
    const topic = createSafeText(link.dataset.topic, "top").toLowerCase();
    link.href = buildHomeUrl(topic, searchText);
    link.addEventListener("click", (event) => {
      if (
        event.defaultPrevented ||
        event.button !== 0 ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey
      ) {
        return;
      }
      event.preventDefault();
      const next = buildHomeUrl(topic, searchText);
      const current = `${window.location.pathname}${window.location.search}`;
      if (current === next) {
        loadHome();
        return;
      }
      window.location.assign(next);
    });
  });
}

async function loadHome() {
  renderLoading();

  try {
    const userId = getUserId();
    const topicQuery = activeTopic && activeTopic !== "top" ? `&topic=${encodeURIComponent(activeTopic)}` : "";
    const searchQuery = searchText ? `&q=${encodeURIComponent(searchText)}` : "";

    const [recommendations, articles] = await Promise.all([
      apiFetch(`/api/users/${encodeURIComponent(userId)}/recommendations?limit=36${topicQuery}${searchQuery}`),
      apiFetch(`/api/articles?limit=80${topicQuery}${searchQuery}`)
    ]);

    const serverRecs = Array.isArray(recommendations) ? recommendations : [];
    const serverArticles = Array.isArray(articles) ? articles : [];

    // Backend already ranks by topic/query. This local filter is a safety net for sparse data.
    recommendedArticles = filterArticles(serverRecs, activeTopic, searchText);
    listedArticles = filterArticles(serverArticles, activeTopic, searchText);

    renderFromData();
  } catch (_err) {
    renderFallback(searchText ? `No results for "${searchText}".` : "Unable to refresh stories right now.");
  }
}

function renderFromData() {
  const uniqueRecs = uniqueById(recommendedArticles);
  const uniqueList = uniqueById(listedArticles);
  const headline = uniqueRecs[0] || uniqueList[0];
  const headlineId = createSafeText(headline?.id);

  const trendSource = uniqueList.filter((item) => createSafeText(item.id) !== headlineId).slice(0, 12);
  const feedSource = uniqueById(uniqueRecs.concat(uniqueList))
    .filter((item) => createSafeText(item.id) && createSafeText(item.id) !== headlineId)
    .slice(0, 30);

  renderBrief(uniqueList, headlineId);
  renderHeadline(headline);
  renderTrending(trendSource);
  renderFeed(feedSource);
}

function renderBrief(items, excludeId) {
  if (!briefDateEl || !briefListEl) {
    return;
  }

  briefDateEl.textContent = new Date().toLocaleDateString("en-US", {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric"
  });

  briefListEl.replaceChildren();
  const bulletItems = items
    .filter((item) => createSafeText(item.id) !== excludeId)
    .slice(0, 5);

  if (!bulletItems.length) {
    const li = document.createElement("li");
    li.textContent = "No key updates at the moment.";
    briefListEl.appendChild(li);
    return;
  }

  bulletItems.forEach((item) => {
    const li = document.createElement("li");
    li.textContent = createSafeText(item.title, "Untitled");
    briefListEl.appendChild(li);
  });
}

function renderHeadline(item) {
  headlineEl.replaceChildren();
  if (!item) {
    headlineEl.textContent = "No headline available right now.";
    headlineEl.className = "headline-loading";
    return;
  }

  const articleId = createSafeText(item.id);
  const link = document.createElement("a");
  link.className = "headline";
  link.href = toArticleHref(articleId);

  const image = document.createElement("img");
  image.src = createSafeText(item.imageUrl, `https://picsum.photos/seed/${articleId || "headline"}/1280/820`);
  image.alt = createSafeText(item.title, "headline");

  const body = document.createElement("div");
  body.className = "headline-content";

  const meta = document.createElement("p");
  meta.textContent = `${createSafeText(item.source, "Recommended")} | ${formatRelativeTime(item.publishedAt)}`;

  const title = document.createElement("h2");
  title.textContent = createSafeText(item.title, "Top Headline");

  const summary = document.createElement("span");
  summary.textContent = createSafeText(item.summary, "No summary available.");

  body.append(meta, title, summary);
  link.append(image, body);
  link.addEventListener("click", () => trackClick(articleId));

  headlineEl.className = "";
  headlineEl.appendChild(link);
}

function renderTrending(items) {
  trendingEl.replaceChildren();
  if (!items.length) {
    const empty = document.createElement("li");
    empty.className = "muted";
    empty.textContent = "No ranking data yet.";
    trendingEl.appendChild(empty);
    return;
  }

  items.forEach((item) => {
    const articleId = createSafeText(item.id);
    const li = document.createElement("li");
    const link = document.createElement("a");
    link.href = toArticleHref(articleId);

    const meta = document.createElement("p");
    meta.textContent = `${createSafeText(item.source, "News")} | ${formatRelativeTime(item.publishedAt)}`;

    const title = document.createElement("strong");
    title.textContent = createSafeText(item.title, "Untitled");

    link.append(meta, title);
    link.addEventListener("click", () => trackClick(articleId));
    li.appendChild(link);
    trendingEl.appendChild(li);
  });
}

function renderFeed(items) {
  feedEl.replaceChildren();
  if (!items.length) {
    renderFallback(searchText ? `No results for "${searchText}".` : "No stories available right now.");
    return;
  }

  items.forEach((item) => {
    const articleId = createSafeText(item.id);
    const card = cardTpl.content.firstElementChild.cloneNode(true);
    const coverLink = card.querySelector(".cover-link");
    const image = card.querySelector(".cover");
    const meta = card.querySelector(".meta");
    const title = card.querySelector(".title");
    const summary = card.querySelector(".summary");
    const readBtn = card.querySelector(".read-btn");
    const actionButtons = card.querySelectorAll("button[data-action]");

    coverLink.href = toArticleHref(articleId);
    readBtn.href = toArticleHref(articleId);
    image.src = createSafeText(item.imageUrl, `https://picsum.photos/seed/${articleId || "feed"}/880/560`);
    image.alt = createSafeText(item.title, "news cover");
    meta.textContent = `${createSafeText(item.source, "News")} | ${formatRelativeTime(item.publishedAt)}`;
    title.textContent = createSafeText(item.title, "Untitled");
    summary.textContent = createSafeText(item.summary, "No summary available.");

    [coverLink, readBtn].forEach((link) => {
      link.addEventListener("click", () => trackClick(articleId));
    });

    actionButtons.forEach((btn) => {
      if (!articleId) {
        btn.disabled = true;
        return;
      }
      btn.addEventListener("click", async () => {
        const type = btn.dataset.action;
        if (!type || btn.disabled) {
          return;
        }
        btn.disabled = true;
        try {
          await recordEvent({ itemId: articleId, type });
          btn.classList.add("is-active");
        } catch (_err) {
          btn.classList.add("is-failed");
          window.setTimeout(() => btn.classList.remove("is-failed"), 320);
        } finally {
          btn.disabled = false;
        }
      });
    });

    feedEl.appendChild(card);
  });
}

function renderFallback(text) {
  feedEl.replaceChildren();
  const node = document.createElement("p");
  node.className = "muted";
  node.textContent = text;
  feedEl.appendChild(node);
}

function renderLoading() {
  headlineEl.className = "headline-loading";
  headlineEl.textContent = "Loading top story...";
  trendingEl.replaceChildren();
  feedEl.replaceChildren();

  if (briefListEl) {
    briefListEl.replaceChildren();
    const li = document.createElement("li");
    li.textContent = "Compiling the latest key points...";
    briefListEl.appendChild(li);
  }

  const loading = document.createElement("p");
  loading.className = "muted";
  loading.textContent = "Building your stream...";
  feedEl.appendChild(loading);
}

function filterArticles(items, topic, query) {
  return (Array.isArray(items) ? items : []).filter(
    (item) => matchesTopic(item, topic) && matchesSearch(item, query)
  );
}

function matchesTopic(item, topic) {
  if (!topic || topic === "top") {
    return true;
  }
  const terms = TOPIC_HINTS[topic] || [topic];
  const text = articleText(item);
  return terms.some((term) => text.includes(term));
}

function matchesSearch(item, query) {
  const value = createSafeText(query).toLowerCase();
  if (!value) {
    return true;
  }
  const words = value.split(/\s+/).filter(Boolean);
  if (!words.length) {
    return true;
  }
  const text = articleText(item);
  return words.every((word) => text.includes(word));
}

function articleText(item) {
  const tags = Array.isArray(item?.tags) ? item.tags.join(" ") : "";
  return `${createSafeText(item?.title)} ${createSafeText(item?.summary)} ${createSafeText(item?.source)} ${tags}`
    .toLowerCase();
}

function uniqueById(items) {
  const seen = new Set();
  const out = [];
  items.forEach((item) => {
    const id = createSafeText(item?.id);
    if (!id || seen.has(id)) {
      return;
    }
    seen.add(id);
    out.push(item);
  });
  return out;
}

function readTopicFromUrl() {
  return createSafeText(new URLSearchParams(window.location.search).get("topic"), "top").toLowerCase();
}

function readQueryFromUrl() {
  return createSafeText(new URLSearchParams(window.location.search).get("q"));
}

function buildHomeUrl(topic, query) {
  const params = new URLSearchParams();
  if (topic && topic !== "top") {
    params.set("topic", topic);
  }
  if (query) {
    params.set("q", query);
  }
  const str = params.toString();
  return str ? `/?${str}` : "/";
}

function updateChannelHighlight() {
  channelLinks.forEach((link) => {
    link.classList.toggle("active", (link.dataset.topic || "top") === activeTopic);
  });
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
