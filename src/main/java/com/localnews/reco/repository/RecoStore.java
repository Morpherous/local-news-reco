package com.localnews.reco.repository;

import java.util.Collection;
import java.util.Optional;

import com.localnews.reco.model.NewsArticle;

public interface RecoStore {
    Collection<NewsArticle> listArticles();

    Optional<NewsArticle> getArticle(String id);

    void upsertArticles(Collection<NewsArticle> articles);

}
