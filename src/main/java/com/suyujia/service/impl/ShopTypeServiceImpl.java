package com.suyujia.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.suyujia.dto.Result;
import com.suyujia.entity.ShopType;
import com.suyujia.mapper.ShopTypeMapper;
import com.suyujia.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.suyujia.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author syj
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //设置redis前缀
        String key = CACHE_SHOP_TYPE_KEY;
        //1.从redis中查询店铺缓存
        String shopType = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否存贮
        if(StrUtil.isNotBlank(shopType)){
            //3.缓存存在，直接返回
            //先将其转化为java对象再将其返回
            List<ShopType> typeList = JSONUtil.toList(shopType, ShopType.class);
            return Result.ok(typeList);
        }
        //4.缓存不存在，从数据库中进行查询
        List<ShopType> list = (List<ShopType>) query().orderByAsc("sort").list();
        if(list.isEmpty()){
            //5.数据库不存在
            return Result.fail("店铺类型查询失败");
        }
        //6.存在，将其存入redis缓存中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(list));
        //7.返回
        return Result.ok(list);
    }
}
