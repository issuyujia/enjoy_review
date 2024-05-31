package com.suyujia.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.suyujia.dto.Result;
import com.suyujia.entity.Shop;
import com.suyujia.mapper.ShopMapper;
import com.suyujia.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suyujia.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.suyujia.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author syj
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById, CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //采用互斥锁方式
        //Shop shop = queryWithMutex(id);
        //采用逻辑过期时间方式
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById, CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop==null){
            return Result.fail("商店信息为空！！！");
        }
        //7.返回
        return Result.ok(shop);
    }

    /**
     * 采用互斥锁解决缓存击穿问题
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY+id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断是否是空字符串
        if(shopJson!=null){
            //返回一个错误信息
            return null;
        }
        //4.实现缓存重建
        Shop shop = null;
        //4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2 判断是否获取成功
            if(!isLock){
                //4.3 失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4 成功，根据id查询数据库
            shop = getById(id);
            //模拟重建数据库需要的时间
            Thread.sleep(200);
            //5.不存在，返回错误
            if(shop==null){
                //解决穿透问题
                //将空值写入redis缓存中，并且设置过期时间
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException();
        }finally {
            //7释放互斥锁
            unclock(lockKey);
        }
        //8.返回
        return shop;
    }
    //创建一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 采用逻辑过期时间解决缓存击穿问题
     * @param id
     * @return
     */
//    public Shop queryWithLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY+id;
//        //1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断缓存是否命中
//        if(StrUtil.isBlank(shopJson)){
//            //3.不存在，直接返回
//            return null;
//        }
//        //4 命中，先将json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5 判断缓存是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //5.1 未过期，返回商铺信息
//            return shop;
//        }
//        //5.2 已过期，需要缓存重建
//        //6 缓存重建
//        //6.1 尝试获取互斥锁
//        String lockKey = LOCK_SHOP_KEY+id;
//        boolean isLock = tryLock(lockKey);
//        if(isLock){
//            //获取锁成功，开启独立线程
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    //重建缓存
//                    this.saveShop2Redis(id, 20L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    // 释放锁
//                    unclock(lockKey);
//                }
//
//            });
//        }
//        //6 获取互斥锁成功，开启独立线程
//        return shop;
//    }
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
//    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
//        //1.查询店铺信息
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        //2.封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //3.写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//
//    }
    /**
     * 更新商店信息
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        //获取商店id
        Long id = shop.getId();
        if(id == null){
            return Result.fail("商店id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
