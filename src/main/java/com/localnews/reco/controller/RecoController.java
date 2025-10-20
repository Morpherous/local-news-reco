package com.localnews.reco.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.localnews.reco.model.NewsArticle;
import com.localnews.reco.service.RecoService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class RecoController {
    private final RecoService svc;

    public RecoController(RecoService svc) {
        this.svc = svc;
    }

    @GetMapping("/articles")
    public List<NewsArticle> articles() {
        System.out.println("get all the articles");
        return svc.articles();
    }

    @GetMapping("/users/{userId}/recommendations")
    public List<NewsArticle> recs(@PathVariable String userId, @RequestParam(defaultValue = "10") int limit) {
        System.out.println("get limit results");
        return svc.recommend(userId, limit);
    }

    // user action record
//    @PostMapping("/events")
//    public ResponseEntity<?> event(@RequestBody EventReportRequest req) {
//
//    }
}
