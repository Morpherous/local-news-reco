package com.localnews.reco.model;

import java.util.List;

public class StatsOverview {
    private long totalArticles;
    private long totalEvents;
    private List<NamedCount> eventTypes;
    private List<NamedCount> topTags;
    private List<ArticleCount> topArticles;

    public StatsOverview() {
    }

    public StatsOverview(long totalArticles, long totalEvents, List<NamedCount> eventTypes, List<NamedCount> topTags, List<ArticleCount> topArticles) {
        this.totalArticles = totalArticles;
        this.totalEvents = totalEvents;
        this.eventTypes = eventTypes;
        this.topTags = topTags;
        this.topArticles = topArticles;
    }

    public long getTotalArticles() {
        return totalArticles;
    }

    public void setTotalArticles(long totalArticles) {
        this.totalArticles = totalArticles;
    }

    public long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public List<NamedCount> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<NamedCount> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public List<NamedCount> getTopTags() {
        return topTags;
    }

    public void setTopTags(List<NamedCount> topTags) {
        this.topTags = topTags;
    }

    public List<ArticleCount> getTopArticles() {
        return topArticles;
    }

    public void setTopArticles(List<ArticleCount> topArticles) {
        this.topArticles = topArticles;
    }
}
