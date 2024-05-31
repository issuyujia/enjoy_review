package com.suyujia.service;

import com.suyujia.dto.Result;
import com.suyujia.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author syj
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}
