package com.localnews.reco.model;

import java.util.List;

public class NewsArticle {
    private String id;
    private String title;
    private String summary;
    private String url;
    private String imageUrl;
    private Long publishedAt;
    private String source;
    private List<String> tags;

    public NewsArticle(String id, String title, String summary, String url,
                       String imageUrl, Long publishedAt, String source, List<String> tags) {
        this.id = "id";
        this.title = "title";
        this.summary = "summary";
        this.url = "url";
        this.imageUrl = "imageUrl";
        this.publishedAt = publishedAt;
        this.source = "source";
        this.tags = List.of("stuff", "stuff");

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Long getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Long publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
