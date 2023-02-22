package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Autowired
    private VoucherOrderServiceImpl voucherOrderService;

    //抢券功能
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        Result res = voucherOrderService.seckillVoucher(voucherId);
        return res;

    }
}
