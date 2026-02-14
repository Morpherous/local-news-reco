package com.localnews.reco.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.localnews.reco.config.NewsSourceProperties;
import com.localnews.reco.model.ArticleCount;
import com.localnews.reco.model.EventReportRequest;
import com.localnews.reco.model.NamedCount;
import com.localnews.reco.model.NewsArticle;
import com.localnews.reco.model.StatsOverview;
import com.localnews.reco.model.UserEvent;
import com.localnews.reco.repository.InMemoryRecoStore;
import com.localnews.reco.repository.RecoStore;

@Service
public class RecoService {
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 60;
    private static final int DEFAULT_ARTICLE_LIMIT = 24;
    private static final int MAX_ARTICLE_LIMIT = 120;
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of("impression", "click", "like", "share");
    private static final long HOUR_MS = 3_600_000L;
    private static final Map<String, List<String>> TOPIC_HINTS = Map.of(
            "politics", List.of("politic", "government", "election", "senate", "congress", "policy", "white house"),
            "business", List.of("business", "market", "economy", "finance", "stock", "trade", "company"),
            "technology", List.of("technology", "tech", "ai", "software", "startup", "chip", "internet", "app"),
            "sports", List.of("sport", "game", "league", "player", "coach", "team", "match", "nfl", "nba", "mlb"),
            "culture", List.of("culture", "music", "movie", "film", "art", "festival", "book", "media"),
            "lifestyle", List.of("lifestyle", "health", "travel", "food", "fashion", "wellness", "home")
    );

    private final RecoStore store;
    private final NewsFeedIngestionService feedIngestionService;
    private final long refreshIntervalMs;
    private final AtomicLong lastRefreshAt = new AtomicLong(0L);
    private final Object refreshLock = new Object();

    public RecoService(NewsFeedIngestionService feedIngestionService, NewsSourceProperties props) {
        this.store = new InMemoryRecoStore();
        this.feedIngestionService = feedIngestionService;
        int refreshMinutes = Math.max(1, props.getRefreshMinutes());
        this.refreshIntervalMs = refreshMinutes * 60_000L;
    }

    public List<NewsArticle> articles() {
        return articles(null, null, MAX_ARTICLE_LIMIT);
    }

    public List<NewsArticle> articles(String topic, String query, int limit) {
        ensureArticlesReady();
        int cappedLimit = normalizeArticleLimit(limit);
        String normalizedTopic = normalizeTopic(topic);
        List<String> queryTerms = tokenizeQuery(query);

        return rankArticles(
                store.listArticles(),
                new HashMap<>(),
                new HashMap<>(),
                normalizedTopic,
                queryTerms,
                cappedLimit,
                false
        );
    }

    public Optional<NewsArticle> articleById(String articleId) {
        ensureArticlesReady();
        return store.getArticle(articleId);
    }

    public List<NewsArticle> recommend(String userId, int limit) {
        return recommend(userId, limit, null, null);
    }

    public List<NewsArticle> recommend(String userId, int limit, String topic, String query) {
        ensureArticlesReady();
        String normalizedUserId = sanitizeUserId(userId);
        int cappedLimit = normalizeLimit(limit);
        String normalizedTopic = normalizeTopic(topic);
        List<String> queryTerms = tokenizeQuery(query);
        Map<String, Double> tagAffinity = buildTagAffinity(normalizedUserId);
        Map<String, Double> sourceAffinity = buildSourceAffinity(normalizedUserId);

        return rankArticles(
                store.listArticles(),
                tagAffinity,
                sourceAffinity,
                normalizedTopic,
                queryTerms,
                cappedLimit,
                true
        );
    }

    public UserEvent recordEvent(EventReportRequest req) {
        ensureArticlesReady();
        if (req == null) {
            throw new IllegalArgumentException("request body is required");
        }

        String userId = sanitizeUserId(req.getUserId());
        String itemId = normalize(req.getItemId());
        String type = normalize(req.getType()).toLowerCase(Locale.ROOT);

        if (itemId.isEmpty()) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (!ALLOWED_EVENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("type must be one of: impression, click, like, share");
        }
        if (store.getArticle(itemId).isEmpty()) {
            throw new IllegalArgumentException("itemId not found: " + itemId);
        }

        UserEvent event = new UserEvent(
                "evt-" + UUID.randomUUID(),
                userId,
                itemId,
                type,
                req.getTs() == null ? System.currentTimeMillis() : req.getTs()
        );
        store.saveEvent(event);
        return event;
    }

    public List<UserEvent> userEvents(String userId, int limit) {
        ensureArticlesReady();
        String normalizedUserId = sanitizeUserId(userId);
        int cappedLimit = normalizeLimit(limit);
        return store.listEventsByUser(normalizedUserId).stream()
                .sorted(Comparator.comparingLong(this::safeEventTs).reversed())
                .limit(cappedLimit)
                .collect(Collectors.toList());
    }

    public StatsOverview overview() {
        ensureArticlesReady();
        List<NewsArticle> articleList = new ArrayList<>(store.listArticles());
        List<UserEvent> eventList = store.listEvents();

        List<NamedCount> eventTypes = toNamedCount(eventList.stream()
                .collect(Collectors.groupingBy(UserEvent::getType, Collectors.counting())));

        List<NamedCount> topTags = toNamedCount(buildTagHeat(eventList));
        if (topTags.isEmpty()) {
            topTags = toNamedCount(articleList.stream()
                    .filter(article -> article.getTags() != null)
                    .flatMap(article -> article.getTags().stream())
                    .collect(Collectors.groupingBy(tag -> tag, Collectors.counting())));
        }

        List<ArticleCount> topArticles = eventList.stream()
                .collect(Collectors.groupingBy(UserEvent::getItemId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new ArticleCount(
                        entry.getKey(),
                        store.getArticle(entry.getKey()).map(NewsArticle::getTitle).orElse(entry.getKey()),
                        entry.getValue()))
                .collect(Collectors.toList());

        return new StatsOverview(
                articleList.size(),
                eventList.size(),
                eventTypes,
                topTags,
                topArticles
        );
    }

    private void ensureArticlesReady() {
        long now = System.currentTimeMillis();
        if (!store.listArticles().isEmpty() && now - lastRefreshAt.get() < refreshIntervalMs) {
            return;
        }

        synchronized (refreshLock) {
            long current = System.currentTimeMillis();
            if (!store.listArticles().isEmpty() && current - lastRefreshAt.get() < refreshIntervalMs) {
                return;
            }

            List<NewsArticle> latest = feedIngestionService.fetchLatestArticles();
            if (!latest.isEmpty()) {
                store.upsertArticles(latest);
            } else if (store.listArticles().isEmpty()) {
                seedFallbackArticles(current);
            }
            lastRefreshAt.set(current);
        }
    }

    private void seedFallbackArticles(long now) {
        store.upsertArticles(Arrays.asList(
                new NewsArticle(
                        "fallback-n1",
                        "Fallback News: Transit Expansion Plan",
                        "External feeds are temporarily unavailable, so local fallback content is served.",
                        "https://example.com/fallback/transit",
                        "",
                        now - 2 * HOUR_MS,
                        "Fallback Feed",
                        Arrays.asList("fallback", "city", "transport")
                ),
                new NewsArticle(
                        "fallback-n2",
                        "Fallback News: Community Weekend Event",
                        "This placeholder item keeps the recommendation flow alive while feeds recover.",
                        "https://example.com/fallback/community",
                        "",
                        now - 5 * HOUR_MS,
                        "Fallback Feed",
                        Arrays.asList("fallback", "community", "event")
                ),
                new NewsArticle(
                        "fallback-n3",
                        "Fallback News: Local Business Snapshot",
                        "The system will automatically switch back to live feed content once reachable.",
                        "https://example.com/fallback/business",
                        "",
                        now - 8 * HOUR_MS,
                        "Fallback Feed",
                        Arrays.asList("fallback", "business", "economy")
                )
        ));
    }

    private Map<String, Double> buildTagAffinity(String userId) {
        List<UserEvent> events = store.listEventsByUser(userId);
        Map<String, Double> scores = new HashMap<>();
        for (UserEvent event : events) {
            Optional<NewsArticle> article = store.getArticle(event.getItemId());
            if (article.isEmpty() || article.get().getTags() == null) {
                continue;
            }
            double weight = eventWeight(event.getType());
            for (String tag : article.get().getTags()) {
                scores.merge(tag, weight, Double::sum);
            }
        }
        return scores;
    }

    private Map<String, Double> buildSourceAffinity(String userId) {
        List<UserEvent> events = store.listEventsByUser(userId);
        Map<String, Double> scores = new HashMap<>();
        for (UserEvent event : events) {
            Optional<NewsArticle> article = store.getArticle(event.getItemId());
            if (article.isEmpty()) {
                continue;
            }
            String source = normalize(article.get().getSource());
            if (source.isEmpty()) {
                continue;
            }
            scores.merge(source, eventWeight(event.getType()), Double::sum);
        }
        return scores;
    }

    private Map<String, Long> buildTagHeat(Collection<UserEvent> eventList) {
        Map<String, Long> heat = new HashMap<>();
        for (UserEvent event : eventList) {
            Optional<NewsArticle> article = store.getArticle(event.getItemId());
            if (article.isEmpty() || article.get().getTags() == null) {
                continue;
            }
            long weight = Math.round(eventWeight(event.getType()) * 10);
            for (String tag : article.get().getTags()) {
                heat.merge(tag, weight, Long::sum);
            }
        }
        return heat;
    }

    private List<NamedCount> toNamedCount(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private List<NewsArticle> rankArticles(
            Collection<NewsArticle> candidates,
            Map<String, Double> tagAffinity,
            Map<String, Double> sourceAffinity,
            String topic,
            List<String> queryTerms,
            int limit,
            boolean personalized) {
        boolean hasTopicFilter = !"top".equals(topic);
        boolean hasQuery = !queryTerms.isEmpty();

        return candidates.stream()
                .map(article -> {
                    double baseScore = personalized
                            ? scoreArticle(article, tagAffinity, sourceAffinity)
                            : freshnessScore(article.getPublishedAt());
                    double topicScore = topicRelevanceScore(article, topic);
                    double queryScore = queryRelevanceScore(article, queryTerms);

                    if (hasTopicFilter && topicScore <= 0D) {
                        return null;
                    }
                    if (hasQuery && queryScore <= 0D) {
                        return null;
                    }

                    double finalScore = baseScore + topicScore * 4.2D + queryScore * 6.4D;
                    return new ScoredArticle(article, finalScore);
                })
                .filter(scored -> scored != null)
                .sorted((left, right) -> {
                    int scoreDiff = Double.compare(right.getScore(), left.getScore());
                    if (scoreDiff != 0) {
                        return scoreDiff;
                    }
                    return Long.compare(safePublishedAt(right.getArticle()), safePublishedAt(left.getArticle()));
                })
                .limit(limit)
                .map(ScoredArticle::getArticle)
                .collect(Collectors.toList());
    }

    private double scoreArticle(NewsArticle article, Map<String, Double> tagAffinity, Map<String, Double> sourceAffinity) {
        double freshScore = freshnessScore(article.getPublishedAt());
        double tagScore = 0D;
        if (article.getTags() != null) {
            for (String tag : article.getTags()) {
                tagScore += tagAffinity.getOrDefault(tag, 0D);
            }
        }
        double sourceScore = sourceAffinity.getOrDefault(normalize(article.getSource()), 0D) * 0.45;
        return freshScore + tagScore + sourceScore;
    }

    private double topicRelevanceScore(NewsArticle article, String topic) {
        if (topic == null || topic.isEmpty() || "top".equals(topic)) {
            return 0D;
        }
        List<String> hints = TOPIC_HINTS.getOrDefault(topic, List.of(topic));
        String title = normalize(article.getTitle()).toLowerCase(Locale.ROOT);
        String summary = normalize(article.getSummary()).toLowerCase(Locale.ROOT);
        String source = normalize(article.getSource()).toLowerCase(Locale.ROOT);
        List<String> tags = article.getTags() == null ? List.of() : article.getTags().stream()
                .map(tag -> normalize(tag).toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());

        double score = 0D;
        for (String hint : hints) {
            if (hint.isEmpty()) {
                continue;
            }
            if (title.contains(hint)) {
                score += 1.8D;
            }
            if (summary.contains(hint)) {
                score += 1.2D;
            }
            if (source.contains(hint)) {
                score += 0.8D;
            }
            if (tags.stream().anyMatch(tag -> tag.contains(hint))) {
                score += 2.0D;
            }
        }
        return score;
    }

    private double queryRelevanceScore(NewsArticle article, List<String> queryTerms) {
        if (queryTerms == null || queryTerms.isEmpty()) {
            return 0D;
        }
        String title = normalize(article.getTitle()).toLowerCase(Locale.ROOT);
        String summary = normalize(article.getSummary()).toLowerCase(Locale.ROOT);
        String source = normalize(article.getSource()).toLowerCase(Locale.ROOT);
        List<String> tags = article.getTags() == null ? List.of() : article.getTags().stream()
                .map(tag -> normalize(tag).toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());

        double score = 0D;
        for (String term : queryTerms) {
            if (term.isEmpty()) {
                continue;
            }
            if (title.contains(term)) {
                score += 2.3D;
            }
            if (summary.contains(term)) {
                score += 1.4D;
            }
            if (source.contains(term)) {
                score += 0.6D;
            }
            if (tags.stream().anyMatch(tag -> tag.contains(term))) {
                score += 1.7D;
            }
        }
        return score;
    }

    private double freshnessScore(Long publishedAt) {
        if (publishedAt == null || publishedAt <= 0) {
            return 0D;
        }
        double ageHours = (System.currentTimeMillis() - publishedAt) / (double) HOUR_MS;
        double maxAge = 120D;
        if (ageHours >= maxAge) {
            return 0D;
        }
        return ((maxAge - ageHours) / maxAge) * 6D;
    }

    private double eventWeight(String eventType) {
        String value = normalize(eventType).toLowerCase(Locale.ROOT);
        switch (value) {
            case "share":
                return 2.8D;
            case "like":
                return 2.2D;
            case "click":
                return 1.4D;
            case "impression":
            default:
                return 0.4D;
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeArticleLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_ARTICLE_LIMIT;
        }
        return Math.min(limit, MAX_ARTICLE_LIMIT);
    }

    private long safePublishedAt(NewsArticle article) {
        return article.getPublishedAt() == null ? 0L : article.getPublishedAt();
    }

    private long safeEventTs(UserEvent event) {
        return event.getTs() == null ? 0L : event.getTs();
    }

    private String sanitizeUserId(String userId) {
        String normalized = normalize(userId);
        return normalized.isEmpty() ? "guest" : normalized;
    }

    private String normalizeTopic(String topic) {
        String value = normalize(topic).toLowerCase(Locale.ROOT);
        return value.isEmpty() ? "top" : value;
    }

    private List<String> tokenizeQuery(String query) {
        String normalized = normalize(query).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split("\\s+"))
                .filter(part -> !part.isBlank())
                .limit(10)
                .collect(Collectors.toList());
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static class ScoredArticle {
        private final NewsArticle article;
        private final double score;

        private ScoredArticle(NewsArticle article, double score) {
            this.article = article;
            this.score = score;
        }

        private NewsArticle getArticle() {
            return article;
        }

        private double getScore() {
            return score;
        }
    }
}
