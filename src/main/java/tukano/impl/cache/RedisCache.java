package tukano.impl.cache;

import java.util.function.Supplier;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import utils.*;

public class RedisCache {
    private static final String RedisHostname = System.getProperty("REDIS_URL");
    private static final String RedisKey = System.getProperty("REDIS_KEY");
    private static final int REDIS_PORT = 6380;
    private static final int REDIS_TIMEOUT = 3000;
    private static final boolean Redis_USE_TLS = true;

    private static final int SHORTS_EXPIRATION = 3600;
    private static final int USERS_EXPIRATION = 1800;

    private static JedisPool instance;

    public synchronized static JedisPool getCachePool() {
        if (instance != null)
            return instance;

        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT,
                                 REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
        return instance;
    }

    public static <T> Result<T> getOne(String key, Class<T> clazz) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            var obj = jedis.get(key);

            if (obj == null) {
                return Result.error(ErrorCode.NOT_FOUND);
            }

            return Result.ok(JSON.decode(obj, clazz));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    public static <T> Result<T> insertOne(String key, T obj) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            jedis.set(key, JSON.encode(obj));
            if (key.contains("short")) {
                jedis.expire(key, SHORTS_EXPIRATION);
            } else {
                jedis.expire(key, USERS_EXPIRATION);
            }

            return Result.ok(obj);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    public static <T> Result<T> invalidate(String... keys) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            jedis.del(keys);

            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            return Result.ok(supplierFunc.get());
        } catch (Exception x) {
            x.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }
}
