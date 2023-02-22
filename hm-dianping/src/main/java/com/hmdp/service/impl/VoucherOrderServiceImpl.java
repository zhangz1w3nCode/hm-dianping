package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.redisIdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private  SeckillVoucherServiceImpl seckillVoucherService;

    @Autowired
    private redisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        LocalDateTime curTime = LocalDateTime.now();
        if(curTime.isBefore(beginTime)){
            return Result.fail("当前时间 早于 活动开始时间 => 活动没开始");
        }

        if(curTime.isAfter(endTime)){
            return Result.fail("当前时间 晚于 活动结束时间 => 活动结束");
        }

        Integer DbStock = voucher.getStock();

        if(voucher.getStock()<1) return Result.fail("优惠券库存不足");

        boolean status = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                //使用乐观锁的方式解决超卖问题
                //.eq("stock", voucher.getStock())
                .gt("stock", 0)
                .update();

        if(!status) return Result.fail("更新失败！");

        VoucherOrder voucherOrder = new VoucherOrder();

        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);

        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);

        return Result.ok(orderId);
    }
}
