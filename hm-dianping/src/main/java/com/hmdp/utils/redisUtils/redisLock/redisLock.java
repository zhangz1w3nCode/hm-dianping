package com.hmdp.utils.redisUtils.redisLock;

public interface redisLock {

    boolean tryLock(long timeoutSec);

    void unLock();

}
