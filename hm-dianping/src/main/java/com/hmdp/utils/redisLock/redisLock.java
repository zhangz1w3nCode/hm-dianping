package com.hmdp.utils.redisLock;

public interface redisLock {

    boolean tryLock(long timeoutSec);

    void unLock();

}
