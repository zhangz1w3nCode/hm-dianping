local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId

local orderKey = 'seckill:order:' .. voucherId

if(tonumber(redis.call('get',stockKey)) <= 0) then
    return 1

end

if(redis.call('sismember',orderKey,userId) == 1) then
    return 2

end

redis.call('incrby',stockKey,-1)
redis.call('sadd',orderKey,userId)

redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0