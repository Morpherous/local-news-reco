package com.localnews.reco.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.localnews.reco.model.EventReportRequest;
import com.localnews.reco.model.NewsArticle;
import com.localnews.reco.model.StatsOverview;
import com.localnews.reco.model.UserEvent;
import com.localnews.reco.service.RecoService;

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*")
public class RecoController {
    private final RecoService svc;

    public RecoController(RecoService svc) {
        this.svc = svc;
    }

    @GetMapping("/articles")
    public List<NewsArticle> articles(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "24") int limit) {
        return svc.articles(topic, q, limit);
    }

    @GetMapping("/articles/{articleId}")
    public ResponseEntity<NewsArticle> articleById(@PathVariable String articleId) {
        Optional<NewsArticle> article = svc.articleById(articleId);
        return article.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{userId}/recommendations")
    public List<NewsArticle> recs(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String q) {
        return svc.recommend(userId, limit, topic, q);
    }

    @PostMapping("/events")
    public ResponseEntity<?> event(@RequestBody EventReportRequest req) {
        try {
            UserEvent saved = svc.recordEvent(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/users/{userId}/events")
    public List<UserEvent> userEvents(@PathVariable String userId, @RequestParam(defaultValue = "20") int limit) {
        return svc.userEvents(userId, limit);
    }

    @GetMapping("/stats/overview")
    public StatsOverview overview() {
        return svc.overview();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
