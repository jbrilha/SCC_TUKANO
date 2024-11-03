package tukano.serverless;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.NotFoundException;
import com.azure.cosmos.models.PartitionKey;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import java.util.Objects;

public class ShortStatsFunction {
    private static final String BLOB_ID = "blobId";
    private static final String PATH = "shorts/{" + BLOB_ID + "}";
    private static final String BLOBS_TRIGGER_NAME =
        "shortStatsFunctionTrigger";
    private static final String SHORTS_FUNCTION_NAME = "updateShortViews";
    private static final String DATA_TYPE = "binary";
    private static final String BLOBSTORE_CONNECTION_ENV =
        "BlobStoreConnection";

    private static final String COSMOS_ENDPOINT = System.getenv("COSMOSDB_URL");
    private static final String COSMOS_KEY = System.getenv("COSMOSDB_KEY");
    private static final String DATABASE_NAME =
        System.getenv("COSMOSDB_DATABASE");
    private static final String STATS_CONTAINER = "stats";

    @FunctionName(SHORTS_FUNCTION_NAME)
    public void updateShortViews(
        @BlobTrigger(name = BLOBS_TRIGGER_NAME,
                    dataType = DATA_TYPE,
                     path = PATH,
                     connection = BLOBSTORE_CONNECTION_ENV) byte[] content,
        @BindingName(BLOB_ID) String blobId, ExecutionContext context) {

        CosmosClient cosmosClient = new CosmosClientBuilder()
                                        .endpoint(COSMOS_ENDPOINT)
                                        .key(COSMOS_KEY)
                                        .buildClient();

        CosmosDatabase db = cosmosClient.getDatabase(DATABASE_NAME);
        CosmosContainer container = db.getContainer(STATS_CONTAINER);

        context.getLogger().info(
            String.format("SHORT_STATS: blob : %s, downloaded with %d bytes\n",
                          blobId, content.length));

        try {
            var item = container
                    .readItem(blobId, new PartitionKey(blobId), Stats.class)
                    .getItem();

            Stats updatedStats = item;
            updatedStats.setViews(item.getViews() + 1);

            container.upsertItem(updatedStats);

            context.getLogger().info("Updated stats for: " + blobId + " | views: " + updatedStats.getViews());
        } catch (NotFoundException e) {
            try {
                Stats stats = new Stats(blobId);

                container.createItem(stats);
                context.getLogger().info("Inserted document");

            } catch (CosmosException ce) {
                context.getLogger().severe("CosmosException: ");
                ce.printStackTrace();
            }
        } catch (Exception e) {
            context.getLogger().severe("Error updating view count: " +
                                       e.getMessage());
        }
    }

    private class Stats {
        String id; // for Cosmos NoSQL
        int views;

        public Stats(String blobId) {
            this.id = blobId;
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
            return Objects.equals(id, other.id) &&
                Objects.equals(views, other.views);
        }
    }
}
