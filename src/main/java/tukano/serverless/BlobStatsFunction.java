package tukano.serverless;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

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
            System.out.println("blobname: " + blobname);

            /*String shortId = blobname.split("\\.")[0]; // Assume que o nome é "123.mp4"

            // Tentar ler o documento de estatísticas existente
            JSONObject statsDoc;
            try {
                String json = statsContainer.readItem(shortId, new PartitionKey(shortId), String.class)
                        .getItem();
                statsDoc = new JSONObject(json);
            } catch (Exception e) {
                // Se não existir, criar novo documento
                statsDoc = new JSONObject()
                        .put("id", shortId)
                        .put("viewCount", 0)
                        .put("createdAt", java.time.Instant.now().toString());
            }

            // Incrementar viewCount
            int currentViews = statsDoc.optInt("viewCount", 0);
            statsDoc.put("viewCount", currentViews + 1)
                    .put("lastUpdated", java.time.Instant.now().toString());

            // Atualizar ou criar documento
            statsContainer.upsertItem(statsDoc.toString());

            context.getLogger().info("Successfully updated view count for short: " + shortId);*/

        } catch (Exception e) {
            context.getLogger().severe("Error updating view count: " + e.getMessage());
            throw new RuntimeException("Failed to update view count", e);
        }
    }
}