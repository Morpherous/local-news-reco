package com.localnews.reco.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.localnews.reco.model.NewsArticle;
import com.localnews.reco.repository.InMemoryRecoStore;
import com.localnews.reco.repository.RecoStore;

@Service
public class RecoService {
    private final RecoStore store = new InMemoryRecoStore();

    public void initSampleData() {
        long now = System.currentTimeMillis();
        store.upsertArticles(Arrays.asList(
                new NewsArticle("n1", "City Council Passes Budget", "…", "https://news/a", "https://img/a.jpg", now - 2 * 3600_000L, "LocalTimes", Arrays.asList("city", "budget")),
                new NewsArticle("n2", "Marathon This Weekend", "…", "https://news/b", "https://img/b.jpg", now - 26 * 3600_000L, "DailyPost", Arrays.asList("sport", "event")),
                new NewsArticle("n3", "New Cafe Opens", "…", "https://news/c", "https://img/c.jpg", now - 5 * 3600_000L, "Metro", Arrays.asList("food", "life"))
        ));
    }

    public List<NewsArticle> articles() {
        initSampleData();
        return new ArrayList<>(store.listArticles());
    }

    public List<NewsArticle> recommend(String userId, int limit) {
        initSampleData();
        // add more ranking logistic
        // you can use ml/deel models here
        return store.listArticles().stream()
                .sorted((a, b) -> Long.compare(
                        b.getPublishedAt() == null ? 0L : b.getPublishedAt(),
                        a.getPublishedAt() == null ? 0L : a.getPublishedAt()))
                .limit(limit <= 0 ? 10 : limit)
                .collect(java.util.stream.Collectors.toList());
    }
}
