package tukano.serverless;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.PartitionKey;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import org.json.JSONObject;

public class BlobStatsFunction {
    private static final String NAME = "name";
    private static final String PATH = "shorts/{" + NAME + "}";
    private static final String BLOBS_TRIGGER_NAME = "blobStatsFunctionTrigger";
    private static final String BLOBS_FUNCTION_NAME = "blobStatsFunctionExample";
    private static final String DATA_TYPE = "binary";
    private static final String BLOBSTORE_CONNECTION_ENV = "BlobStoreConnection";

    private static final String COSMOS_ENDPOINT = System.getenv("COSMOSDB_URL");
    private static final String COSMOS_KEY = System.getenv("COSMOSDB_KEY");
    private static final String DATABASE_NAME = System.getenv("COSMOSDB_DATABASE");
    private static final String CONTAINER_NAME = "stats";

    private final CosmosClient cosmosClient;
    private final CosmosContainer statsContainer;

    public BlobStatsFunction() {
        //this.cosmosClient = new CosmosClient(COSMOS_ENDPOINT, COSMOS_KEY);
        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(COSMOS_ENDPOINT)
                .key(COSMOS_KEY)
                .buildClient();
        CosmosDatabase database = cosmosClient.getDatabase(DATABASE_NAME);
        this.statsContainer = database.getContainer(CONTAINER_NAME);
    }

    @FunctionName(BLOBS_FUNCTION_NAME)
    public void blobFunctionExample(
            @BlobTrigger(
                    name = BLOBS_TRIGGER_NAME,
                    dataType = DATA_TYPE,
                    path = PATH,
                    connection = BLOBSTORE_CONNECTION_ENV
            ) byte[] content,
            @BindingName("name") String blobname,
            ExecutionContext context) {

        context.getLogger().info(String.format("blob : %s, downloaded with %d bytes", blobname, content.length));

        try {
            JSONObject statsDoc;
            try {
                String json = statsContainer.readItem(blobname, new PartitionKey(blobname), String.class).getItem();
                statsDoc = new JSONObject(json);
            } catch (Exception e) {
                statsDoc = new JSONObject()
                        .put("id", blobname)
                        .put("viewCount", 0)
                        .put("lastUpdated", java.time.Instant.now().toString());
            }

            context.getLogger().info("Stats document: " + statsDoc.toString());

            int currentViews = statsDoc.optInt("viewCount", 0);
            statsDoc.put("viewCount", currentViews + 1)
                    .put("lastUpdated", java.time.Instant.now().toString());

            statsContainer.upsertItem(statsDoc.toString());

            context.getLogger().info("Successfully updated view count for short: " + blobname);

        } catch (Exception e) {
            context.getLogger().severe("Error updating view count: " + e.getMessage());
            throw new RuntimeException("Failed to update view count", e);
        }
    }
}