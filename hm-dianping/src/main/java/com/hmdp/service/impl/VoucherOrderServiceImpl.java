package com.hmdp.service.impl;

import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
        Long userId = voucherOrder.getUserId();
//        Long voucherId = voucherOrder.getVoucherId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
        if(!isLock){
            log.error("不允许重复下单");
            return;
        }
        try {
             proxy.createVoucherOrder(voucherOrder);
             return;
        } finally {
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {

        Long userId = UserHolder.getUser().getId();

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        if(r != 0){
            return Result.fail(r == 1? "库存不足":"不能重复下单");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        orderTasks.add(voucherOrder);

        proxy = (IVoucherOrderService)AopContext.currentProxy();

        return Result.ok(orderId);
    }


//    @Override
//    public Result seckillVoucher(Long voucherId) throws InterruptedException {
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀尚未开始");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已经结束");
//        }
//
//        if(voucher.getStock() < 1){
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()){
////            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
////        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
//        if(!isLock){
//            return  Result.fail("一个人只允许下一单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            log.error("用户已经购买过一次");
            return;
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if(!success){
            log.error("库存不足");
            return;
        }

//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);

        return;


    }
}
