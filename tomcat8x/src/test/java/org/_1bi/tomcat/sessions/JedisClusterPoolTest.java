package org._1bi.tomcat.sessions;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import redis.clients.jedis.JedisCluster;

public class JedisClusterPoolTest {
	
	private static ConnectionPool<JedisCluster> pool;
	
	
	
	@BeforeAll
	static void initAll() {

		
		
		PoolConfig poolConfig = new PoolConfig();
		
		Properties clusterProps = new Properties();
		clusterProps.setProperty( RedisConfig.CLUSTER_PROPERTY, "10.12.204.78:6379,10.12.204.79:6379,10.12.204.80:6379" );
		clusterProps.setProperty( RedisConfig.TIMEOUT_PROPERTY , "10000");
		clusterProps.setProperty( RedisConfig.MAXATTE_PROPERTY , "6");
		//clusterProps.setProperty( RedisConfig.PASSWORD_PROPERTY  , null);		
		
	

		
		
		pool = new RedisClusterConnPool( poolConfig , clusterProps );
		
	}
	
	
	@DisplayName("test Simple get set jedis cluster")
	@Test
	public void test_case1() {
		
		JedisCluster client = pool.getConnection();
		client.set("testKey1", "testValue1");
		
		String value = client.get("testKey1");
		
		
		Assertions.assertEquals("testValue1", value);
		
		
		pool.returnConnection( client );
	}

}
