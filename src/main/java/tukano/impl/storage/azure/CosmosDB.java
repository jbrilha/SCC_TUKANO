package tukano.impl.storage.azure;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import tukano.api.Result;
import tukano.api.Result.*;
import tukano.api.Short;
import tukano.api.User;

public class CosmosDB {
    private static final String CONNECTION_URL =
        System.getProperty("COSMOSDB_URL");
    private static final String DB_KEY = System.getProperty("COSMOSDB_KEY");
    private static final String DB_NAME =
        System.getProperty("COSMOSDB_DATABASE");
    public static final String USERS_CONTAINER = "users";
    public static final String SHORTS_CONTAINER = "shorts";
    public static final String LIKES_CONTAINER = "likes";
    public static final String FOLLOWING_CONTAINER = "following";

    private static final Map<String, String> containerMap = Map.of(
        "User", USERS_CONTAINER,
        "Short", SHORTS_CONTAINER,
        "Following", FOLLOWING_CONTAINER,
        "Likes", LIKES_CONTAINER
    );

    private static CosmosDB instance;

    public static synchronized CosmosDB getInstance() {
        if (instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                                  .endpoint(CONNECTION_URL)
                                  .key(DB_KEY)
                                  // .directMode()
                                  .gatewayMode()
                                  .consistencyLevel(ConsistencyLevel.SESSION)
                                  .connectionSharingAcrossClientsEnabled(true)
                                  .contentResponseOnWriteEnabled(true)
                                  .buildClient();
        instance = new CosmosDB(client);
        return instance;
    }

    private CosmosClient client;
    private CosmosDatabase db;
    private CosmosContainer container;

    public CosmosDB(CosmosClient client) { this.client = client; }

    private synchronized void init() {
        if (db != null)
            return;
        db = client.getDatabase(DB_NAME);
        container = db.getContainer(USERS_CONTAINER);
    }

    public void close() { client.close(); }

    public <T> Result<T> getOne(String id, Class<T> clazz) {
        return tryCatch(()
                            -> getContainer(clazz)
                                   .readItem(id, new PartitionKey(id), clazz)
                                   .getItem());
    }

    public <T> Result<?> deleteOne(T obj) {
        return tryCatch(
            ()
                -> getContainer(obj)
                       .deleteItem(obj, new CosmosItemRequestOptions())
                       .getItem());
    }

    public <T> Result<T> updateOne(T obj) {
        return tryCatch(() -> getContainer(obj).upsertItem(obj).getItem());
    }

    public <T> Result<T> insertOne(T obj) {
        return tryCatch(() -> getContainer(obj).createItem(obj).getItem());
    }

    public <T> Result<List<T>> query(String containerName, String queryStr, Class<T> clazz) {
        return tryCatch(() -> {
            var res = getContainer(containerName).queryItems(
                queryStr, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            init();
            return Result.ok(supplierFunc.get());
        } catch (CosmosException ce) {
            System.out.println("\n\nCosmos Exception:");
            ce.printStackTrace();
            return Result.error(errorCodeFromStatus(ce.getStatusCode()));
        } catch (Exception x) {
            x.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> CosmosContainer getContainer(T obj) {
        if (db == null)
            init();

        String containerName = "";
        if(obj instanceof Class clazz) {
            System.out.println("'tis a class: " + clazz);
            containerName = containerMap.get(clazz.getSimpleName());
        } else {
            System.out.println("'tis an object: " + obj.getClass().getSimpleName());
            containerName = containerMap.get(obj.getClass().getSimpleName());
        }

        if (container != null &&
            container.getId().equals(containerName)) {
            return container;
        }

        container = db.getContainer(containerName);
        return container;
    }

    private <T> CosmosContainer getContainer(String containerName) {
        if (db == null)
            init();

        if (container != null &&
            container.getId().equals(containerName)) {
            return container;
        }

        container = db.getContainer(containerName);
        return container;
    }

    static Result.ErrorCode errorCodeFromStatus(int status) {
        return switch (status) {
            case 200 -> ErrorCode.OK;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
