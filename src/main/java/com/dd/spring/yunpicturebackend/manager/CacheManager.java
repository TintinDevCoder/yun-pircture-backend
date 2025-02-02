package com.dd.spring.yunpicturebackend.manager;

import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
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
    public Object getCacheData(String key) {


        String fromLocalCache = getFromLocalCache(key);
        if (fromLocalCache != null) {
            return fromLocalCache;
        }
        String fromRedis = getFromRedis(key);
        return fromRedis;
    }

    /**
     * 从缓存中存储数据
     * @param key
     * @param value
     * @param expireTime
     */
    public void setCacheData(String key, Object value, int expireTime) {

        //构建缓存的value
        String cacheValue = JSONUtil.toJsonStr(value);
        //存入本地缓存
        putInLocalCache(key, cacheValue);
        //存入redis缓存
        putInRedis(key, cacheValue, expireTime);
    }

    /**
     * 刷新以指定前缀开头的所有 Redis 缓存
     */
    public void refreshRedisCacheByPrefix(String prefix) {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(100)
                    .build());

            while (cursor.hasNext()) {
                byte[] keyBytes = cursor.next();
                String key = new String(keyBytes);
                stringRedisTemplate.delete(key);
            }

            return null;
        });
    }

    /**
     * 刷新以指定前缀开头的所有本地缓存
     */
    public void refreshLocalCacheByPrefix(String prefix) {
        localCache.asMap().keySet().forEach(key -> {
            if (key.startsWith(prefix)) {
                localCache.invalidate(key);
            }
        });
    }

    /**
     * 刷新以指定前缀开头的所有缓存（Redis + 本地缓存）
     */
    public void refreshAllCacheByPrefix(String prefix) {
        refreshRedisCacheByPrefix(prefix);
        refreshLocalCacheByPrefix(prefix);
    }

    /**
     * 删除所有缓存
     */
    public void deleteAllCache() {
        localCache.invalidateAll();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.flushDb();
            return null;
        });
    }
}