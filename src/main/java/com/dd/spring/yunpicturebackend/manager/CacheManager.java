package com.dd.spring.yunpicturebackend.manager;

import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.concurrent.TimeUnit;

@Component
public class CacheManager {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 本地缓存
     */
    private final Cache<String, String> localCache =
            Caffeine.newBuilder()
                    .initialCapacity(1024)
                    .maximumSize(10000L)
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    /**
     * 从本地缓存获取数据
     */
    public String getFromLocalCache(String key) {
        return localCache.getIfPresent(key);
    }

    /**
     * 存入本地缓存
     */
    public void putInLocalCache(String key, String value) {
        localCache.put(key, value);
    }

    /**
     * 从 Redis 获取数据
     */
    public String getFromRedis(String key) {
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        return opsForValue.get(key);
    }

    /**
     * 存入 Redis
     */
    public void putInRedis(String key, String value, int expireTime) {
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        opsForValue.set(key, value, expireTime, TimeUnit.SECONDS);
    }

    /**
     * 从缓存中获取数据
     * @param key
     * @return
     */
    public Object getCacheData(Object key, String tag) {
        //构建缓存的key
        String queryCondition = JSONUtil.toJsonStr(key);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = String.format("yunpicture:%s:%s", tag, hashKey);

        String fromLocalCache = getFromLocalCache(cacheKey);
        if (fromLocalCache != null) {
            return fromLocalCache;
        }
        String fromRedis = getFromRedis(cacheKey);
        return fromRedis;
    }

    /**
     * 从缓存中存储数据
     * @param key
     * @param value
     * @param expireTime
     */
    public void setCacheData(Object key, Object value, String tag, int expireTime) {
        //构建缓存的key
        String queryCondition = JSONUtil.toJsonStr(key);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = String.format("yunpicture:%s:%s", tag, hashKey);
        //构建缓存的value
        String cacheValue = JSONUtil.toJsonStr(value);
        //存入本地缓存
        putInLocalCache(cacheKey, cacheValue);
        //存入redis缓存
        putInRedis(cacheKey, cacheValue, expireTime);
    }
}