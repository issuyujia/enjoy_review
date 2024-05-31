package com.suyujia.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.suyujia.dto.Result;
import com.suyujia.entity.VoucherOrder;
import com.suyujia.mapper.VoucherOrderMapper;
import com.suyujia.service.ISeckillVoucherService;
import com.suyujia.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suyujia.utils.RedisIdWorker;
import com.suyujia.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author syj
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    //id生成器
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
//    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    String queueName = "stream.orders";
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            try{
                while (true){
                    //1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAM streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if(list==null || list.isEmpty()){
                        //2.1.如果获取失败，说明没有消息，继续下一次循环，继续下一次循环
                        continue;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.2. 如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //4.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                }
            }catch (Exception e){
                log.error("处理订单异常",e);
                handlePendingList();
            }

        }

        private void handlePendingList() {
            try{
                while (true){
                    //1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAM streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if(list==null || list.isEmpty()){
                        //2.1.如果获取失败，说明pending-list没有消息，结束循环
                        break;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.2. 如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //4.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                }
            }catch (Exception e){
                log.error("处理订单异常",e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
//    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024*1024);
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            try{
//                while (true){
//                    //1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTask.take();
//                    //2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                }
//            }catch (Exception e){
//                log.error("处理订单异常",e);
//            }
//
//        }
//    }
    private IVoucherOrderService proxy;
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1，获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:"+userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4，判断锁是否获取成功
        if(!isLock){
            //获取锁失败，返回错误或重试
            log.error("不允许重复下单!!!");
            return;
        }
        try {
            //获取代理对象（事务）
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
    }
        /**
     * 实现秒杀卷的下单功能
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //2.判断结果是否是0
        int r = result.intValue();
        if(r!=0){
            //2.1 不为0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足":"不能重复下单");
        }
        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }
//    /**
//     * 实现秒杀卷的下单功能
//     *
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //获取用户
//        Long userId = UserHolder.getUser().getId();
//        //1.执行lua脚本
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString()
//        );
//        //2.判断结果是否是0
//        int r = result.intValue();
//        if(r!=0){
//            //2.1 不为0，代表没有购买资格
//            return Result.fail(r == 1 ? "库存不足":"不能重复下单");
//        }
//        //2.2 为0,有购买资格，把下单信息保存到阻塞队列中去
//        VoucherOrder voucherOrder = new VoucherOrder();
//
//        //2.3订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
////        //2.4用户id
//        voucherOrder.setUserId(userId);
////        //2.5 代金卷id
//        voucherOrder.setVoucherId(voucherId);
////        //2，6 放入阻塞队列
//        orderTask.add(voucherOrder);
//        //3.获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //返回订单id
//        return Result.ok(orderId);
//    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1,查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2,判断秒杀是否已经开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //秒杀还未开始
//            return Result.fail("秒杀尚未开始！！！");
//        }
//        //3、判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            //秒杀已经结束
//            return Result.fail("秒杀已经结束！！！");
//        }
//        //4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足！！！");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"order:"+userId);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        //尝试获取锁
//        boolean isLock = lock.tryLock();
//        //判断是否获取锁成功
//        if(!isLock){
//            // 获取锁失败,返回错误或者重试
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            //获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //6. 一人一单
        Long userId = voucherOrder.getUserId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //6.1 查询订单
        if (count > 0) {
            //用户已经购买过了
            log.error("用户已经购买过一次了！");
            return;
        }
        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            //扣减失败
            log.error("库存不足！！");
            return ;
        }
        //7.创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //7.1 订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //7.2 用户id
//        UserDTO user = UserHolder.getUser();
//        voucherOrder.setUserId(user.getId());
//        //7.3 代金卷id
//        voucherOrder.setVoucherId(voucherOrder);
        //8.返回订单id
        save(voucherOrder);
        //返回的订单id也就是生成的订单id
//        return Result.ok(orderId);
    //        return Result.ok(voucherOrder);
    }
}
