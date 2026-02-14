import {
  apiFetch,
  createSafeText,
  formatRelativeTime,
  getUserId,
  recordEvent
} from "./client-api.js";

const messageEl = document.getElementById("article-message");
const loadingEl = document.getElementById("article-loading");
const detailEl = document.getElementById("article-detail");
const imageEl = document.getElementById("detail-image");
const metaEl = document.getElementById("detail-meta");
const titleEl = document.getElementById("detail-title");
const summaryEl = document.getElementById("detail-summary");
const tagsEl = document.getElementById("detail-tags");
const sourceLinkEl = document.getElementById("detail-source-link");
const recommendationEl = document.getElementById("detail-recommendations");
const recommendationTpl = document.getElementById("detail-reco-template");
const actionButtons = document.querySelectorAll(".actions button[data-action]");

const articleId = new URLSearchParams(window.location.search).get("id");

if (!articleId) {
  showMessage("Missing article id. Open this page from Home.", "error");
  if (loadingEl) {
    loadingEl.textContent = "Invalid URL";
  }
} else {
  bindActions(articleId);
  loadPage(articleId);
}

async function loadPage(id) {
  showMessage("");
  if (loadingEl) {
    loadingEl.textContent = "Loading story...";
  }
  if (detailEl) {
    detailEl.hidden = true;
  }
  try {
    const userId = getUserId();
    const [article, recs] = await Promise.all([
      apiFetch(`/api/articles/${encodeURIComponent(id)}`),
      apiFetch(`/api/users/${encodeURIComponent(userId)}/recommendations?limit=7`)
    ]);

    renderArticle(article);
    renderRecommendations((Array.isArray(recs) ? recs : []).filter((item) => item.id !== id).slice(0, 6));

    if (loadingEl) {
      loadingEl.textContent = "";
    }
    if (detailEl) {
      detailEl.hidden = false;
    }
    recordEvent({ itemId: id, type: "impression" }).catch(() => {});
  } catch (err) {
    showMessage(`Failed to load story: ${err.message}`, "error");
    if (loadingEl) {
      loadingEl.textContent = "Unable to open this story right now.";
    }
  }
}

function renderArticle(article) {
  const source = createSafeText(article.source, "News");
  const articleLink = createSafeText(article.url, "#");
  const fallbackSeed = createSafeText(article.id, "article");

  imageEl.src = createSafeText(article.imageUrl, `https://picsum.photos/seed/${fallbackSeed}/1200/760`);
  imageEl.alt = createSafeText(article.title, "article cover");
  metaEl.textContent = `${source} | ${formatRelativeTime(article.publishedAt)}`;
  titleEl.textContent = createSafeText(article.title, "Untitled");
  summaryEl.textContent = createSafeText(article.summary, "No summary available.");
  sourceLinkEl.href = articleLink;

  tagsEl.replaceChildren();
  if (Array.isArray(article.tags) && article.tags.length > 0) {
    article.tags.forEach((tag) => {
      const chip = document.createElement("span");
      chip.className = "pill";
      chip.textContent = tag;
      tagsEl.appendChild(chip);
    });
  }
}

function renderRecommendations(items) {
  recommendationEl.replaceChildren();
  if (!items.length) {
    const p = document.createElement("p");
    p.className = "muted";
    p.textContent = "No more stories right now.";
    recommendationEl.appendChild(p);
    return;
  }

  items.forEach((item) => {
    const articleRef = createSafeText(item.id);
    const card = recommendationTpl.content.firstElementChild.cloneNode(true);
    const img = card.querySelector(".cover");
    const meta = card.querySelector(".meta");
    const title = card.querySelector(".title");

    card.href = toArticleHref(articleRef);
    img.src = createSafeText(item.imageUrl, `https://picsum.photos/seed/${articleRef || "reco"}/760/520`);
    img.alt = createSafeText(item.title, "recommendation");
    meta.textContent = `${createSafeText(item.source, "News")} | ${formatRelativeTime(item.publishedAt)}`;
    title.textContent = createSafeText(item.title, "Untitled");

    card.addEventListener("click", () => trackClick(articleRef));

    recommendationEl.appendChild(card);
  });
}

function bindActions(itemId) {
  actionButtons.forEach((btn) => {
    btn.addEventListener("click", async () => {
      const action = btn.dataset.action;
      if (!action) {
        return;
      }
      if (btn.disabled) {
        return;
      }
      btn.disabled = true;
      try {
        await recordEvent({ itemId, type: action });
        btn.classList.add("is-active");
      } catch (_err) {
        btn.classList.add("is-failed");
        window.setTimeout(() => btn.classList.remove("is-failed"), 320);
      } finally {
        btn.disabled = false;
      }
    });
  });
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
