package com.localnews.reco.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "news")
public class NewsSourceProperties {
    private List<String> feeds = new ArrayList<>();
    private int refreshMinutes = 20;
    private int maxArticles = 120;
    private int requestTimeoutSeconds = 12;

    public List<String> getFeeds() {
        return feeds;
    }

    public void setFeeds(List<String> feeds) {
        this.feeds = feeds;
    }

    public int getRefreshMinutes() {
        return refreshMinutes;
    }

    public void setRefreshMinutes(int refreshMinutes) {
        this.refreshMinutes = refreshMinutes;
    }

    public int getMaxArticles() {
        return maxArticles;
    }

    public void setMaxArticles(int maxArticles) {
        this.maxArticles = maxArticles;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }
}
