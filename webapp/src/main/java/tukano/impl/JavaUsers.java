package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.storage.azure.CosmosDB;
import utils.DB;

public class JavaUsers implements Users {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
    private static String triggerFunctionEndpoint =
        System.getProperty("TUKRECS_TRIGGER_FUNC_URL");

    private static Users instance;

    synchronized public static Users getInstance() {
        if (instance == null)
            instance = new JavaUsers();
        return instance;
    }

    private JavaUsers() {}

    @Override
    public Result<String> createUser(User user) {
        Log.info(() -> format("createUser : %s\n", user));

        if (badUserInfo(user))
            return error(BAD_REQUEST);

        return errorOrValue(DB.insertOne(user), u -> {
            String userId = u.getUserId();
            triggerFunction(userId);
            return userId;
        });
    }

    private void triggerFunction(String blobId) {
        HttpClient.newHttpClient().sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create(triggerFunctionEndpoint + blobId))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding());
    }

    @Override
    public Result<User> getUser(String userId, String pwd) {
        Log.info(
                () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

        if (userId == null)
            return error(BAD_REQUEST);

        return validatedUserOrError(DB.getOne(userId, User.class), pwd);
    }

    @Override
    public Result<User> updateUser(String userId, String pwd, User other) {
        Log.info(()
                -> format("updateUser : userId = %s, pwd = %s, user: %s\n",
                userId, pwd, other));

        if (badUpdateUserInfo(userId, pwd, other))
            return error(BAD_REQUEST);

        return errorOrResult(
                validatedUserOrError(DB.getOne(userId, User.class), pwd),
                user -> DB.updateOne(user.updateFrom(other)));
    }

    @Override
    public Result<User> deleteUser(String userId, String pwd) {
        Log.info(
                () -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

        if (userId == null || pwd == null)
            return error(BAD_REQUEST);

        return errorOrResult(
                validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
                    // Delete user shorts and related info asynchronously in a
                    // separate thread
                    Executors.defaultThreadFactory()
                            .newThread(() -> {
                                JavaShorts.getInstance().deleteAllShorts(
                                        userId, pwd, Token.get(userId));
                                JavaBlobs.getInstance().deleteAllBlobs(
                                        userId, Token.get(userId));
                            })
                            .start();

                    return DB.deleteOne(user);
                });
    }

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

        String query;
        if (DB.usingHibernate) {
            query = format("SELECT * FROM Users u WHERE UPPER(u.id) LIKE '%%%s%%'",
                    pattern.toUpperCase());
        } else {
            query = format("SELECT * FROM Users u WHERE CONTAINS(UPPER(u.id), '%s')",
                    pattern.toUpperCase());
        }
        var hits = DB.sql(CosmosDB.USERS_CONTAINER, query, User.class)
                .stream()
                .map(User::copyWithoutPassword)
                .toList();

        return ok(hits);
    }

    private Result<User> validatedUserOrError(Result<User> res, String pwd) {
        if (res.isOK())
            return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
        else
            return res;
    }

    private boolean badUserInfo(User user) {
        return (user.userId() == null || user.pwd() == null ||
                user.displayName() == null || user.email() == null);
    }

    private boolean badUpdateUserInfo(String userId, String pwd, User info) {
        return (userId == null || pwd == null ||
                info.getUserId() != null && !userId.equals(info.getUserId()));
    }
}
