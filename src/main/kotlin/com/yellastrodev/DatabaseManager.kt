package com.yellastrodev

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

object DatabaseManager {
    val REDIS_HOST = "redis-12855.crce175.eu-north-1-1.ec2.redns.redis-cloud.com"
    val REDIS_PORT = 12855
    val REDIS_PAS = "mT9UIPBt7IC3IhA3uFe3XG06awiPMRyh"

    val jedisPool = JedisPool(
        JedisPoolConfig().apply {
            maxTotal = 30 // Максимальное количество соединений в пуле
            maxIdle = 15 // Максимальное количество неиспользуемых соединений в пуле
            maxWaitMillis = 10000L // Максимальное время ожидания соединения
        },
        REDIS_HOST,
        REDIS_PORT,
        6000,
        REDIS_PAS)
}