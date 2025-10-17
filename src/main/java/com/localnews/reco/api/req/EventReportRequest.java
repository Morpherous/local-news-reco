package com.localnews.reco.api.req;

public class EventReportRequest {
    private String userId;
    private String itemId;
    private String type;     // impression | click | share | like
    private Long ts;

    public EventReportRequest() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String v) {
        this.userId = v;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String v) {
        this.itemId = v;
    }

    public String getType() {
        return type;
    }

    public void setType(String v) {
        this.type = v;
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long v) {
        this.ts = v;
    }
}
