package utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.Session;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.Short;
import tukano.api.Result.ErrorCode;
import tukano.impl.storage.azure.CosmosDB;
import tukano.impl.cache.RedisCache;

public class DB {

    public static <T> List<T> sql(String query, Class<T> clazz) {
        return Hibernate.getInstance().sql(query, clazz);
    }

    public static <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
        return Hibernate.getInstance().sql(String.format(fmt, args), clazz);
    }

    public static <T> Result<T> getOne(String id, Class<T> clazz) {
        String[] classSplit = clazz.toString().toLowerCase().split("\\.");
        String className = classSplit[classSplit.length-1];
        // TODO this is kinda disgusting but avoids type checking so..? lmk

        String key = className + ":" + id;
        var res = RedisCache.getOne(key, clazz);

        if (res.isOK()) {
            System.out.println("Cached result: " + res.getClass() + " | " + res);
            return res;
        }

        System.out.println("Cache miss: " + key);
        return CosmosDB.getInstance().getOne(id, clazz);
        // return Hibernate.getInstance().getOne(id, clazz);
    }

    public static <T> Result<T> deleteOne(T obj) {
        String key = getObjectKey(obj);
        if (key != null)
            RedisCache.invalidate(key);

        return Result.errorOrValue(CosmosDB.getInstance().deleteOne(obj), obj);
        // return Hibernate.getInstance().deleteOne(obj);
    }

    public static <T> Result<T> updateOne(T obj) {
        String key = getObjectKey(obj);
        if (key != null)
            RedisCache.invalidate(key);

        return CosmosDB.getInstance().updateOne(obj);
        // return Hibernate.getInstance().updateOne(obj);
    }

    public static <T> Result<T> insertOne(T obj) {
        System.err.println("DB.insert:" + obj);

        String key = getObjectKey(obj);
        if (key != null)
            RedisCache.insertOne(key, obj);

        System.out.println("\n\nafter cache\n\n");
        var res = CosmosDB.getInstance().insertOne(obj);
        if (res.isOK()) {
            System.out.println("res is ok\n");
        } else {
            System.out.println("res is not ok\n");
        }
        return Result.errorOrValue(res, obj);
        // return Result.errorOrValue(Hibernate.getInstance().persistOne(obj), obj);
    }

    public static <T> Result<T> transaction(Consumer<Session> c) {
        return Hibernate.getInstance().execute(c::accept);
    }

    public static <T> Result<T> transaction(Function<Session, Result<T>> func) {
        return Hibernate.getInstance().execute(func);
    }

    private static <T> String getObjectKey(T obj) {
        String key = null;

        if (obj instanceof User user) {
            key = "user:" + user.getUserId();
        } else if (obj instanceof Short shrt) {
            key = "short:" + shrt.getShortId();
        }

        return key;
    }
}
