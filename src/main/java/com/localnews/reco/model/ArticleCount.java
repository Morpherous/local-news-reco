package com.localnews.reco.model;

public class ArticleCount {
    private String articleId;
    private String title;
    private long count;

    public ArticleCount() {
    }

    public ArticleCount(String articleId, String title, long count) {
        this.articleId = articleId;
        this.title = title;
        this.count = count;
    }

    public String getArticleId() {
        return articleId;
    }

    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
