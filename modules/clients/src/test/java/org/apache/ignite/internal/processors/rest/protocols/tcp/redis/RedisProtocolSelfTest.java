/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.rest.protocols.tcp.redis;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.ConnectorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Assert;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Tests for Redis protocol.
 */
public class RedisProtocolSelfTest extends GridCommonAbstractTest {
    /** Grid count. */
    private static final int GRID_CNT = 2;

    /** IP finder. */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** Local host. */
    private static final String HOST = "127.0.0.1";

    /** Port. */
    private static final int PORT = 6379;

    /** Pool. */
    private static JedisPool pool;

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        startGrids(gridCount());

        JedisPoolConfig jedisPoolCfg = new JedisPoolConfig();

        jedisPoolCfg.setMaxWaitMillis(10000);
        jedisPoolCfg.setMaxIdle(100);
        jedisPoolCfg.setMinIdle(1);
        jedisPoolCfg.setNumTestsPerEvictionRun(10);
        jedisPoolCfg.setTestOnBorrow(true);
        jedisPoolCfg.setTestOnReturn(true);
        jedisPoolCfg.setTestWhileIdle(true);
        jedisPoolCfg.setTimeBetweenEvictionRunsMillis(30000);

        pool = new JedisPool(jedisPoolCfg, HOST, PORT, 10000);
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        stopAllGrids();

        pool.destroy();
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setLocalHost(HOST);

        assert cfg.getConnectorConfiguration() == null;

        ConnectorConfiguration redisCfg = new ConnectorConfiguration();

        redisCfg.setHost(HOST);
        redisCfg.setPort(PORT);

        cfg.setConnectorConfiguration(redisCfg);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(IP_FINDER);

        cfg.setDiscoverySpi(disco);

        CacheConfiguration ccfg = defaultCacheConfiguration();

        ccfg.setStatisticsEnabled(true);
        ccfg.setIndexedTypes(String.class, String.class);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /**
     * @return Cache.
     */
    @Override protected <K, V> IgniteCache<K, V> jcache() {
        return grid(0).cache(null);
    }

    /** {@inheritDoc} */
    protected int gridCount() {
        return GRID_CNT;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        assert grid(0).cluster().nodes().size() == gridCount();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        jcache().clear();

        assertTrue(jcache().localSize() == 0);
    }

    /**
     * @throws Exception If failed.
     */
    public void testPing() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals("PONG", jedis.ping());
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testEcho() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals("Hello, grid!", jedis.echo("Hello, grid!"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testGet() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            jcache().put("getKey1", "getVal1");
            jcache().put("getKey2", 0);

            Assert.assertEquals("getVal1", jedis.get("getKey1"));
            Assert.assertEquals("0", jedis.get("getKey2"));
            Assert.assertNull(jedis.get("wrongKey"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testGetSet() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            jcache().put("getSetKey1", 1);

            Assert.assertEquals("1", jedis.getSet("getSetKey1", "0"));
            Assert.assertNull(jedis.get("getSetNonExistingKey"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testMGet() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            jcache().put("getKey1", "getVal1");
            jcache().put("getKey2", 0);

            List<String> result = jedis.mget("getKey1", "getKey2", "wrongKey");
            Assert.assertTrue(result.contains("getVal1"));
            Assert.assertTrue(result.contains("0"));

            // not supported.
//            fail("Incompatible! getAll() does not return null values!");
//            Assert.assertTrue(result.contains("nil"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testSet() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            jedis.set("setKey1", "1");
            jedis.set("setKey2".getBytes(), "b0".getBytes());

            Assert.assertEquals("1", jcache().get("setKey1"));
            Assert.assertEquals("b0", jcache().get("setKey2"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testMSet() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            jedis.mset("setKey1", "1", "setKey2", "2");

            Assert.assertEquals("1", jcache().get("setKey1"));
            Assert.assertEquals("2", jcache().get("setKey2"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testIncrDecr() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals(1, (long)jedis.incr("newKeyIncr"));
            Assert.assertEquals(-1, (long)jedis.decr("newKeyDecr"));

            jcache().put("incrKey1", 1L);
            Assert.assertEquals(2L, (long)jedis.incr("incrKey1"));
            jcache().put("decrKey1", 1L);
            Assert.assertEquals(0L, (long)jedis.decr("decrKey1"));

            jcache().put("nonInt", "abc");
            try {
                jedis.incr("nonInt");

                assert false : "Exception has to be thrown!";
            }
            catch (JedisDataException e) {
                assertTrue(e.getMessage().startsWith("ERR"));
            }
            try {
                jedis.decr("nonInt");

                assert false : "Exception has to be thrown!";
            }
            catch (JedisDataException e) {
                assertTrue(e.getMessage().startsWith("ERR"));
            }

            jcache().put("outOfRange", new BigInteger("234293482390480948029348230948"));
            try {
                jedis.incr("outOfRange");

                assert false : "Exception has to be thrown!";
            }
            catch (JedisDataException e) {
                assertTrue(e.getMessage().startsWith("ERR"));
            }
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testIncrDecrBy() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals(2, (long)jedis.incrBy("newKeyIncr1", 2));
            Assert.assertEquals(-2, (long)jedis.decrBy("newKeyDecr1", 2));

            jcache().put("incrKey2", 1L);
            Assert.assertEquals(3L, (long)jedis.incrBy("incrKey2", 2));
            jcache().put("decrKey2", 2L);
            Assert.assertEquals(0L, (long)jedis.decrBy("decrKey2", 2));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testAppend() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals(5, (long)jedis.append("appendKey1", "Hello"));
            Assert.assertEquals(12, (long)jedis.append("appendKey1", " World!"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testStrlen() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals(0, (long)jedis.strlen("strlenKeyNonExisting"));

            jcache().put("strlenKey", "abc");
            Assert.assertEquals(3, (long)jedis.strlen("strlenKey"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testSetRange() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals(0, (long)jedis.setrange("setRangeKey1", 0, ""));

            jcache().put("setRangeKey2", "abc");
            Assert.assertEquals(3, (long)jedis.setrange("setRangeKey2", 0, ""));

            Assert.assertEquals(3, (long)jedis.setrange("setRangeKeyPadded", 2, "a"));

            try {
                jedis.setrange("setRangeKeyWrongOffset", -1, "a");

                assert false : "Exception has to be thrown!";
            }
            catch (JedisDataException e) {
                assertTrue(e.getMessage().startsWith("ERR"));
            }

            try {
                jedis.setrange("setRangeKeyWrongOffset2", 536870911, "a");

                assert false : "Exception has to be thrown!";
            }
            catch (JedisDataException e) {
                assertTrue(e.getMessage().startsWith("ERR"));
            }

            jcache().put("setRangeKey3", "Hello World");
            Assert.assertEquals(11, (long)jedis.setrange("setRangeKey3", 6, "Redis"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testGetRange() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals("", jedis.getrange("getRangeKeyNonExisting", 0, 0));

            jcache().put("getRangeKey", "This is a string");
            Assert.assertEquals("This", jedis.getrange("getRangeKey", 0, 3));
            Assert.assertEquals("ing", jedis.getrange("getRangeKey", -3, -1));
            Assert.assertEquals("This is a string", jedis.getrange("getRangeKey", 0, -1));
            Assert.assertEquals("string", jedis.getrange("getRangeKey", 10, 100));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testDel() throws Exception {
        jcache().put("delKey1", "abc");
        jcache().put("delKey2", "abcd");
        try (Jedis jedis = pool.getResource()) {
            // Should return the number of actually deleted entries.
//            Assert.assertEquals(0, (long)jedis.del("nonExistingDelKey"));
            Assert.assertEquals(2, (long)jedis.del("delKey1", "delKey2"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testExists() throws Exception {
        jcache().put("existsKey1", "abc");
        jcache().put("existsKey2", "abcd");
        try (Jedis jedis = pool.getResource()) {
            Assert.assertFalse(jedis.exists("nonExistingDelKey"));
            Assert.assertEquals(2, (long)jedis.exists("existsKey1", "existsKey2"));
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testDbSize() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            Assert.assertEquals(0, (long)jedis.dbSize());

            jcache().putAll(new HashMap<Integer, Integer>() {
                {
                    for (int i = 0; i < 100; i++)
                        put(i, i);
                }
            });
            Assert.assertEquals(100, (long)jedis.dbSize());
        }
    }
}