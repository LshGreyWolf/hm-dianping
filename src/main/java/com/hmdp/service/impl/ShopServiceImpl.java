package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IShopService shopService;

    @Override
    public Object queryById(Long id) {
        //互斥锁解决缓存穿透
        Shop shop = queryWithMutex(id);
        if (shop == null){
            return  Result.fail("空");
        }

        return Result.ok();
    }

    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //从redis中查询缓存
        String shopJson = redisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            //存在，返回

            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //命中缓存后，如果命中“”空 字符串 进入这个if  如果为null不走这个if直接查询数据库
        if (shopJson != null) {
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        Shop shop = null;
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否成功

            if (!isLock) {
                //失败，则，休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功，根据id查询数据库
            //不存在，查询数据库
            shop = shopService.getById(id);
            //数据库中根据id查询店铺信息

            //存在店铺信息，,写入redis中，返回
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //不存在，将null存到redis中
            if (shop == null) {
                redisTemplate.opsForValue().set(key, " ", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(lockKey);
        }
        return shop;
    }

    /**
     * 加锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unLock(String key) {
        redisTemplate.delete(key);

    }

    /**
     * 缓存穿透
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //从redis中查询缓存
        String shopJson = redisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            //存在，返回

            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //命中缓存后，如果命中“”空 字符串 进入这个if  如果为null不走这个if直接查询数据库
        if (shopJson != null) {
            return null;
        }
        //不存在，查询数据库
        Shop shop = shopService.getById(id);
        //数据库中根据id查询店铺信息

        //存在店铺信息，,写入redis中，返回
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //不存在，将null存到redis中
        if (shop == null) {
            redisTemplate.opsForValue().set(key, " ", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;

        }
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //更新数据库
        shopService.updateById(shop);
        //删除缓存
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        redisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok(shop);
    }
}
