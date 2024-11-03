package tukano.impl.data;

import jakarta.persistence.Id;

public class Stats {
    // for Cosmos NoSQL
    String id;
    Integer viewCount;
    long lastUpdated;

    public Stats() {}

    public Stats(String id, Integer viewCount, long lastUpdated) {
        super();
        this.id = id;
        this.viewCount = viewCount;
        this.lastUpdated = lastUpdated;
    }

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public Integer getViewCount() { return viewCount; }

    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    public long getLastUpdated() { return lastUpdated; }

    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}
