package tukano.serverless;

import java.util.Optional;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import tukano.data.Following;

public class FollowTukRecsHTTPTrigger {
    private static final String SHORTS_FUNCTION_NAME = "followTukRecsHTTP";

    private static final String COSMOS_ENDPOINT = System.getenv("COSMOSDB_URL");
    private static final String COSMOS_KEY = System.getenv("COSMOSDB_KEY");
    private static final String DATABASE_NAME =
        System.getenv("COSMOSDB_DATABASE");
    public static final String FOLLOWING_CONTAINER = "following";
    private static final String USER_ID = "userId";

    @FunctionName(SHORTS_FUNCTION_NAME)
    public void
    updateShortViews(@HttpTrigger(name = "req", methods = {HttpMethod.GET},
                                  route = "tukRecs/{" + USER_ID + "}",
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
            CosmosContainer followingContainer =
                db.getContainer(FOLLOWING_CONTAINER);
            
            var f = new Following(userId, "tukRecs");
            followingContainer.createItem(f).getItem();

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

}
