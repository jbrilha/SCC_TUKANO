package utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.hibernate.Session;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.Short;
import tukano.api.User;
import tukano.impl.cache.RedisCache;
import tukano.impl.storage.azure.CosmosDB;

public class DB {

    // for testing purposes
    public static final boolean usingHibernate = false;

    public static <T> List<T> sql(String containerName, String query, Class<T> clazz) {
        if (usingHibernate) {
            return Hibernate.getInstance().sql(query, clazz);
        }

        var res = CosmosDB.getInstance().query(containerName, query, clazz);
        if (res.isOK()) {
            return res.value();
        }

        return Collections.emptyList();
    }

    public static <T> List<T> sql(String containerName, Class<T> clazz, String fmt, Object... args) {
        if (usingHibernate) {
            return Hibernate.getInstance().sql(String.format(fmt, args), clazz);
        }

        var res = CosmosDB.getInstance().query(containerName, String.format(fmt, args), clazz);
        if (res.isOK()) {
            return res.value();
        }

        return Collections.emptyList();
    }

    public static <T> Result<T> getOne(String id, Class<T> clazz) {
        String key = clazz.getSimpleName().toLowerCase() + ":" + id;
        var cacheRes = RedisCache.getOne(key, clazz);

        if (cacheRes.isOK()) {
            System.out.println("Cached result: " + cacheRes.value());
            return cacheRes;
        }

        System.out.println("Cache miss: " + key);
        Result<T> DBRes;
        if (usingHibernate) {
            DBRes =  Hibernate.getInstance().getOne(id, clazz);
        } else {
            DBRes =  CosmosDB.getInstance().getOne(id, clazz);
        }

        if (DBRes.isOK()) {
            RedisCache.insertOne(key, DBRes.value());
        }

        return DBRes;
    }

    public static <T> Result<T> deleteOne(T obj) {
        String key = getObjectKey(obj);
        if (key != null)
            RedisCache.invalidate(key);

        if (usingHibernate) {
            return Hibernate.getInstance().deleteOne(obj);
        }

        return Result.errorOrValue(CosmosDB.getInstance().deleteOne(obj), obj);
    }

    public static <T> Result<T> updateOne(T obj) {
        String key = getObjectKey(obj);
        if (key != null)
            RedisCache.insertOne(key, obj);

        if (usingHibernate) {
            return Hibernate.getInstance().updateOne(obj);
        }
        return CosmosDB.getInstance().updateOne(obj);
    }

    public static <T> Result<T> insertOne(T obj) {
        System.err.println("DB.insert:" + obj);

        String key = getObjectKey(obj);
        if (key != null)
            RedisCache.insertOne(key, obj);

        if (usingHibernate) {
            return Result.errorOrValue(Hibernate.getInstance().persistOne(obj),
                                       obj);
        }
        return Result.errorOrValue(CosmosDB.getInstance().insertOne(obj), obj);
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
