package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("list")
    public Result queryTypeList(@RequestBody ShopType shopType) {
        String key = String.valueOf(shopType.getId());
        //在redis中查询是否有店铺类型信息
        String shopTypes = redisTemplate.opsForValue().get(key);
        //存在，直接返回
        if (StrUtil.isNotBlank(shopTypes)){
            return Result.ok(shopTypes);
        }

        //不存在，查询数据库，存入redis
//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();
        LambdaQueryWrapper<ShopType> queryWrapper = new LambdaQueryWrapper<ShopType>()
                                                    .orderByAsc(ShopType::getSort);
        List<ShopType> typeList = typeService.list(queryWrapper);
        //不存在，返回错误信息
        if (shopTypes == null){
            return Result.fail("错误");
        }
        return Result.ok(typeList);
    }
}
