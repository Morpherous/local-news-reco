import {
  apiFetch,
  createSafeText,
  formatRelativeTime,
  recordEvent
} from "./client-api.js";

const params = new URLSearchParams(window.location.search);
const query = createSafeText(params.get("q"));
const topic = createSafeText(params.get("topic"), "top").toLowerCase();

const queryLineEl = document.getElementById("search-query-line");
const resultsEl = document.getElementById("search-results");
const cardTpl = document.getElementById("search-card-template");

if (queryLineEl && resultsEl && cardTpl) {
  if (!query) {
    queryLineEl.textContent = "Enter a keyword in the search box above.";
    renderEmpty("No query provided.");
  } else {
    loadResults();
  }
}

async function loadResults() {
  queryLineEl.textContent = `Searching for "${query}"...`;
  renderEmpty("Loading results...");
  try {
    const topicQuery = topic && topic !== "top" ? `&topic=${encodeURIComponent(topic)}` : "";
    const data = await apiFetch(`/api/articles?limit=120&q=${encodeURIComponent(query)}${topicQuery}`);
    const items = Array.isArray(data) ? data : [];
    queryLineEl.textContent = `${items.length} result${items.length === 1 ? "" : "s"} for "${query}"`;
    renderResults(items);
  } catch (_err) {
    queryLineEl.textContent = `Search failed for "${query}"`;
    renderEmpty("Unable to load search results right now.");
  }
}

function renderResults(items) {
  resultsEl.replaceChildren();
  if (!items.length) {
    renderEmpty(`No results found for "${query}".`);
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
    image.src = createSafeText(item.imageUrl, `https://picsum.photos/seed/${articleId || "search"}/900/560`);
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

    resultsEl.appendChild(card);
  });
}

function renderEmpty(text) {
  resultsEl.replaceChildren();
  const node = document.createElement("p");
  node.className = "muted";
  node.textContent = text;
  resultsEl.appendChild(node);
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
