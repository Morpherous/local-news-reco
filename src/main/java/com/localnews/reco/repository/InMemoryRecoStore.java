package com.localnews.reco.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.localnews.reco.model.NewsArticle;
import com.localnews.reco.model.UserEvent;

public class InMemoryRecoStore implements RecoStore {
    private final Map<String, NewsArticle> articles = new ConcurrentHashMap<>();
    private final List<UserEvent> events = Collections.synchronizedList(new ArrayList<>());

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

    @Override
    public void saveEvent(UserEvent event) {
        events.add(event);
    }

    @Override
    public List<UserEvent> listEvents() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    @Override
    public List<UserEvent> listEventsByUser(String userId) {
        synchronized (events) {
            return events.stream()
                    .filter(evt -> evt.getUserId().equals(userId))
                    .collect(java.util.stream.Collectors.toList());
        }
    }
}
