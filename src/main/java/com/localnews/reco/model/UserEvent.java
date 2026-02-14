package com.localnews.reco.model;

public class UserEvent {
    private String eventId;
    private String userId;
    private String itemId;
    private String type;
    private Long ts;

    public UserEvent() {
    }

    public UserEvent(String eventId, String userId, String itemId, String type, Long ts) {
        this.eventId = eventId;
        this.userId = userId;
        this.itemId = itemId;
        this.type = type;
        this.ts = ts;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }
}
