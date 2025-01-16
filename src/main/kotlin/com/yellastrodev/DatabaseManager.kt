package com.yellastrodev

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

object DatabaseManager {
    val REDIS_HOST = "redis-12703.c327.europe-west1-2.gce.redns.redis-cloud.com"
    val REDIS_PORT = 12703
    val REDIS_PAS = "XUqovunbShd12asbuLVoZeYf63DfJNPq"

    val jedisPool = JedisPool(
        JedisPoolConfig().apply {
            maxTotal = 30 // Максимальное количество соединений в пуле
            maxIdle = 15 // Максимальное количество неиспользуемых соединений в пуле
            maxWaitMillis = 3000L // Максимальное время ожидания соединения
        },
        REDIS_HOST,
        REDIS_PORT,
        6000,
        REDIS_PAS)
}