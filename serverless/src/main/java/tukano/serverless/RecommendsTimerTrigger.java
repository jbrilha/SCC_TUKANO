package tukano.serverless;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.NotFoundException;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecommendsTimerTrigger {
    private static final String TIMER_FUNCTION_NAME = "recommendsTimer";
    private static final String TIMER_TRIGGER_NAME = "recommendsTimer";
    private static final String TIMER_TRIGGER_SCHEDULE = "0 */5 * * * *";

    private static final String COSMOS_ENDPOINT = System.getenv("COSMOSDB_URL");
    private static final String COSMOS_KEY = System.getenv("COSMOSDB_KEY");
    private static final String DATABASE_NAME = System.getenv("COSMOSDB_DATABASE");

    private static final String LIKES_CONTAINER = "likes";
    private static final String STATS_CONTAINER = "stats";

    @FunctionName(TIMER_FUNCTION_NAME)
    public void run( @TimerTrigger(name = TIMER_TRIGGER_NAME, schedule = TIMER_TRIGGER_SCHEDULE)
                     String timerInfo,
                     ExecutionContext context) {

        CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint(COSMOS_ENDPOINT)
                .key(COSMOS_KEY)
                .buildClient();

        try {
            CosmosDatabase db = cosmosClient.getDatabase(DATABASE_NAME);
            if(db == null) {
                context.getLogger().severe("Failed to connect to database: " + DATABASE_NAME);
                return;
            }

            //CosmosContainer likesContainer = db.getContainer(LIKES_CONTAINER);
            //CosmosContainer statsContainer = db.getContainer(STATS_CONTAINER);
            CosmosContainer likesContainer = validateAndGetContainer(db, LIKES_CONTAINER, context);
            CosmosContainer statsContainer = validateAndGetContainer(db, STATS_CONTAINER, context);
            if (likesContainer == null || statsContainer == null) return;

            /*String likesQuery = "SELECT c.shortId, COUNT(1) as totalLikes FROM c GROUP BY c.shortId";
            var likesResult = likesContainer.queryItems(likesQuery, new CosmosQueryRequestOptions(), JsonNode.class);

            var topLikeShorts = likesResult.stream()
                .sorted((a, b) -> b.get("totalLikes").asInt() - a.get("totalLikes").asInt())
                .limit(10)
                .toList();*/

            List<JsonNode> topLikeShorts = getTopLikedShorts(likesContainer, context);
            if (topLikeShorts.isEmpty()) {
                context.getLogger().warning("No liked shorts found");
            }

            String viewsQuery = "SELECT TOP 10 * FROM c ORDER BY c.views DESC";
            var viewsResult = statsContainer.queryItems(viewsQuery, new CosmosQueryRequestOptions(), JsonNode.class);
            var topViewedShorts = viewsResult.stream().toList();

            var allRecommendedShorts = Stream.concat(
                    topViewedShorts.stream()
                            .map(node -> node.get("shortId").asText()),
                    topLikeShorts.stream()
                            .map(node -> node.get("shortId").asText())
            ).distinct().toList();

            context.getLogger().info("Recommend Shorts: " + allRecommendedShorts);
        } catch (CosmosException ce) {
            context.getLogger().severe("CosmosException: " + ce.getMessage());
        } catch (Exception e) {
            context.getLogger().severe("Exception: " + e.getMessage());
        } finally {
            if (cosmosClient != null) {
                cosmosClient.close();
            }
        }

        context.getLogger().info("Timer is triggered: " + timerInfo);
    }

    private CosmosContainer validateAndGetContainer(CosmosDatabase db, String containerName, ExecutionContext context) {
        try {
            return db.getContainer(containerName);
        } catch (CosmosException e) {
            context.getLogger().severe("Failed to connect to container: " + containerName + ". Error: " + e.getMessage());
            return null;
        }
    }

    private List<JsonNode> getTopLikedShorts(CosmosContainer container, ExecutionContext context) {
        try {
            String query = "SELECT c.shortId, COUNT(1) as totalLikes FROM c GROUP BY c.shortId";
            var result = container.queryItems(query, new CosmosQueryRequestOptions(), JsonNode.class);
            return result.stream()
                    .sorted((a, b) -> b.get("totalLikes").asInt() - a.get("totalLikes").asInt())
                    .limit(10)
                    .toList();
        } catch (Exception e) {
            context.getLogger().severe("Error querying likes: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}