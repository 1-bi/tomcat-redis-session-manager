# Redis Session Manager for Apache Tomcat

## 1. Overview

An session manager implementation that stores sessions in Redis for easy distribution of requests across a cluster of Tomcat servers. Sessions are implemented as as non-sticky--that is, each request is able to go to any server in the cluster (unlike the Apache provided Tomcat clustering setup.)

Sessions are stored into Redis immediately upon creation for use by other servers. Sessions are loaded as requested directly from Redis (but subsequent requests for the session during the same request context will return a ThreadLocal cache rather than hitting Redis multiple times.) In order to prevent collisions (and lost writes) as much as possible, session data is only updated in Redis if the session has been modified.

The manager relies on the native expiration capability of Redis to expire keys for automatic session expiration to avoid the overhead of constantly searching the entire list of sessions for expired sessions.

Data stored in the session must be Serializable.

* [Source Project : "tomcat-redis-session-manager"](https://github.com/jcoleman/tomcat-redis-session-manager)


##### Support this project!

This project is open project. If your business depends on Tomcat and persistent sessions and you need a specific feature,please contact me via email at vison_ruan@126.com. 


## 2. Tomcat Versions supported

  * Tomcat 8.5 (Branch: * tomcat8x)

## 3. Architecture

* RedisSessionManager: provides the session creation, saving, and loading functionality.
* RedisClusterSessionManager: provides the redis cluster implement for SessionManager
* RedisSessionHandlerValve: ensures that sessions are saved after a request is finished processing.

Note: this architecture differs from the Apache PersistentManager implementation which implements persistent sticky sessions. Because that implementation expects all requests from a specific session to be routed to the same server, the timing persistence of sessions is non-deterministic since it is primarily for failover capabilities.

## 4. Useage 

#### 4.1 Deploys jars
Copy the following files into the `TOMCAT_BASE/lib` directory:

* tomcat-redis-session-manager-[VERSION].jar
* jedis-2.9.3.jar
* commons-pool2-2.9.jar
* slf4j-jdk14-1.7.22.jar


### 4.2 Single Redis Usage

Add the following into your Tomcat context.xml (or the context block of the server.xml if applicable.)
```
    <Valve className="com.orangefunction.tomcat.redissessions.RedisSessionHandlerValve" />
    <Manager className="com.orangefunction.tomcat.redissessions.RedisSessionManager"
             host="localhost" <!-- optional: defaults to "localhost" -->
             port="6379" <!-- optional: defaults to "6379" -->
             database="0" <!-- optional: defaults to "0" -->
             maxInactiveInterval="60" <!-- optional: defaults to "60" (in seconds) -->
             sessionPersistPolicies="PERSIST_POLICY_1,PERSIST_POLICY_2,.." <!-- optional -->
             sentinels="sentinel-host-1:port,sentinel-host-2:port,.." <!-- optional --> />
```
The Valve must be declared before the Manager.

Reboot the server, and sessions should now be stored in Redis.

#### Attributes Support (Redis standalone) 

- abc : dkii



### 4.3 Cluster Redis Usage

Add the following into your Tomcat context.xml (or the context block of the server.xml if applicable.)
```
    <Valve className="com.orangefunction.tomcat.redissessions.RedisSessionHandlerValve" />
    <Manager className="com.orangefunction.tomcat.redissessions.RedisClusterSessionManager"
             host="[ip1]:[port1],[ip2]:[port2],[ip3]:[port3]" <!-- optional: defaults to "localhost" -->
             maxInactiveInterval="60" <!-- optional: defaults to "60" (in seconds) -->
             sessionPersistPolicies="PERSIST_POLICY_1,PERSIST_POLICY_2,.." <!-- optional -->
             sentinels="sentinel-host-1:port,sentinel-host-2:port,.." <!-- optional --> />
```
The Valve must be declared before the Manager.

Reboot the server, and sessions should now be stored in Redis.

#### Attributes Support (Redis cluster) 

- abc : dkii


Connection Pool Configuration
-----------------------------

All of the configuration options from both `org.apache.commons.pool2.impl.GenericObjectPoolConfig` and `org.apache.commons.pool2.impl.BaseObjectPoolConfig` are also configurable for the Redis connection pool used by the session manager. To configure any of these attributes (e.g., `maxIdle` and `testOnBorrow`) just use the config attribute name prefixed with `connectionPool` (e.g., `connectionPoolMaxIdle` and `connectionPoolTestOnBorrow`) and set the desired value in the `<Manager>` declaration in your Tomcat context.xml.

Session Change Tracking
-----------------------

As noted in the "Overview" section above, in order to prevent colliding writes, the Redis Session Manager only serializes the session object into Redis if the session object has changed (it always updates the expiration separately however.) This dirty tracking marks the session as needing serialization according to the following rules:

* Calling `session.removeAttribute(key)` always marks the session as dirty (needing serialization.)
* Calling `session.setAttribute(key, newAttributeValue)` marks the session as dirty if any of the following are true:
    * `previousAttributeValue == null && newAttributeValue != null`
    * `previousAttributeValue != null && newAttributeValue == null`
    * `!newAttributeValue.getClass().isInstance(previousAttributeValue)`
    * `!newAttributeValue.equals(previousAttributeValue)`

This feature can have the unintended consequence of hiding writes if you implicitly change a key in the session or if the object's equality does not change even though the key is updated. For example, assuming the session already contains the key `"myArray"` with an Array instance as its corresponding value, and has been previously serialized, the following code would not cause the session to be serialized again:

    List myArray = session.getAttribute("myArray");
    myArray.add(additionalArrayValue);

If your code makes these kind of changes, then the RedisSession provides a mechanism by which you can mark the session as dirty in order to guarantee serialization at the end of the request. For example:

    List myArray = session.getAttribute("myArray");
    myArray.add(additionalArrayValue);
    session.setAttribute("__changed__");

In order to not cause issues with an application that may already use the key `"__changed__"`, this feature is disabled by default. To enable this feature, simple call the following code in your application's initialization:

    RedisSession.setManualDirtyTrackingSupportEnabled(true);

This feature also allows the attribute key used to mark the session as dirty to be changed. For example, if you executed the following:

    RedisSession.setManualDirtyTrackingAttributeKey("customDirtyFlag");

Then the example above would look like this:

    List myArray = session.getAttribute("myArray");
    myArray.add(additionalArrayValue);
    session.setAttribute("customDirtyFlag");

