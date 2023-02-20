package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.util.List;

/**
 *Todo:
 * 作业 使用redis去改造 '商店类型' 接口
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {

//        List<ShopType> typeList = typeService
//                .query().orderByAsc("sort").list();

        List<ShopType> typeList2 = typeService.queryTypeList();

        if(typeList2==null) return Result.fail("查询错误！");

        return Result.ok(typeList2);
    }
}
