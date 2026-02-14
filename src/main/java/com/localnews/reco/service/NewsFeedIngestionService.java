package com.localnews.reco.service;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.localnews.reco.config.NewsSourceProperties;
import com.localnews.reco.model.NewsArticle;

@Service
public class NewsFeedIngestionService {
    private static final Logger log = LoggerFactory.getLogger(NewsFeedIngestionService.class);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    private final NewsSourceProperties props;
    private final HttpClient client;

    public NewsFeedIngestionService(NewsSourceProperties props) {
        this.props = props;
        int timeout = Math.max(5, props.getRequestTimeoutSeconds());
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<NewsArticle> fetchLatestArticles() {
        if (props.getFeeds() == null || props.getFeeds().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, NewsArticle> dedup = new LinkedHashMap<>();
        for (String feedUrl : props.getFeeds()) {
            String url = normalize(feedUrl);
            if (url.isEmpty()) {
                continue;
            }
            try {
                List<NewsArticle> feedArticles = fetchSingleFeed(url);
                for (NewsArticle article : feedArticles) {
                    dedup.putIfAbsent(article.getId(), article);
                }
            } catch (Exception ex) {
                log.warn("Failed to fetch {}: {}", url, ex.getMessage());
            }
        }

        List<NewsArticle> sorted = dedup.values().stream()
                .sorted(Comparator.comparingLong(this::safePublishedAt).reversed())
                .collect(Collectors.toList());

        int maxArticles = Math.max(10, props.getMaxArticles());
        if (sorted.size() > maxArticles) {
            return new ArrayList<>(sorted.subList(0, maxArticles));
        }
        return sorted;
    }

    private List<NewsArticle> fetchSingleFeed(String feedUrl) throws Exception {
        int timeout = Math.max(5, props.getRequestTimeoutSeconds());
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                .GET()
                .header("User-Agent", "local-news-reco/1.0")
                .timeout(Duration.ofSeconds(timeout))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return parseFeedXml(response.body(), feedUrl);
    }

    private List<NewsArticle> parseFeedXml(String xml, String feedUrl) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        hardenXmlParser(factory);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        Element root = doc.getDocumentElement();
        if (root == null) {
            return Collections.emptyList();
        }
        String rootName = normalize(root.getNodeName()).toLowerCase(Locale.ROOT);
        if (rootName.contains("feed")) {
            return parseAtom(doc, feedUrl);
        }
        return parseRss(doc, feedUrl);
    }

    private List<NewsArticle> parseRss(Document doc, String feedUrl) {
        List<NewsArticle> result = new ArrayList<>();
        String sourceName = extractRssSource(doc, feedUrl);
        NodeList items = doc.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element item = (Element) node;
            String title = firstTagText(item, "title");
            String link = firstTagText(item, "link");
            String summary = sanitizeSummary(firstTagText(item, "description", "content:encoded"));
            String imageUrl = firstAttr(item, "enclosure", "url");
            if (imageUrl.isEmpty()) {
                imageUrl = firstAttr(item, "media:content", "url");
            }
            if (imageUrl.isEmpty()) {
                imageUrl = firstAttr(item, "media:thumbnail", "url");
            }
            String pubRaw = firstTagText(item, "pubDate", "dc:date", "published");
            long publishedAt = parsePublishedAt(pubRaw);
            List<String> tags = extractRssCategories(item);
            String articleId = buildArticleId(firstTagText(item, "guid"), link, title, pubRaw);
            if (articleId.isEmpty() || title.isEmpty()) {
                continue;
            }
            result.add(new NewsArticle(
                    articleId,
                    title,
                    summary.isEmpty() ? "No summary from source." : summary,
                    link,
                    imageUrl,
                    publishedAt,
                    sourceName,
                    tags
            ));
        }
        return result;
    }

    private List<NewsArticle> parseAtom(Document doc, String feedUrl) {
        List<NewsArticle> result = new ArrayList<>();
        String sourceName = extractAtomSource(doc, feedUrl);
        NodeList entries = doc.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Node node = entries.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element entry = (Element) node;
            String title = firstTagText(entry, "title");
            String link = atomLink(entry);
            String summary = sanitizeSummary(firstTagText(entry, "summary", "content"));
            String imageUrl = firstAttr(entry, "media:content", "url");
            if (imageUrl.isEmpty()) {
                imageUrl = firstAttr(entry, "media:thumbnail", "url");
            }
            String pubRaw = firstTagText(entry, "published", "updated");
            long publishedAt = parsePublishedAt(pubRaw);
            List<String> tags = extractAtomCategories(entry);
            String articleId = buildArticleId(firstTagText(entry, "id"), link, title, pubRaw);
            if (articleId.isEmpty() || title.isEmpty()) {
                continue;
            }
            result.add(new NewsArticle(
                    articleId,
                    title,
                    summary.isEmpty() ? "No summary from source." : summary,
                    link,
                    imageUrl,
                    publishedAt,
                    sourceName,
                    tags
            ));
        }
        return result;
    }

    private String extractRssSource(Document doc, String feedUrl) {
        NodeList channelNodes = doc.getElementsByTagName("channel");
        if (channelNodes.getLength() > 0 && channelNodes.item(0) instanceof Element) {
            String name = firstTagText((Element) channelNodes.item(0), "title");
            if (!name.isEmpty()) {
                return name;
            }
        }
        return sourceFromUrl(feedUrl);
    }

    private String extractAtomSource(Document doc, String feedUrl) {
        NodeList feedNodes = doc.getElementsByTagName("feed");
        if (feedNodes.getLength() > 0 && feedNodes.item(0) instanceof Element) {
            String name = firstTagText((Element) feedNodes.item(0), "title");
            if (!name.isEmpty()) {
                return name;
            }
        }
        return sourceFromUrl(feedUrl);
    }

    private String sourceFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = normalize(uri.getHost());
            if (!host.isEmpty()) {
                return host;
            }
        } catch (Exception ignored) {
        }
        return "External feed";
    }

    private List<String> extractRssCategories(Element item) {
        NodeList categories = item.getElementsByTagName("category");
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < categories.getLength(); i++) {
            String tag = normalizeTag(text(categories.item(i)));
            if (!tag.isEmpty() && !tags.contains(tag)) {
                tags.add(tag);
            }
            if (tags.size() >= 6) {
                break;
            }
        }
        return tags;
    }

    private List<String> extractAtomCategories(Element entry) {
        NodeList categories = entry.getElementsByTagName("category");
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < categories.getLength(); i++) {
            Node node = categories.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element category = (Element) node;
            String tag = normalizeTag(category.getAttribute("term"));
            if (tag.isEmpty()) {
                tag = normalizeTag(text(node));
            }
            if (!tag.isEmpty() && !tags.contains(tag)) {
                tags.add(tag);
            }
            if (tags.size() >= 6) {
                break;
            }
        }
        return tags;
    }

    private String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        String fallback = "";
        for (int i = 0; i < links.getLength(); i++) {
            Node node = links.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element link = (Element) node;
            String href = normalize(link.getAttribute("href"));
            if (href.isEmpty()) {
                continue;
            }
            if (fallback.isEmpty()) {
                fallback = href;
            }
            String rel = normalize(link.getAttribute("rel"));
            if (rel.isEmpty() || "alternate".equalsIgnoreCase(rel)) {
                return href;
            }
        }
        return fallback;
    }

    private String buildArticleId(String guid, String link, String title, String publishedRaw) {
        String basis = normalize(guid);
        if (basis.isEmpty()) {
            basis = normalize(link);
        }
        if (basis.isEmpty()) {
            basis = normalize(title) + "|" + normalize(publishedRaw);
        }
        if (basis.isEmpty()) {
            return "";
        }
        String uuid = UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return "n-" + uuid.substring(0, 16);
    }

    private String sanitizeSummary(String raw) {
        String value = normalize(raw);
        if (value.isEmpty()) {
            return "";
        }
        value = value.replace("&nbsp;", " ");
        value = HTML_TAG_PATTERN.matcher(value).replaceAll(" ");
        value = SPACE_PATTERN.matcher(value).replaceAll(" ").trim();
        if (value.length() > 280) {
            return value.substring(0, 277) + "...";
        }
        return value;
    }

    private String normalizeTag(String raw) {
        String value = normalize(raw).toLowerCase(Locale.ROOT);
        value = value.replaceAll("[\\s/]+", "-");
        value = value.replaceAll("[^a-z0-9\\-]", "");
        if (value.length() > 24) {
            value = value.substring(0, 24);
        }
        return value;
    }

    private long parsePublishedAt(String raw) {
        String value = normalize(raw);
        if (value.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        return System.currentTimeMillis();
    }

    private void hardenXmlParser(DocumentBuilderFactory factory) {
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        safeSetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        safeSetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        safeSetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        safeSetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    }

    private void safeSetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
        }
    }

    private String firstTagText(Element element, String... tagNames) {
        for (String tagName : tagNames) {
            NodeList nodes = element.getElementsByTagName(tagName);
            if (nodes.getLength() == 0) {
                continue;
            }
            String value = normalize(text(nodes.item(0)));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String firstAttr(Element element, String tagName, String attrName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        Node first = nodes.item(0);
        if (!(first instanceof Element)) {
            return "";
        }
        return normalize(((Element) first).getAttribute(attrName));
    }

    private String text(Node node) {
        return node == null ? "" : normalize(node.getTextContent());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private long safePublishedAt(NewsArticle article) {
        return article.getPublishedAt() == null ? 0L : article.getPublishedAt();
    }
}
