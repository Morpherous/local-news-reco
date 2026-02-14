import {
  apiFetch,
  createSafeText,
  formatRelativeTime,
  recordEvent
} from "./client-api.js";

const listEl = document.getElementById("articles-list");
const tpl = document.getElementById("articles-card-template");
const searchInput = document.getElementById("articles-search");
const refreshBtn = document.getElementById("articles-refresh");
const tagsEl = document.getElementById("articles-tag-filters");
const messageEl = document.getElementById("articles-message");

let allArticles = [];
let selectedTag = "";
let searchText = "";

if (listEl && tpl && searchInput && refreshBtn && tagsEl) {
  refreshBtn.addEventListener("click", loadArticles);

  searchInput.addEventListener("input", () => {
    searchText = searchInput.value.trim().toLowerCase();
    renderFiltered();
  });

  tagsEl.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement) || !target.classList.contains("filter-tag")) {
      return;
    }
    selectedTag = target.dataset.tag || "";
    renderFilterTags(allArticles);
    renderFiltered();
  });

  listEl.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }
    const action = target.dataset.action;
    const itemId = target.dataset.itemId;
    if (!action || !itemId) {
      return;
    }
    try {
      await recordEvent({ itemId, type: action });
      showMessage(action === "like" ? "Saved to your interests." : "Share intent recorded.", "ok");
    } catch (err) {
      showMessage(`Action failed: ${err.message}`, "error");
    }
  });

  loadArticles();
}

async function loadArticles() {
  showMessage("");
  renderPlaceholder("Loading stories...");
  try {
    const data = await apiFetch("/api/articles");
    allArticles = Array.isArray(data) ? data : [];
    renderFilterTags(allArticles);
    renderFiltered();
  } catch (err) {
    renderPlaceholder("Unable to load stories right now.");
    showMessage(`Failed to load stories: ${err.message}`, "error");
  }
}

function renderFilterTags(items) {
  tagsEl.replaceChildren();
  const tagSet = new Set();
  items.forEach((article) => {
    if (Array.isArray(article.tags)) {
      article.tags.forEach((tag) => tagSet.add(tag));
    }
  });

  tagsEl.appendChild(createTagChip("All", ""));
  [...tagSet].sort((a, b) => a.localeCompare(b)).forEach((tag) => {
    tagsEl.appendChild(createTagChip(`#${tag}`, tag));
  });
}

function createTagChip(label, tagValue) {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "filter-tag";
  btn.textContent = label;
  btn.dataset.tag = tagValue;
  if (selectedTag === tagValue) {
    btn.classList.add("active");
  }
  return btn;
}

function renderFiltered() {
  const filtered = allArticles.filter((item) => {
    const text = `${item.title || ""} ${item.summary || ""}`.toLowerCase();
    const matchesSearch = !searchText || text.includes(searchText);
    const tags = Array.isArray(item.tags) ? item.tags : [];
    const matchesTag = !selectedTag || tags.includes(selectedTag);
    return matchesSearch && matchesTag;
  });

  listEl.replaceChildren();
  if (filtered.length === 0) {
    renderPlaceholder("No matches found. Try another keyword.");
    return;
  }

  filtered.forEach((item) => {
    const articleId = createSafeText(item.id);
    const card = tpl.content.firstElementChild.cloneNode(true);
    const coverLink = card.querySelector(".cover-link");
    const image = card.querySelector(".cover");
    const meta = card.querySelector(".meta");
    const headline = card.querySelector(".headline");
    const summary = card.querySelector(".summary");
    const tags = card.querySelector(".tags");
    const openLink = card.querySelector(".open-link");
    const actionButtons = card.querySelectorAll("button[data-action]");

    const source = createSafeText(item.source, "News");
    coverLink.href = toArticleHref(articleId);
    openLink.href = toArticleHref(articleId);
    image.src = createSafeText(item.imageUrl, `https://picsum.photos/seed/${articleId || "explore"}/780/480`);
    image.alt = createSafeText(item.title, "news cover");

    meta.textContent = `${source} | ${formatRelativeTime(item.publishedAt)}`;
    headline.textContent = createSafeText(item.title, "Untitled");
    summary.textContent = createSafeText(item.summary, "No summary available.");

    if (Array.isArray(item.tags)) {
      item.tags.slice(0, 4).forEach((tagName) => {
        const chip = document.createElement("span");
        chip.className = "pill";
        chip.textContent = tagName;
        tags.appendChild(chip);
      });
    }

    [coverLink, openLink].forEach((link) => {
      link.addEventListener("click", () => trackClick(articleId));
    });

    actionButtons.forEach((button) => {
      if (articleId) {
        button.dataset.itemId = articleId;
      } else {
        delete button.dataset.itemId;
        button.disabled = true;
      }
    });

    listEl.appendChild(card);
  });
}

function renderPlaceholder(text) {
  listEl.replaceChildren();
  const node = document.createElement("p");
  node.className = "muted";
  node.textContent = text;
  listEl.appendChild(node);
}

function showMessage(text, type = "ok") {
  if (!messageEl) {
    return;
  }
  if (!text) {
    messageEl.hidden = true;
    messageEl.textContent = "";
    messageEl.className = "message";
    return;
  }
  messageEl.hidden = false;
  messageEl.textContent = text;
  messageEl.className = `message ${type}`;
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
