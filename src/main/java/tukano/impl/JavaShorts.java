package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.ErrorCode.NOT_IMPLEMENTED;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static utils.DB.getOne;
import static utils.DB.usingHibernate;

import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.azure.CosmosDB;
import utils.DB;
import utils.JSON;

public class JavaShorts implements Shorts {

    private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
    private static String triggerFunctionEndpoint =
        System.getProperty("USERS_TRIGGER_FUNC_URL");

    private static Shorts instance;

    synchronized public static Shorts getInstance() {
        if (instance == null)
            instance = new JavaShorts();
        return instance;
    }

    private JavaShorts() {}

    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(()
                     -> format("createShort : userId = %s, pwd = %s\n", userId,
                               password));

        return errorOrResult(okUser(userId, password), user -> {
            var shortId = format("%s+%s", userId, UUID.randomUUID());
            var blobUrl =
                format("%s/%s/%s", Blobs.STORAGE_ENDPOINT, Blobs.NAME, shortId);
            var shrt = new Short(shortId, userId, blobUrl);

            return errorOrValue(DB.insertOne(shrt),
                                s -> s.copyWithLikes_Views_And_Token(0, 0));
        });
    }

    @Override
    public Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if (shortId == null)
            return error(BAD_REQUEST);

        String likesQuery;
        String viewsQuery =
            format("SELECT * FROM Stats s WHERE s.id = '%s'", shortId);

        if (DB.usingHibernate) {
            likesQuery = format(
                "SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
        } else {
            likesQuery = format(
                "SELECT VALUE COUNT(1) FROM Likes l WHERE l.shortId = '%s'",
                shortId);
        }

        var likes = DB.sql(CosmosDB.LIKES_CONTAINER, likesQuery, Long.class);
        var viewsRes =
            DB.sql(CosmosDB.STATS_CONTAINER, viewsQuery, JsonNode.class);
        var views =
            viewsRes.isEmpty() ? 0 : viewsRes.get(0).get("views").asInt();
        return errorOrValue(
            getOne(shortId, Short.class),
            shrt -> shrt.copyWithLikes_Views_And_Token(likes.get(0), views));
    }

    @Override

    public Result<Void> deleteShort(String shortId, String password) {
        Log.info(()
                     -> format("deleteShort : shortId = %s, pwd = %s\n",
                               shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {
            return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
                if (DB.usingHibernate) {
                    return DB.transaction(hibernate -> {
                        hibernate.remove(shrt);

                        var query = format(
                            "DELETE FROM likes WHERE shortId = '%s'", shortId);
                        hibernate.createNativeQuery(query, Likes.class)
                            .executeUpdate();

                        JavaBlobs.getInstance().delete(shrt.getBlobUrl(),
                                                       Token.get());
                    });
                } else {
                    var query = "SELECT * FROM c WHERE ENDSWITH(c.id, '_" +
                                shortId + "')";
                    var res =
                        DB.sql(CosmosDB.LIKES_CONTAINER, query, JsonNode.class);

                    for (var like : res) {
                        String likeId = like.get("id").asText();
                        String userId =
                            likeId.substring(0, likeId.indexOf('_'));

                        var l = new Likes(userId, shortId, shrt.getOwnerId());
                        var deleteLikeResult =
                            CosmosDB.getInstance().deleteOne(l);
                        if (deleteLikeResult.error() != ErrorCode.OK) {
                            return error(deleteLikeResult.error());
                        }
                    }

                    var deleteShortResult =
                        CosmosDB.getInstance().deleteOne(shrt);
                    if (deleteShortResult.error() != ErrorCode.OK) {
                        return error(deleteShortResult.error());
                    }

                    JavaBlobs.getInstance().delete(shrt.getBlobUrl(),
                                                   Token.get());
                    return Result.ok();
                }
            });
        });
    }

    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));
        var query =
            format("SELECT s.id FROM Shorts s WHERE s.ownerId = '%s'", userId);

        if (DB.usingHibernate) {
            return errorOrValue(
                okUser(userId),
                DB.sql(CosmosDB.SHORTS_CONTAINER, query, String.class));
        } else {
            return errorOrValue(
                okUser(userId),
                DB.sql(CosmosDB.SHORTS_CONTAINER, query, JsonNode.class)
                    .stream()
                    .map(jsonNode -> jsonNode.get("id").asText())
                    .toList());
        }
    }

    @Override
    public Result<Void> follow(String userId1, String userId2,
                               boolean isFollowing, String password) {
        Log.info(()
                     -> format("follow : userId1 = %s, userId2 = %s, "
                                   + "isFollowing = %s, pwd = %s\n",
                               userId1, userId2, isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            return errorOrVoid(okUser(userId2),
                               isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
        });
    }

    @Override
    public Result<List<String>> followers(String userId, String password) {
        Log.info(()
                     -> format("followers : userId = %s, pwd = %s\n", userId,
                               password));

        var query =
            format("SELECT f.follower FROM Following f WHERE f.followee = '%s'",
                   userId);

        if (DB.usingHibernate) {
            return errorOrValue(
                okUser(userId),
                DB.sql(CosmosDB.FOLLOWING_CONTAINER, query, String.class));
        } else {
            return errorOrValue(
                okUser(userId),
                DB.sql(CosmosDB.FOLLOWING_CONTAINER, query, JsonNode.class)
                    .stream()
                    .map(jsonNode -> jsonNode.get("follower").asText())
                    .toList());
        }
    }

    private Result<List<String>> following(String userId, String password) {
        Log.info(()
                     -> format("following : userId = %s, pwd = %s\n", userId,
                               password));

        var query =
            format("SELECT f.followee FROM Following f WHERE f.follower = '%s'",
                   userId);

        if (DB.usingHibernate) {
            return errorOrValue(
                okUser(userId),
                DB.sql(CosmosDB.FOLLOWING_CONTAINER, query, String.class));
        } else {
            return errorOrValue(
                okUser(userId),
                DB.sql(CosmosDB.FOLLOWING_CONTAINER, query, JsonNode.class)
                    .stream()
                    .map(jsonNode -> jsonNode.get("followee").asText())
                    .toList());
        }
    }

    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked,
                             String password) {
        Log.info(()
                     -> format("like : shortId = %s, userId = %s, isLiked = "
                                   + "%s, pwd = %s\n",
                               shortId, userId, isLiked, password));

        return errorOrResult(getShort(shortId), shrt -> {
            var l = new Likes(userId, shortId, shrt.getOwnerId());
            return errorOrVoid(okUser(userId, password),
                               isLiked ? DB.insertOne(l) : DB.deleteOne(l));
        });
    }

    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(()
                     -> format("likes : shortId = %s, pwd = %s\n", shortId,
                               password));

        var query = format(
            "SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);
        if (DB.usingHibernate) {
            return errorOrResult(getShort(shortId), shrt -> {
                return errorOrValue(
                    okUser(shrt.getOwnerId(), password),
                    DB.sql(CosmosDB.LIKES_CONTAINER, query, String.class));
            });
        } else {
            return errorOrResult(getShort(shortId), shrt -> {
                return errorOrValue(
                    okUser(shrt.getOwnerId(), password),
                    DB.sql(CosmosDB.LIKES_CONTAINER, query, JsonNode.class)
                        .stream()
                        .map(jsonNode -> jsonNode.get("userId").asText())
                        .toList());
            });
        }
    }

    @Override
    public Result<List<String>> getFeed(String userId, String password) {
        Log.info(()
                     -> format("getFeed : userId = %s, pwd = %s\n", userId,
                               password));

        if (usingHibernate) {
            final var QUERY_FMT = """
                    SELECT s.id, s.timestamp FROM Shorts s WHERE s.ownerId = '%s'
                    UNION			
                    SELECT s.id, s.timestamp FROM Shorts s
                        JOIN Following f ON f.followee = s.ownerId
                            WHERE f.follower = '%s' 
                    ORDER BY timestamp DESC""";

            return errorOrValue(okUser(userId),
                                DB.sql(CosmosDB.SHORTS_CONTAINER,
                                       format(QUERY_FMT, userId, userId),
                                       String.class));
        } else {
            return errorOrValue(okUser(userId, password), user -> {
                List<String> usersToFetch = new ArrayList<>();
                usersToFetch.add(userId);
                usersToFetch.addAll(following(userId, password).value());

                var query = format("SELECT * FROM c WHERE c.ownerId IN (%s) " +
                                   "ORDER BY c.timestamp DESC",
                                   usersToFetch.stream()
                                       .map(f -> "'" + f + "'")
                                       .collect(Collectors.joining(",")));

                return DB.sql(CosmosDB.SHORTS_CONTAINER, query, JsonNode.class)
                    .stream()
                    .map(jsonNode -> jsonNode.get("id").asText())
                    .toList();
            });
        }
    }

    protected Result<User> okUser(String userId, String pwd) {
        return JavaUsers.getInstance().getUser(userId, pwd);
    }

    private Result<Void> okUser(String userId) {
        var res = okUser(userId, "");
        if (res.error() == FORBIDDEN)
            return ok();
        else
            return error(res.error());
    }

    @Override
    public Result<Void> deleteAllShorts(String userId, String password,
                                        String token) {
        Log.info(()
                     -> format("deleteAllShorts : userId = %s, password = "
                                   + "%s, token = %s\n",
                               userId, password, token));

        if (!Token.isValid(token, userId))
            return error(FORBIDDEN);

        if (usingHibernate) {
            return DB.transaction((hibernate) -> {
                // delete shorts
                var query1 =
                    format("DELETE FROM Shorts WHERE ownerId = '%s'", userId);
                hibernate.createQuery(query1, Short.class).executeUpdate();

                // delete follows
                var query2 =
                    format("DELETE FROM Following f WHERE follower = '%s' "
                               + "OR followee = '%s'",
                           userId, userId);
                hibernate.createQuery(query2, Following.class).executeUpdate();

                // delete likes
                var query3 = format(
                    "DELETE FROM Likes WHERE ownerId = '%s' OR userId = '%s'",
                    userId, userId);
                hibernate.createQuery(query3, Likes.class).executeUpdate();
            });
        } else {
            triggerFunction(userId);

            return Result.ok();
        }
    }

    private void triggerFunction(String userId) {
        HttpClient.newHttpClient().sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create(triggerFunctionEndpoint + userId))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding());
    }
}
