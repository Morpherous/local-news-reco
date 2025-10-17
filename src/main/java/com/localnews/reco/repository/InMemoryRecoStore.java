package com.localnews.reco.repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.localnews.reco.model.NewsArticle;

public class InMemoryRecoStore implements RecoStore {
    private final Map<String, NewsArticle> articles = new ConcurrentHashMap<>();

    @Override
    public Collection<NewsArticle> listArticles() {
        return articles.values();
    }

    @Override
    public Optional<NewsArticle> getArticle(String id) {
        return Optional.ofNullable(articles.get(id));
    }

    @Override
    public void upsertArticles(Collection<NewsArticle> list) {
        list.forEach(a -> articles.put(a.getId(), a));
    }

}
