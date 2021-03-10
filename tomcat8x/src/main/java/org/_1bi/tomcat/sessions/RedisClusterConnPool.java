package org._1bi.tomcat.sessions;

import java.io.IOException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

public class RedisClusterConnPool implements ConnectionPool<JedisCluster> {

	private static Log logger = LogFactory.getLog(RedisClusterConnPool.class);	
	
    private ObjectPool<JedisCluster> pool;
    
    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param clusterNodes the jedis cluster nodes
     */
    public RedisClusterConnPool(final Set<HostAndPort> clusterNodes) {

        this(clusterNodes, RedisConfig.DEFAULT_TIMEOUT);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param clusterNodes the cluster nodes
     * @param timeout      the timeout
     */
    public RedisClusterConnPool(final Set<HostAndPort> clusterNodes,
                                final int timeout) {

        this(clusterNodes, timeout, timeout);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param clusterNodes      the jedis cluster nodes
     * @param connectionTimeout the connection timeout
     * @param soTimeout         the so timeout
     */
    public RedisClusterConnPool(final Set<HostAndPort> clusterNodes,
                                final int connectionTimeout,
                                final int soTimeout) {

        this(clusterNodes, connectionTimeout, soTimeout, RedisConfig.DEFAULT_MAXATTE);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param clusterNodes      the jedis cluster nodes
     * @param connectionTimeout the connection timeout
     * @param soTimeout         the so timeout
     * @param maxAttempts       the max attempts
     */
    public RedisClusterConnPool(final Set<HostAndPort> clusterNodes,
                                final int connectionTimeout,
                                final int soTimeout,
                                final int maxAttempts) {

        this(clusterNodes, connectionTimeout, soTimeout, maxAttempts, new PoolConfig());
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param properties the properties
     */
    public RedisClusterConnPool(final Properties properties) {

        this(new PoolConfig(), properties);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param poolConfig the pool config
     * @param properties the properties
     */
    public RedisClusterConnPool(final PoolConfig poolConfig, final Properties properties) {

        Set<HostAndPort> jedisClusterNodes = new HashSet<HostAndPort>();

        for (String hostAndPort : properties.getProperty(RedisConfig.CLUSTER_PROPERTY).split(","))

            jedisClusterNodes.add(new HostAndPort(hostAndPort.split(":")[0], Integer.valueOf(hostAndPort.split(":")[1])));

        int timeout = Integer.parseInt(properties.getProperty(RedisConfig.TIMEOUT_PROPERTY, String.valueOf(RedisConfig.DEFAULT_TIMEOUT)));

        int maxAttempts = Integer.valueOf(properties.getProperty(RedisConfig.MAXATTE_PROPERTY, String.valueOf(RedisConfig.DEFAULT_MAXATTE)));

        String password = properties.getProperty(RedisConfig.PASSWORD_PROPERTY, RedisConfig.DEFAULT_PASSWORD);
 
        
        RedistClusterPoolableObjectFactory factory = new RedistClusterPoolableObjectFactory(
        		jedisClusterNodes,
        		timeout,
        		timeout,
        		maxAttempts,
        		password,
        		poolConfig
        		);
        
        
        pool = new GenericObjectPool<JedisCluster>(factory, poolConfig);  
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param poolConfig   the pool config
     * @param clusterNodes the cluster nodes
     */
    public RedisClusterConnPool(final PoolConfig poolConfig, final Set<HostAndPort> clusterNodes) {

        this(poolConfig, clusterNodes, RedisConfig.DEFAULT_PASSWORD);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param poolConfig   the pool config
     * @param clusterNodes the cluster nodes
     * @param password     the password
     */
    public RedisClusterConnPool(final PoolConfig poolConfig, final Set<HostAndPort> clusterNodes, final String password) {

        this(poolConfig, clusterNodes, password, RedisConfig.DEFAULT_TIMEOUT);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param poolConfig   the pool config
     * @param clusterNodes the cluster nodes
     * @param password     the password
     * @param timeout      the timeout
     */
    public RedisClusterConnPool(final PoolConfig poolConfig, final Set<HostAndPort> clusterNodes, final String password, final int timeout) {

        this(clusterNodes, timeout, timeout, RedisConfig.DEFAULT_MAXATTE, password, poolConfig);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param clusterNodes the cluster nodes
     * @param timeout      the timeout
     * @param maxAttempts  the max attempts
     * @param poolConfig   the pool config
     */
    public RedisClusterConnPool(final Set<HostAndPort> clusterNodes,
                                final int timeout,
                                final int maxAttempts,
                                final PoolConfig poolConfig) {

        this(clusterNodes, timeout, timeout, maxAttempts, RedisConfig.DEFAULT_PASSWORD, poolConfig);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param clusterNodes      the jedis cluster nodes
     * @param connectionTimeout the connection timeout
     * @param soTimeout         the so timeout
     * @param maxAttempts       the max attempts
     * @param poolConfig        the pool config
     */
    public RedisClusterConnPool(final Set<HostAndPort> clusterNodes,
                                final int connectionTimeout,
                                final int soTimeout,
                                final int maxAttempts,
                                final PoolConfig poolConfig) {

        this(clusterNodes, connectionTimeout, soTimeout, maxAttempts, RedisConfig.DEFAULT_PASSWORD, poolConfig);
    }

    /**
     * Instantiates a new Redis cluster conn pool.
     *
     * @param clusterNodes      the cluster nodes
     * @param connectionTimeout the connection timeout
     * @param soTimeout         the so timeout
     * @param maxAttempts       the max attempts
     * @param password          the password
     * @param poolConfig        the pool config
     */
    public RedisClusterConnPool(final Set<HostAndPort> clusterNodes,
                                final int connectionTimeout,
                                final int soTimeout,
                                final int maxAttempts,
                                final String password,
                                final PoolConfig poolConfig) {
    	
        RedistClusterPoolableObjectFactory factory = new RedistClusterPoolableObjectFactory(
        		clusterNodes,
        		connectionTimeout,
        		soTimeout,
        		maxAttempts,
        		password,
        		poolConfig
        		);
        
        
        pool = new GenericObjectPool<JedisCluster>(factory, poolConfig);  

    }

    @Override
    public JedisCluster getConnection() {
    	
    	JedisCluster newObject = null;
		try {
			
			newObject = pool.borrowObject();
		
		} catch (NoSuchElementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {

			e.printStackTrace();
		}

        return newObject;
    }

    @Override
    public void returnConnection(JedisCluster conn) {
    	
    	
    	if ( !Objects.isNull( conn )) {
    		
    		try {
    			
				conn.close();

    		} catch (IOException e) {
				logger.warn( e.getMessage() );
			}
    	}

    }

    @Override
    public void invalidateConnection(JedisCluster conn) {
        // nothing to do...
    }

    /**
     * Close.
     *
     * @throws IOException the io exception
     */
    public void close() throws IOException {

        //jedisCluster.close();
    }
}
