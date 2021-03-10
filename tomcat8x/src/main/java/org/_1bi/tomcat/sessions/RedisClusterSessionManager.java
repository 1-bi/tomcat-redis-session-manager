package org._1bi.tomcat.sessions;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.Valve;
import org.apache.catalina.session.ManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import redis.clients.jedis.JedisCluster;



public class RedisClusterSessionManager extends ManagerBase implements RedisSessionManagerBean, Lifecycle {

	protected byte[] NULL_SESSION = "null".getBytes();

	private final Log log = LogFactory.getLog(RedisClusterSessionManager.class);

	protected String host = "localhost";
	protected String password = null;
	
	private String timeout;

	protected RedisClusterConnPool connectionPool;

	protected RedisSessionHandlerValve handlerValve;
	protected ThreadLocal<RedisSession> currentSession = new ThreadLocal<>();
	protected ThreadLocal<SessionSerializationMetadata> currentSessionSerializationMetadata = new ThreadLocal<>();
	protected ThreadLocal<String> currentSessionId = new ThreadLocal<>();
	protected ThreadLocal<Boolean> currentSessionIsPersisted = new ThreadLocal<>();
	protected Serializer serializer;

	protected static String name = "RedisClusterSessionManager";

	protected String serializationStrategyClass = "org._1bi.tomcat.sessions.JavaSerializer";

	protected EnumSet<SessionPersistPolicy> sessionPersistPoliciesSet = EnumSet.of(SessionPersistPolicy.DEFAULT);

	/**
	 * The lifecycle event support for this component.
	 */
	protected LifecycleSupport lifecycle = new LifecycleSupport(this);

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}
	
	private int port = 6379;

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}


	public String getTimeout() {
		return timeout;
	}

	public void setTimeout(String timeout) {
		this.timeout = timeout;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setSerializationStrategyClass(String strategy) {
		this.serializationStrategyClass = strategy;
	}

	public String getSessionPersistPolicies() {
		StringBuilder policies = new StringBuilder();
		for (Iterator<SessionPersistPolicy> iter = this.sessionPersistPoliciesSet.iterator(); iter.hasNext();) {
			SessionPersistPolicy policy = iter.next();
			policies.append(policy.name());
			if (iter.hasNext()) {
				policies.append(",");
			}
		}
		return policies.toString();
	}

	public void setSessionPersistPolicies(String sessionPersistPolicies) {
		String[] policyArray = sessionPersistPolicies.split(",");
		EnumSet<SessionPersistPolicy> policySet = EnumSet.of(SessionPersistPolicy.DEFAULT);
		for (String policyName : policyArray) {
			SessionPersistPolicy policy = SessionPersistPolicy.fromName(policyName);
			policySet.add(policy);
		}
		this.sessionPersistPoliciesSet = policySet;
	}

	public boolean getSaveOnChange() {
		return this.sessionPersistPoliciesSet.contains(SessionPersistPolicy.SAVE_ON_CHANGE);
	}

	public boolean getAlwaysSaveAfterRequest() {
		return this.sessionPersistPoliciesSet.contains(SessionPersistPolicy.ALWAYS_SAVE_AFTER_REQUEST);
	}



	@Override
	public int getRejectedSessions() {
		// Essentially do nothing.
		return 0;
	}

	public void setRejectedSessions(int i) {
		// Do nothing.
	}

	protected JedisCluster acquireConnection() {
		return connectionPool.getConnection();
	}

	protected void returnConnection(JedisCluster jedis, Boolean error) {
		connectionPool.returnConnection(jedis);
	}


	@Override
	public void load() throws ClassNotFoundException, IOException {

	}

	@Override
	public void unload() throws IOException {

	}

	/**
	 * Add a lifecycle event listener to this component.
	 *
	 * @param listener
	 *            The listener to add
	 */
	@Override
	public void addLifecycleListener(LifecycleListener listener) {
		lifecycle.addLifecycleListener(listener);
	}

	/**
	 * Get the lifecycle listeners associated with this lifecycle. If this
	 * Lifecycle has no listeners registered, a zero-length array is returned.
	 */
	@Override
	public LifecycleListener[] findLifecycleListeners() {
		return lifecycle.findLifecycleListeners();
	}

	/**
	 * Remove a lifecycle event listener from this component.
	 *
	 * @param listener
	 *            The listener to remove
	 */
	@Override
	public void removeLifecycleListener(LifecycleListener listener) {
		lifecycle.removeLifecycleListener(listener);
	}

	/**
	 * Start this component and implement the requirements of
	 * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
	 *
	 * @exception LifecycleException
	 *                if this component detects a fatal error that prevents this
	 *                component from being used
	 */
	@Override
	protected synchronized void startInternal() throws LifecycleException {
		super.startInternal();

		setState(LifecycleState.STARTING);

		Boolean attachedToValve = false;
		for (Valve valve : getContext().getPipeline().getValves()) {
			if (valve instanceof RedisSessionHandlerValve) {
				this.handlerValve = (RedisSessionHandlerValve) valve;
				this.handlerValve.setRedisSessionManager(this);
				log.info("Attached to RedisSessionHandlerValve");
				attachedToValve = true;
				break;
			}
		}

		if (!attachedToValve) {
			String error = "Unable to attach to session handling valve; sessions cannot be saved after the request without the valve starting properly.";
			log.fatal(error);
			throw new LifecycleException(error);
		}

		try {
			initializeSerializer();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			log.fatal("Unable to load serializer", e);
			throw new LifecycleException(e);
		}

		log.info("Will expire sessions after " + getMaxInactiveInterval() + " seconds");

		initializeDatabaseConnection();

	}

	/**
	 * Stop this component and implement the requirements of
	 * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
	 *
	 * @exception LifecycleException
	 *                if this component detects a fatal error that prevents this
	 *                component from being used
	 */
	@Override
	protected synchronized void stopInternal() throws LifecycleException {
		if (log.isDebugEnabled()) {
			log.debug("Stopping");
		}

		setState(LifecycleState.STOPPING);

		try {
			connectionPool.close();
		} catch (Exception e) {
			// Do nothing.
		}

		// Require a new random number generator if we are restarted
		super.stopInternal();
	}

	@Override
	public Session createSession(String requestedSessionId) {
		RedisSession session = null;
		String sessionId = null;
		String jvmRoute = getJvmRoute();

		Boolean error = true;
		JedisCluster jedis = null;
		try {
			jedis = acquireConnection();

			// Ensure generation of a unique session identifier.
			if (null != requestedSessionId) {
				sessionId = sessionIdWithJvmRoute(requestedSessionId, jvmRoute);
				if (jedis.setnx(sessionId.getBytes(), NULL_SESSION) == 0L) {
					sessionId = null;
				}
			} else {
				do {
					sessionId = sessionIdWithJvmRoute(generateSessionId(), jvmRoute);
				} while (jedis.setnx(sessionId.getBytes(), NULL_SESSION) == 0L); 
			}

			/*
			 * Even though the key is set in Redis, we are not going to flag the
			 * current thread as having had the session persisted since the
			 * session isn't actually serialized to Redis yet. This ensures that
			 * the save(session) at the end of the request will serialize the
			 * session into Redis with 'set' instead of 'setnx'.
			 */

			error = false;

			if (null != sessionId) {
				session = (RedisSession) createEmptySession();
				session.setNew(true);
				session.setValid(true);
				session.setCreationTime(System.currentTimeMillis());
				session.setMaxInactiveInterval(getMaxInactiveInterval());
				session.setId(sessionId);
				session.tellNew();
			}

			currentSession.set(session);
			currentSessionId.set(sessionId);
			currentSessionIsPersisted.set(false);
			currentSessionSerializationMetadata.set(new SessionSerializationMetadata());

			if (null != session) {
				try {
					error = saveInternal(jedis, session, true);
				} catch (IOException ex) {
					log.error("Error saving newly created session: " + ex.getMessage());
					currentSession.set(null);
					currentSessionId.set(null);
					session = null;
				}
			}
		} finally {
			if (jedis != null) {
				returnConnection(jedis, error);
			}
		}

		return session;
	}

	private String sessionIdWithJvmRoute(String sessionId, String jvmRoute) {
		if (jvmRoute != null) {
			String jvmRoutePrefix = '.' + jvmRoute;
			return sessionId.endsWith(jvmRoutePrefix) ? sessionId : sessionId + jvmRoutePrefix;
		}
		return sessionId;
	}

	@Override
	public Session createEmptySession() {
		return new RedisSession(this);
	}

	@Override
	public void add(Session session) {
		try {
			save(session);
		} catch (IOException ex) {
			log.warn("Unable to add to session manager store: " + ex.getMessage());
			throw new RuntimeException("Unable to add to session manager store.", ex);
		}
	}

	@Override
	public Session findSession(String id) throws IOException {
		RedisSession session = null;

		if (null == id) {
			currentSessionIsPersisted.set(false);
			currentSession.set(null);
			currentSessionSerializationMetadata.set(null);
			currentSessionId.set(null);
		} else if (id.equals(currentSessionId.get())) {
			session = currentSession.get();
		} else {
			byte[] data = loadSessionDataFromRedis(id);
			if (data != null) {
				DeserializedSessionContainer container = sessionFromSerializedData(id, data);
				session = container.session;
				currentSession.set(session);
				currentSessionSerializationMetadata.set(container.metadata);
				currentSessionIsPersisted.set(true);
				currentSessionId.set(id);
			} else {
				currentSessionIsPersisted.set(false);
				currentSession.set(null);
				currentSessionSerializationMetadata.set(null);
				currentSessionId.set(null);
			}
		}

		return session;
	}


	public String[] keys() throws IOException {
		JedisCluster jedis = null;
		Boolean error = true;
		try {
			jedis = acquireConnection();
			Set<String> keySet = jedis.keys("*");
			error = false;
			return keySet.toArray(new String[keySet.size()]);
		} finally {
			if (jedis != null) {
				returnConnection(jedis, error);
			}
		}
	}

	public byte[] loadSessionDataFromRedis(String id) throws IOException {
		JedisCluster jedis = null;
		Boolean error = true;

		try {
			log.trace("Attempting to load session " + id + " from Redis");

			jedis = acquireConnection();
			byte[] data = jedis.get(id.getBytes());
			error = false;

			if (data == null) {
				log.trace("Session " + id + " not found in Redis");
			}

			return data;
		} finally {
			if (jedis != null) {
				returnConnection(jedis, error);
			}
		}
	}

	public DeserializedSessionContainer sessionFromSerializedData(String id, byte[] data) throws IOException {
		log.trace("Deserializing session " + id + " from Redis");

		if (Arrays.equals(NULL_SESSION, data)) {
			log.error("Encountered serialized session " + id + " with data equal to NULL_SESSION. This is a bug.");
			throw new IOException("Serialized session data was equal to NULL_SESSION");
		}

		RedisSession session = null;
		SessionSerializationMetadata metadata = new SessionSerializationMetadata();

		try {
			session = (RedisSession) createEmptySession();

			serializer.deserializeInto(data, session, metadata);

			session.setId(id);
			session.setNew(false);
			session.setMaxInactiveInterval(getMaxInactiveInterval());
			session.access();
			session.setValid(true);
			session.resetDirtyTracking();

			if (log.isTraceEnabled()) {
				log.trace("Session Contents [" + id + "]:");
				Enumeration<String> en = session.getAttributeNames();
				while (en.hasMoreElements()) {
					log.trace("  " + en.nextElement());
				}
			}
		} catch (ClassNotFoundException ex) {
			log.fatal("Unable to deserialize into session", ex);
			throw new IOException("Unable to deserialize into session", ex);
		}

		return new DeserializedSessionContainer(session, metadata);
	}

	public void save(Session session) throws IOException {
		save(session, false);
	}

	public void save(Session session, boolean forceSave) throws IOException {
		JedisCluster jedis = null;
		Boolean error = true;

		try {
			jedis = acquireConnection();
			error = saveInternal(jedis, session, forceSave);
		} catch (IOException e) {
			throw e;
		} finally {
			if (jedis != null) {
				returnConnection(jedis, error);
			}
		}
	}

	protected boolean saveInternal(JedisCluster jedis, Session session, boolean forceSave) throws IOException {
		Boolean error = true;

		try {
			log.trace("Saving session " + session + " into Redis");

			RedisSession redisSession = (RedisSession) session;

			if (log.isTraceEnabled()) {
				log.trace("Session Contents [" + redisSession.getId() + "]:");
				Enumeration<String> en = redisSession.getAttributeNames();
				while (en.hasMoreElements()) {
					log.trace("  " + en.nextElement());
				}
			}

			byte[] binaryId = redisSession.getId().getBytes();

			Boolean isCurrentSessionPersisted;
			SessionSerializationMetadata sessionSerializationMetadata = currentSessionSerializationMetadata.get();
			byte[] originalSessionAttributesHash = sessionSerializationMetadata.getSessionAttributesHash();
			byte[] sessionAttributesHash = null;
			if (forceSave || redisSession.isDirty()
					|| null == (isCurrentSessionPersisted = this.currentSessionIsPersisted.get())
					|| !isCurrentSessionPersisted || !Arrays.equals(originalSessionAttributesHash,
							(sessionAttributesHash = serializer.attributesHashFrom(redisSession)))) {
				if ( log.isTraceEnabled() ) {
					log.trace("Save was determined to be necessary");
				}

				if (null == sessionAttributesHash) {
					sessionAttributesHash = serializer.attributesHashFrom(redisSession);
				}

				SessionSerializationMetadata updatedSerializationMetadata = new SessionSerializationMetadata();
				updatedSerializationMetadata.setSessionAttributesHash(sessionAttributesHash);

				jedis.set(binaryId, serializer.serializeFrom(redisSession, updatedSerializationMetadata));

				redisSession.resetDirtyTracking();
				currentSessionSerializationMetadata.set(updatedSerializationMetadata);
				currentSessionIsPersisted.set(true);
			} else {
				log.trace("Save was determined to be unnecessary");
			}
			
			if ( log.isTraceEnabled() ) {
				log.trace(
						"Setting expire timeout on session [" + redisSession.getId() + "] to " + getMaxInactiveInterval());
			}
			jedis.expire(binaryId, getMaxInactiveInterval());

			error = false;
			
		} catch (IOException e) {
			log.error(e.getMessage());
			throw e;
		} finally {
			return error;
		}
	}

	public int getMaxInactiveInterval() {
		Context context = getContext();
		if (context == null) {
			return -1;
		}
		return context.getSessionTimeout() * 60;
	}

	@Override
	public void remove(Session session) {
		remove(session, false);
	}

	@Override
	public void remove(Session session, boolean update) {
		JedisCluster jedis = null;
		Boolean error = true;

		log.trace("Removing session ID : " + session.getId());

		try {
			jedis = acquireConnection();
			jedis.del(session.getId());
			error = false;
		} finally {
			if (jedis != null) {
				returnConnection(jedis, error);
			}
		}
	}

	public void afterRequest() {
		RedisSession redisSession = currentSession.get();
		if (redisSession != null) {
			try {
				if (redisSession.isValid()) {
					log.trace("Request with session completed, saving session " + redisSession.getId());
					save(redisSession, getAlwaysSaveAfterRequest());
				} else {
					log.trace("HTTP Session has been invalidated, removing :" + redisSession.getId());
					remove(redisSession);
				}
			} catch (Exception e) {
				log.error("Error storing/removing session", e);
			} finally {
				currentSession.remove();
				currentSessionId.remove();
				currentSessionIsPersisted.remove();
				log.trace("Session removed from ThreadLocal :" + redisSession.getIdInternal());
			}
		}
	}

	@Override
	public void processExpires() {
		// We are going to use Redis's ability to expire keys for session
		// expiration.

		// Do nothing.
	}

	private void initializeDatabaseConnection() throws LifecycleException {
		try {
			
			Properties clusterProps = new Properties();
			clusterProps.setProperty( RedisConfig.CLUSTER_PROPERTY, getHost() );
			
			if ( !Objects.isNull( getTimeout() )) {
				clusterProps.setProperty( RedisConfig.TIMEOUT_PROPERTY , getTimeout());
			}
			
			clusterProps.setProperty( RedisConfig.MAXATTE_PROPERTY , "6");
			
			if ( !Objects.isNull( getPassword() ) ) {
				clusterProps.setProperty( RedisConfig.PASSWORD_PROPERTY ,  getPassword());
			}
			
			this.connectionPool = new RedisClusterConnPool( connectionPoolConfig , clusterProps );

		} catch (Exception e) {
			e.printStackTrace();
			throw new LifecycleException("Error connecting to Redis", e);
		}
	}

	private void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException {

		if ( log.isInfoEnabled() ) {
			log.info("Attempting to use serializer :" + serializationStrategyClass);
		}
		serializer = (Serializer) Class.forName(serializationStrategyClass).newInstance();

		Loader loader = null;
		Context context = this.getContext();
		if (context != null) {
			loader = context.getLoader();
		}

		ClassLoader classLoader = null;

		if (loader != null) {
			classLoader = loader.getClassLoader();
		}
		serializer.setClassLoader(classLoader);
	}

	// Connection Pool Config Accessors

	// - from org.apache.commons.pool2.impl.GenericObjectPoolConfig
	private PoolConfig connectionPoolConfig = new PoolConfig();

	public int getConnectionPoolMaxTotal() {
		return this.connectionPoolConfig.getMaxTotal();
	}

	public void setConnectionPoolMaxTotal(int connectionPoolMaxTotal) {
		this.connectionPoolConfig.setMaxTotal(connectionPoolMaxTotal);
	}

	public int getConnectionPoolMaxIdle() {
		return this.connectionPoolConfig.getMaxIdle();
	}

	public void setConnectionPoolMaxIdle(int connectionPoolMaxIdle) {
		this.connectionPoolConfig.setMaxIdle(connectionPoolMaxIdle);
	}

	public int getConnectionPoolMinIdle() {
		return this.connectionPoolConfig.getMinIdle();
	}

	public void setConnectionPoolMinIdle(int connectionPoolMinIdle) {
		this.connectionPoolConfig.setMinIdle(connectionPoolMinIdle);
	}

	// - from org.apache.commons.pool2.impl.BaseObjectPoolConfig

	public boolean getLifo() {
		return this.connectionPoolConfig.getLifo();
	}

	public void setLifo(boolean lifo) {
		this.connectionPoolConfig.setLifo(lifo);
	}

	public long getMaxWaitMillis() {
		return this.connectionPoolConfig.getMaxWaitMillis();
	}

	public void setMaxWaitMillis(long maxWaitMillis) {
		this.connectionPoolConfig.setMaxWaitMillis(maxWaitMillis);
	}

	public long getMinEvictableIdleTimeMillis() {
		return this.connectionPoolConfig.getMinEvictableIdleTimeMillis();
	}

	public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
		this.connectionPoolConfig.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
	}

	public long getSoftMinEvictableIdleTimeMillis() {
		return this.connectionPoolConfig.getSoftMinEvictableIdleTimeMillis();
	}

	public void setSoftMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
		this.connectionPoolConfig.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
	}

	public int getNumTestsPerEvictionRun() {
		return this.connectionPoolConfig.getNumTestsPerEvictionRun();
	}

	public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
		this.connectionPoolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
	}

	public boolean getTestOnCreate() {
		return this.connectionPoolConfig.getTestOnCreate();
	}

	public void setTestOnCreate(boolean testOnCreate) {
		this.connectionPoolConfig.setTestOnCreate(testOnCreate);
	}

	public boolean getTestOnBorrow() {
		return this.connectionPoolConfig.getTestOnBorrow();
	}

	public void setTestOnBorrow(boolean testOnBorrow) {
		this.connectionPoolConfig.setTestOnBorrow(testOnBorrow);
	}

	public boolean getTestOnReturn() {
		return this.connectionPoolConfig.getTestOnReturn();
	}

	public void setTestOnReturn(boolean testOnReturn) {
		this.connectionPoolConfig.setTestOnReturn(testOnReturn);
	}

	public boolean getTestWhileIdle() {
		return this.connectionPoolConfig.getTestWhileIdle();
	}

	public void setTestWhileIdle(boolean testWhileIdle) {
		this.connectionPoolConfig.setTestWhileIdle(testWhileIdle);
	}

	public long getTimeBetweenEvictionRunsMillis() {
		return this.connectionPoolConfig.getTimeBetweenEvictionRunsMillis();
	}

	public void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
		this.connectionPoolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
	}

	public String getEvictionPolicyClassName() {
		return this.connectionPoolConfig.getEvictionPolicyClassName();
	}

	public void setEvictionPolicyClassName(String evictionPolicyClassName) {
		this.connectionPoolConfig.setEvictionPolicyClassName(evictionPolicyClassName);
	}

	public boolean getBlockWhenExhausted() {
		return this.connectionPoolConfig.getBlockWhenExhausted();
	}

	public void setBlockWhenExhausted(boolean blockWhenExhausted) {
		this.connectionPoolConfig.setBlockWhenExhausted(blockWhenExhausted);
	}

	public boolean getJmxEnabled() {
		return this.connectionPoolConfig.getJmxEnabled();
	}

	public void setJmxEnabled(boolean jmxEnabled) {
		this.connectionPoolConfig.setJmxEnabled(jmxEnabled);
	}

	public String getJmxNameBase() {
		return this.connectionPoolConfig.getJmxNameBase();
	}

	public void setJmxNameBase(String jmxNameBase) {
		this.connectionPoolConfig.setJmxNameBase(jmxNameBase);
	}

	public String getJmxNamePrefix() {
		return this.connectionPoolConfig.getJmxNamePrefix();
	}

	public void setJmxNamePrefix(String jmxNamePrefix) {
		this.connectionPoolConfig.setJmxNamePrefix(jmxNamePrefix);
	}
}

