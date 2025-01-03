package tukano.serverless;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.NotFoundException;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.PartitionKey;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

import tukano.data.Short;
import tukano.data.Stats;

public class ShortStatsHTTPTrigger {
    private static final String SHORTS_FUNCTION_NAME = "updateShortViewsHTTP";

    private static final String COSMOS_ENDPOINT = System.getenv("COSMOSDB_URL");
    private static final String COSMOS_KEY = System.getenv("COSMOSDB_KEY");
    private static final String DATABASE_NAME = System.getenv("COSMOSDB_DATABASE");
    private static final String SHORTS_CONTAINER = "shorts";
    private static final String STATS_CONTAINER = "stats";
    private static final String BLOB_ID = "blobId";

    @FunctionName(SHORTS_FUNCTION_NAME)
    public void run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    route = "blobs/{" + BLOB_ID + "}",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName(BLOB_ID) String blobId,
            final ExecutionContext context) {

        CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint(COSMOS_ENDPOINT)
                .key(COSMOS_KEY)
                .directMode()
                .buildClient();

        try {
            CosmosDatabase db = cosmosClient.getDatabase(DATABASE_NAME);
            CosmosContainer shortsContainer = db.getContainer(SHORTS_CONTAINER);
            CosmosContainer statsContainer = db.getContainer(STATS_CONTAINER);

            context.getLogger().info(String.format("SHORT_STATS: blob : %s", blobId));

            var shortBlob = shortsContainer
                    .readItem(blobId, new PartitionKey(blobId), Short.class)
                    .getItem();

            if (shortBlob == null) {
                context.getLogger().info(String.format("Blob %s not found", blobId));
                return;
            }

            try {
                var currentStats = statsContainer
                        .readItem(blobId, new PartitionKey(blobId), Stats.class)
                        .getItem();

                context.getLogger().info(String.format("Current views for %s: %d", blobId, currentStats.getViews()));

                CosmosPatchOperations patchOperations = CosmosPatchOperations.create()
                        .increment("/views", 1);

                statsContainer.patchItem(
                        blobId,
                        new PartitionKey(blobId),
                        patchOperations,
                        Stats.class);

                context.getLogger().info(String.format("Updated views for %s through patch operation", blobId));

            } catch (NotFoundException e) {
                Stats newStats = new Stats(blobId);
                statsContainer.createItem(newStats);
                context.getLogger().info(String.format("Created new stats for %s with 1 view", blobId));
            }

        } catch (CosmosException ce) {
            context.getLogger().severe("CosmosException: " + ce.getMessage());
        } catch (Exception e) {
            context.getLogger().severe("Error updating view count: " + e.getMessage());
        } finally {
            if (cosmosClient != null) {
                cosmosClient.close();
            }
        }
    }
}
