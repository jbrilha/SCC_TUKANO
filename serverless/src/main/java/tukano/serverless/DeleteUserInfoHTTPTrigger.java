package tukano.serverless;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import tukano.data.Following;
import tukano.data.Likes;
import tukano.data.Short;

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
    private static CosmosDatabase db;

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
            db = cosmosClient.getDatabase(DATABASE_NAME);
            CosmosContainer shortsContainer = db.getContainer(SHORTS_CONTAINER);
            CosmosContainer followingContainer =
                db.getContainer(FOLLOWING_CONTAINER);
            CosmosContainer likesContainer = db.getContainer(LIKES_CONTAINER);

            List<Exception> errs = new ArrayList<>();
            Stack<String> shortIds = new Stack<>();

            var shortsQuery = String.format(
                "SELECT * FROM Shorts s WHERE s.ownerId = '%s'", userId);
            deleteFromContainer(context, shortsContainer, shortsQuery,
                                Short.class, shortIds, errs);
            clearShortsInfo(context, shortIds);

            var followingQuery = String.format(
                "SELECT * FROM Following f WHERE f.follower = '%s' "
                    + "OR f.followee = '%s'",
                userId, userId);
            deleteFromContainer(context, followingContainer, followingQuery,
                                Following.class, shortIds, errs);

            var likesQuery =
                String.format("SELECT * FROM Likes l WHERE l.ownerId = '%s' "
                                  + "OR l.userId = '%s'",
                              userId, userId);
            deleteFromContainer(context, likesContainer, likesQuery,
                                Likes.class, shortIds, errs);

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

    private void clearShortsInfo(ExecutionContext context, Stack<String> ids) {
        CosmosContainer statsContainer = db.getContainer(STATS_CONTAINER);
        while(!ids.isEmpty()) {
            String shortId = ids.pop();
            String key = "short:" + shortId;

            statsContainer.deleteItem(shortId, new PartitionKey(shortId), new CosmosItemRequestOptions());

            try (var jedis = getCachePool().getResource()) {
                context.getLogger().info(
                    String.format("Redis deletion: %s", key));

                jedis.del(key);
            } catch (Exception e) {
                context.getLogger().warning(String.format(
                    "Redis exception %s: %s", key, e.getMessage()));
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
                                         Stack<String> shortIds,
                                         List<Exception> errs) {
        container.queryItems(query, new CosmosQueryRequestOptions(), clazz)
            .forEach(item -> {
                try {
                    if (container.getId().equals(SHORTS_CONTAINER)) {
                        Short s = (Short)item;
                        shortIds.push(s.getId());
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
}
