package com.localnews.reco.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.localnews.reco.model.NewsArticle;
import com.localnews.reco.model.UserEvent;

public interface RecoStore {
    Collection<NewsArticle> listArticles();

    Optional<NewsArticle> getArticle(String id);

    void upsertArticles(Collection<NewsArticle> articles);

    void saveEvent(UserEvent event);

    List<UserEvent> listEvents();

    List<UserEvent> listEventsByUser(String userId);

}
