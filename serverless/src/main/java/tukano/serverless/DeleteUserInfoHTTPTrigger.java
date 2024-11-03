package tukano.serverless;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.NotFoundException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class DeleteUserInfoHTTPTrigger {
    private static final String SHORTS_FUNCTION_NAME = "deleteUserInfoHTTP";

    private static final String COSMOS_ENDPOINT = System.getenv("COSMOSDB_URL");
    private static final String COSMOS_KEY = System.getenv("COSMOSDB_KEY");
    private static final String DATABASE_NAME =
        System.getenv("COSMOSDB_DATABASE");
    public static final String USERS_CONTAINER = "users";
    public static final String SHORTS_CONTAINER = "shorts";
    public static final String LIKES_CONTAINER = "likes";
    public static final String STATS_CONTAINER = "stats";
    public static final String FOLLOWING_CONTAINER = "following";
    private static final String USER_ID = "userId";

    private static final String RedisHostname = System.getenv("REDIS_URL");
    private static final String RedisKey = System.getenv("REDIS_KEY");
    private static final int REDIS_PORT = 6380;
    private static final int REDIS_TIMEOUT = 3000;
    private static final boolean Redis_USE_TLS = true;

    private static JedisPool jedisPool;

    @FunctionName(SHORTS_FUNCTION_NAME)
    public void
    updateShortViews(@HttpTrigger(name = "req", methods = {HttpMethod.GET},
                                  route = "users/{" + USER_ID + "}",
                                  authLevel = AuthorizationLevel.ANONYMOUS)
                     HttpRequestMessage<Optional<String>> request,
                     @BindingName(USER_ID) String userId,
                     final ExecutionContext context) {

        CosmosClient cosmosClient = new CosmosClientBuilder()
                                        .endpoint(COSMOS_ENDPOINT)
                                        .key(COSMOS_KEY)
                                        .buildClient();

        try {
            CosmosDatabase db = cosmosClient.getDatabase(DATABASE_NAME);
            CosmosContainer shortsContainer = db.getContainer(SHORTS_CONTAINER);
            CosmosContainer followingContainer =
                db.getContainer(FOLLOWING_CONTAINER);
            CosmosContainer likesContainer = db.getContainer(LIKES_CONTAINER);

            List<Exception> errs = new ArrayList<>();

            var shortsQuery = String.format(
                "SELECT * FROM Shorts s WHERE s.ownerId = '%s'", userId);
            deleteFromContainer(context, shortsContainer, shortsQuery,
                                Short.class, errs);

            var followingQuery = String.format(
                "SELECT * FROM Following f WHERE f.follower = '%s' "
                    + "OR f.followee = '%s'",
                userId, userId);
            deleteFromContainer(context, followingContainer, followingQuery,
                                Following.class, errs);

            var likesQuery =
                String.format("SELECT * FROM Likes l WHERE l.ownerId = '%s' "
                                  + "OR l.userId = '%s'",
                              userId, userId);
            deleteFromContainer(context, likesContainer, likesQuery,
                                Likes.class, errs);

            if (!errs.isEmpty()) {
                context.getLogger().warning(
                    "Errors occurred when deleting userInfo: \n" +
                    errs.toString());
            } else {
                context.getLogger().info("Successfully deleted user info!");
            }

        } catch (CosmosException ce) {
            context.getLogger().severe("CosmosException: " + ce.getMessage());
        } catch (Exception e) {
            context.getLogger().severe("Error updating view count: " +
                                       e.getMessage());
        } finally {
            if (cosmosClient != null) {
                cosmosClient.close();
            }
        }
    }

    public synchronized static JedisPool getCachePool() {
        if (jedisPool != null)
            return jedisPool;

        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        jedisPool = new JedisPool(poolConfig, RedisHostname, REDIS_PORT,
                                  REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
        return jedisPool;
    }

    private <T> void deleteFromContainer(ExecutionContext context,
                                         CosmosContainer container,
                                         String query, Class<T> clazz,
                                         List<Exception> errs) {
        container.queryItems(query, new CosmosQueryRequestOptions(), clazz)
            .forEach(item -> {
                try {
                    if (container.getId().equals(SHORTS_CONTAINER)) {
                        Short s = (Short) item;
                        String key = "short:" + s.getShortId();

                        try (var jedis = getCachePool().getResource()) {
                        context.getLogger().info( String.format("Redis deletion: %s", key));

                            jedis.del(key);
                        } catch (Exception e) {
                        context.getLogger().warning(
                            String.format("Redis exception %s: %s", key, e.getMessage()));
                        }
                    }

                    container.deleteItem(item, new CosmosItemRequestOptions());
                } catch (Exception e) {
                    errs.add(e);
                    context.getLogger().warning(
                        String.format("Failed to delete item with class |%s| "
                                          + "from container |%s|.",
                                      clazz, container));
                }
            });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Stats {
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

    static class Short {
        private String id; // TODO explain why this is here for CosmosDB
        String ownerId;
        String blobUrl;
        long timestamp;
        int totalLikes;

        public Short() {}

        public Short(String shortId, String ownerId, String blobUrl,
                     long timestamp, int totalLikes) {
            super();
            this.id = shortId;
            this.ownerId = ownerId;
            this.blobUrl = blobUrl;
            this.timestamp = timestamp;
            this.totalLikes = totalLikes;
        }

        public Short(String shortId, String ownerId, String blobUrl) {
            this(shortId, ownerId, blobUrl, System.currentTimeMillis(), 0);
        }

        public String getId() { return id; }

        public void setId(String id) { this.id = id; }

        public String getShortId() { return id; }

        public void setShortId(String shortId) { this.id = shortId; }

        public String getOwnerId() { return ownerId; }

        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

        public String getBlobUrl() { return blobUrl; }

        public void setBlobUrl(String blobUrl) { this.blobUrl = blobUrl; }

        public long getTimestamp() { return timestamp; }

        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public int getTotalLikes() { return totalLikes; }

        public void setTotalLikes(int totalLikes) {
            this.totalLikes = totalLikes;
        }

        @Override
        public String toString() {
            return "Short [id=" + id + ", ownerId=" + ownerId +
                ", blobUrl=" + blobUrl + ", timestamp=" + timestamp +
                ", totalLikes=" + totalLikes + "]";
        }
    }

    static class Following {
        String id; // for Cosmos NoSQL

        String follower;

        String followee;

        Following() {}

        public Following(String follower, String followee) {
            super();
            this.id = follower + "_" + followee;
            this.follower = follower;
            this.followee = followee;
        }

        public String getFollower() { return follower; }

        public void setFollower(String follower) { this.follower = follower; }

        public String getFollowee() { return followee; }

        public void setFollowee(String followee) { this.followee = followee; }

        public String getId() { return id; }

        public void setId(String id) { this.id = id; }

        @Override
        public int hashCode() {
            return Objects.hash(followee, follower);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Following other = (Following)obj;
            return Objects.equals(followee, other.followee) &&
                Objects.equals(follower, other.follower);
        }

        @Override
        public String toString() {
            return "Following [id=" + id + ", follower=" + follower +
                ", followee=" + followee + "]";
        }
    }

    static class Likes {
        String id; // for Cosmos NoSQL

        String userId;

        String shortId;

        String ownerId;

        public Likes() {}

        public Likes(String userId, String shortId, String ownerId) {
            this.id = userId + "_" + shortId;
            this.userId = userId;
            this.shortId = shortId;
            this.ownerId = ownerId;
        }

        public String getUserId() { return userId; }

        public void setUserId(String userId) { this.userId = userId; }

        public String getShortId() { return shortId; }

        public void setShortId(String shortId) { this.shortId = shortId; }

        public String getOwnerId() { return ownerId; }

        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

        public String getId() { return id; }

        public void setId(String id) { this.id = id; }

        @Override
        public String toString() {
            return "Likes [id=" + id + ", userId=" + userId +
                ", shortId=" + shortId + ", ownerId=" + ownerId + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerId, shortId, userId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Likes other = (Likes)obj;
            return Objects.equals(ownerId, other.ownerId) &&
                Objects.equals(shortId, other.shortId) &&
                Objects.equals(userId, other.userId);
        }
    }
}
