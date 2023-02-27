package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.redisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private  SeckillVoucherServiceImpl seckillVoucherService;

    @Autowired
    private redisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient RedissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new voucherOrderHandler());
    }

    private IVoucherOrderService proxy;
    //优化
    // 使用redisStream消息队列优化
    // 性能指标:    没优化  -> 线程池➕阻塞队列 -> redisStream消息队列
    // 接口响应:     497   ->      176      ->       110
    // 吞吐量:      1000   ->     1500      ->      1577
    //获取redis消息队列订单信息
    private class voucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    //读取的消息
                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofMillis(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));

                    if(list==null||list.isEmpty()){
                        continue;
                    }

                    MapRecord<String, Object, Object> record = list.get(0);

                    Map<Object, Object> values = record.getValue();
                    //把消息转化成对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    //ack
                    redisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());

                    handleOrder(voucherOrder);

                } catch (Exception e) {


                    log.error("处理RedisStreamQueue订单异常",e);
                    //去处理异常消息 PendingList
                    handlePendingList();
                }

            }
        }
    }


    //去处理异常消息
    private void handlePendingList() {

        while (true){
            try {
                //读取pendingQueue的消息
                List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0")));

                if(list==null||list.isEmpty()){
                    //pendingQueue中无消息 -直接结束
                    break;
                }

                MapRecord<String, Object, Object> record = list.get(0);

                Map<Object, Object> values = record.getValue();
                //把消息转化成对象
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                //ack
                redisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());

                handleOrder(voucherOrder);

            } catch (Exception e) {
                log.error("处理pendingList异常",e);
            }

        }

    }

    //获取阻塞队列订单信息
//    private class voucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    handleOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常",e);
//                }
//
//            }
//        }
//    }


    //处理订单信息-入库
    private void handleOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        //使用redisson框架 实现分布式锁
        RLock lock = RedissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();

        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }

        try {
            //创建订单
            proxy.creatOrder(voucherOrder);

        } finally {
            //分布式情况下 删除锁有问题：
            // 1.删了别人的 2.删除操作不是原子性的
            // 通过lua脚本 让删除操作变为原子性的
            lock.unlock();
            //lock.unLock();

        }
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        LocalDateTime beginTime = voucher.getBeginTime();
//        LocalDateTime endTime = voucher.getEndTime();
//        LocalDateTime curTime = LocalDateTime.now();
//        if(curTime.isBefore(beginTime)){
//            return Result.fail("当前时间 早于 活动开始时间 => 活动没开始");
//        }
//        if(curTime.isAfter(endTime)){
//            return Result.fail("当前时间 晚于 活动结束时间 => 活动结束");
//        }
//
//        Integer DbStock = voucher.getStock();
//
//        if (DbStock < 1) return Result.fail("优惠券库存不足");
//
//        Long userId = UserHolder.getUser().getId();
//
//        //一人一单 线程安全的做法
//        //simpleRedisLock lock = new simpleRedisLock("voucherOrder:"+userId,redisTemplate);
//
//        //使用redisson框架 实现分布式锁
//        RLock lock = RedissonClient.getLock("voucherOrder:" + userId);
//
//        boolean isLock = lock.tryLock();
//
//         if(!isLock){
//            return Result.fail("一人仅仅只能抢一单!");
//        }
//
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();;
//            return proxy.creatOrder(voucherId);
//        }finally {
//            //分布式情况下 删除锁有问题：
//            // 1.删了别人的 2.删除操作不是原子性的
//            // 通过lua脚本 让删除操作变为原子性的
//            lock.unlock();
//            //lock.unLock();
//        }
//
//
//
//    }

    //优化
    // 使用redis进行异步下单操作 接口响应497->176 吞吐量1000 -> 1500
    // 基于lua脚本的阻塞队列的异步秒杀

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        //lua脚本 进行用户秒杀资格的判断
//        Long userId = UserHolder.getUser().getId();
//        Long res = redisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
//        int r = res.intValue();
//        if(r!=0){
//            return Result.fail(r==1?"库存不足":"不能重复下单");
//        }
//        //保存到阻塞队列 -> 保存下单信息
//        //封装到对象 订单id 用户id 优惠券id
//
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//
//        //创建订单 放入 阻塞队列 后续流程异步执行
//        orderTasks.add(voucherOrder);
//
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//
//        return Result.ok(orderId);
//    }


    //使用redis消息队列去实现异步下单
    @Override
    public Result seckillVoucher(Long voucherId) {

        //lua脚本 进行用户秒杀资格的判断
        Long userId = UserHolder.getUser().getId();
        //订单ID
        long orderId = redisIdWorker.nextId("order");

        Long res = redisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId));

        int r = res.intValue();

        if(r!=0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
        //保存到阻塞队列 -> 保存下单信息
        //封装到对象 订单id 用户id 优惠券id

//        VoucherOrder voucherOrder = new VoucherOrder();
//
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//
//        //创建订单 放入 阻塞队列 后续流程异步执行
//        orderTasks.add(voucherOrder);
//
        proxy = (IVoucherOrderService) AopContext.currentProxy();


        return Result.ok(orderId);
    }

    @Transactional
    //一人一单 线程安全的做法
    public void creatOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();

        Integer orderSum = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();

        if(orderSum>0){
            log.error("购买失败！一人仅限制买一单！");
            return ;
        }


        boolean status = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                //使用乐观锁的方式解决超卖问题
                //.eq("stock", voucher.getStock())
                .gt("stock", 0)
                .update();

        if (!status){
            log.error("更新失败！");
            return;
        }


        save(voucherOrder);
    }
}
