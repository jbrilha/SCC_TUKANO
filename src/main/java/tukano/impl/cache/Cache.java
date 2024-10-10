package tukano.impl.cache;

import tukano.api.Result;

public interface Cache {

    public <T> Result<T> getOne(String id, Class<T> clazz);

    public <T> Result<T> insertOne(String key, T obj);

    public <T> Result<T> invalidate(String ...keys);

}
