package com.suyujia.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.suyujia.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }

    /**
     * 将任意java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * 将任意java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存穿透问题
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time, TimeUnit unit){
        String key = keyPrefix+id;
        //1.从redis中查询店铺缓存
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(jsonStr)){
            //存在，直接返回
            return JSONUtil.toBean(jsonStr,type);
        }
        //判断命中的是否是空值
        if(jsonStr!=null){
            //返回错误信息
            return null;
        }
        //4.不存在，根据id查询数据库
        R r = dbFallBack.apply(id);
        //5.不存在，返回错误
        if(r==null){
            //将空值写入redis，防止缓存击穿问题
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.存在，写入redis
        this.set(key,r,time,unit);
        return r;
    }
    //创建一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，采用逻辑过期时间解决缓存击穿问题
     * @param id
     * @return
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit unit){
        String key = keyPrefix+id;
        //1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isBlank(json)){
            //3.不存在，直接返回
            return null;
        }
        //4 命中，先将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5 判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期，返回商铺信息
            return r;
        }
        //5.2 已过期，需要缓存重建
        //6 缓存重建
        //6.1 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //获取锁成功，开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    //1.查询数据库
                    R r1 = dbFallBack.apply(id);
                    //2.写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unclock(lockKey);
                }

            });
        }
        //6 获取互斥锁成功，开启独立线程
        return r;
    }
    /**
     * 尝试进行获取锁的操作，采用的基本原理是使用redis中的setnx
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 删除释放锁
     * @param key
     */
    private void unclock(String key){
        stringRedisTemplate.delete(key);
    }
}
