package tukano.data;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Stats {
    String id;
    int views;

    public Stats() {}

    public Stats(String userId) {
        this.id = userId;
        this.views = 1;
    }

    public int getViews() { return views; }

    public void setViews(int views) { this.views = views; }

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    @Override
    public String toString() {
        return "Stats [id=" + id + ", views=" + views + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, views);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Stats other = (Stats)obj;
        return Objects.equals(id, other.id) && views == other.views;
    }
}
