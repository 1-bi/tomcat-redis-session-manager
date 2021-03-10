/**
 * 
 */
package org._1bi.tomcat.sessions;

import java.util.Set;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

/**
 * @author vicente.ruan
 *
 */
public class RedistClusterPoolableObjectFactory extends BasePooledObjectFactory<JedisCluster> {


	private Set<HostAndPort> hostsPort;
	
	private int timeout;
	
	private int soTimeout;
	
	private int maxAttempts;
	
	private String password;
	
	private PoolConfig poolConfig;
	

	public RedistClusterPoolableObjectFactory(Set<HostAndPort> hostsPort, int timeout, int soTimeout, int maxAttempts,
			String password, PoolConfig poolConfig) {
		super();
		this.hostsPort = hostsPort;
		this.timeout = timeout;
		this.soTimeout = soTimeout;
		this.maxAttempts = maxAttempts;
		this.password = password;
		this.poolConfig = poolConfig;
	}

	@Override
	public JedisCluster create() throws Exception {
		JedisCluster jedisCluster = new JedisCluster(hostsPort, timeout, timeout, maxAttempts, password, poolConfig);
		return jedisCluster;
	}

	@Override
	public PooledObject<JedisCluster> wrap(JedisCluster obj) {
		return new DefaultPooledObject<JedisCluster>(obj);
	}

	
	
}
