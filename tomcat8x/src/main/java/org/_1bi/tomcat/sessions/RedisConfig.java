package org._1bi.tomcat.sessions;

public interface RedisConfig {

    /**
     * DEFAULT_HOST
     */
    public static final String DEFAULT_HOST = "localhost";
    /**
     * DEFAULT_PORT
     */
    public static final int DEFAULT_PORT = 6379;
    /**
     * DEFAULT_TIMEOUT
     */
    public static final int DEFAULT_TIMEOUT = 2000;
    /**
     * DEFAULT_DATABASE
     */
    public static final int DEFAULT_DATABASE = 0;
    /**
     * DEFAULT_PASSWORD
     */
    public static final String DEFAULT_PASSWORD = null;
    /**
     * DEFAULT_CLIENTNAME
     */
    public static final String DEFAULT_CLIENTNAME = null;
    /**
     * DEFAULT_MAXATTE
     */
    public static final int DEFAULT_MAXATTE = 5;
    /**
     * ADDRESS_PROPERTY
     */
    public static final String ADDRESS_PROPERTY = "address";
    /**
     * TIMEOUT_PROPERTY
     */
    public static final String TIMEOUT_PROPERTY = "timeout";
    /**
     * CONN_TIMEOUT_PROPERTY
     */
    public static final String CONN_TIMEOUT_PROPERTY = "connectionTimeout";
    /**
     * SO_TIMEOUT_PROPERTY
     */
    public static final String SO_TIMEOUT_PROPERTY = "soTimeout";
    /**
     * DATABASE_PROPERTY
     */
    public static final String DATABASE_PROPERTY = "database";
    /**
     * PASSWORD_PROPERTY
     */
    public static final String PASSWORD_PROPERTY = "password";
    /**
     * CLIENTNAME_PROPERTY
     */
    public static final String CLIENTNAME_PROPERTY = "clientName";
    /**
     * MASTERNAME_PROPERTY
     */
    public static final String MASTERNAME_PROPERTY = "masterName";
    /**
     * SENTINELS_PROPERTY
     */
    public static final String SENTINELS_PROPERTY = "sentinels";
    /**
     * CLUSTER_PROPERTY
     */
    public static final String CLUSTER_PROPERTY = "cluster";
    /**
     * MAXATTE_PROPERTY
     */
    public static final String MAXATTE_PROPERTY = "maxAttempts";

}
