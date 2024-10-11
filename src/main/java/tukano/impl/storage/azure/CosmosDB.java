package tukano.impl.storage.azure;

import java.util.List;
import java.util.function.Supplier;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;

import tukano.api.Result.*;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Short;

public class CosmosDB {
    private static final String CONNECTION_URL = System.getenv("DB_URI");
    private static final String DB_KEY = System.getenv("DB_PK");
    private static final String DB_NAME = "scc-70274-cosmos";
    private static final String USERS_CONTAINER = "users";
    private static final String SHORTS_CONTAINER = "shorts";

    private static CosmosDB instance;

    public static synchronized CosmosDB getInstance() {
        if (instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(CONNECTION_URL)
                .key(DB_KEY)
                .directMode() // DOES NOT work on eduroam TODO replace for final submission
                // .gatewayMode()
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

    public CosmosDB(CosmosClient client) {
        this.client = client;
    }

    private synchronized void init() {
        if (db != null)
            return;
        db = client.getDatabase(DB_NAME);
        // container = db.getContainer(CONTAINER);
    }

    public void close() {
        client.close();
    }

    public <T> Result<T> getOne(String id, Class<T> clazz) {
        return tryCatch(() -> getContainer(clazz).readItem(id, new PartitionKey(id), clazz).getItem());
    }

    public <T> Result<?> deleteOne(T obj) {
        return tryCatch(() -> getContainer(obj).deleteItem(obj, new CosmosItemRequestOptions()).getItem());
    }

    public <T> Result<T> updateOne(T obj) {
        return tryCatch(() -> getContainer(obj).upsertItem(obj).getItem());
    }

    public <T> Result<T> insertOne(T obj) {
        return tryCatch(() -> getContainer(obj).createItem(obj).getItem());
    }

    public <T> Result<List<T>> query(Class<T> clazz, String queryStr) {
        return tryCatch(() -> {
            var res = getContainer(clazz).queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            init();
            return Result.ok(supplierFunc.get());
        } catch (CosmosException ce) {
            // ce.printStackTrace();
            return Result.error(errorCodeFromStatus(ce.getStatusCode()));
        } catch (Exception x) {
            x.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> CosmosContainer getContainer(T obj) {
        // TODO do we prefer this instead of overloading the method?
        if (obj instanceof User || obj.equals(User.class)) {
            // avoids redundant calls
            if (container != null && container.getId().equals(USERS_CONTAINER)) {
                return container;
            }

            container = db.getContainer(USERS_CONTAINER);
            return container;
        } else /* if (obj instanceof Short || obj.equals(Short.class)) */ {
            if (container != null && container.getId().equals(SHORTS_CONTAINER)) {
                return container;
            }
            container = db.getContainer(SHORTS_CONTAINER);
            return container;
        }

        // System.out.println("\n\nUnexpected class in CosmosDB: " + obj.getClass());
        // return container;
    }

    // private <T> CosmosContainer getContainer(Class<T> clazz) {
    // if (clazz.equals(User.class)) {
    // if(container.getId().equals(USERS_CONTAINER)) {
    // return container;
    // }
    //
    // return db.getContainer(USERS_CONTAINER);
    // } else /* if (clazz.equals(Short.class)) */ {
    // if(container.getId().equals(SHORTS_CONTAINER)) {
    // return container;
    // }
    // return db.getContainer(SHORTS_CONTAINER);
    // }
    // }

    static Result.ErrorCode errorCodeFromStatus(int status) {
        return switch (status) {
            case 200 -> ErrorCode.OK;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
