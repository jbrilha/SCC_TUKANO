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
	
	
	public static <T> List<T> sql(Class<T> clazz, String fmt, Object ... args) {
		return Hibernate.getInstance().sql(String.format(fmt, args), clazz);
	}
	
	
	public static <T> Result<T> getOne(String id, Class<T> clazz) {
        if(clazz.equals(User.class)) {
            var key = "user:" + id;
            var res = RedisCache.getOne(key, clazz);

            if(!res.isOK()) {
		        return CosmosDB.getInstance().getOne(id, clazz);
            } else {
                System.out.println("Cached result: " + res);
                return res;
            }
                
        }

		return Hibernate.getInstance().getOne(id, clazz);
	}
	
	public static <T> Result<T> deleteOne(T obj) {
        String key = null;

        if(obj instanceof User user) {
            key = "user:" + user.getUserId();
        } else if(obj instanceof Short shrt) {
            key = "short:" + shrt.getShortId();
        }
        if(key != null)
            RedisCache.invalidate(key);

		return Hibernate.getInstance().deleteOne(obj);
	}
	
	public static <T> Result<T> updateOne(T obj) {
        String key = null;

        if(obj instanceof User user) {
            key = "user:" + user.getUserId();
        } else if(obj instanceof Short shrt) {
            key = "short:" + shrt.getShortId();
        }
        if(key != null)
            RedisCache.invalidate(key);

		return Hibernate.getInstance().updateOne(obj);
	}
	
	public static <T> Result<T> insertOne( T obj) {
		System.err.println("DB.insert:" + obj );

        if(obj instanceof User user) {
            var key = "user:" + user.getUserId();

            RedisCache.insertOne(key, obj);

		    return Result.errorOrValue(CosmosDB.getInstance().insertOne(obj), obj);
        }
		return Result.errorOrValue(Hibernate.getInstance().persistOne(obj), obj);
	}
	
	public static <T> Result<T> transaction( Consumer<Session> c) {
		return Hibernate.getInstance().execute( c::accept );
	}
	
	public static <T> Result<T> transaction( Function<Session, Result<T>> func) {
		return Hibernate.getInstance().execute( func );
	}
}
