/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.datasource.pooled;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * This is a simple, synchronous, thread-safe database connection pool.
 *
 * @author Clinton Begin
 */
// 带连接池的数据源， 成员变量通过读取xml文件赋值 （在XMLConfigBuilder类中完成读取）
public class PooledDataSource implements DataSource {

  private static final Log log = LogFactory.getLog(PooledDataSource.class);

  private final PoolState state = new PoolState(this);

  // 带连接池的数据源和不带连接池的数据源配置有相同的，
  // 本类中使用不带连接池的数据源对象创建Connection对象并返回。L476
  private final UnpooledDataSource dataSource;

  // OPTIONAL CONFIGURATION FIELDS
  // 最大活动连接数（默认为10）
  protected int poolMaximumActiveConnections = 10;
  // 最大空闲连接数（默认为5）即 任意时间存在的空闲连接数
  protected int poolMaximumIdleConnections = 5;
  // 最大可回收时间，即当达到最大活动链接数时，此时如果有程序获取连接，则检查最先使用的连接，看其是否超出了该时间，如果超出了该时间，则可以回收该连接。（默认20s）
  protected int poolMaximumCheckoutTime = 20000;
  // 当没有可用的连接时，重新尝试获取连接以及打印日志的时间间隔（默认20s），就是连接都被使用了，我要等一段时间再试试有没有连接可用。
  protected int poolTimeToWait = 20000;
  protected int poolMaximumLocalBadConnectionTolerance = 3;
  // 发送到数据库的侦测查询，用来验证连接是否正常工作，
  // 当poolPingEnabled=true时，这里需要替换成一条简短的sql。例如替换成 "select 1 from dual"
  protected String poolPingQuery = "NO PING QUERY SET";
  // 开启或禁用侦测查询
  protected boolean poolPingEnabled;
  // 配置poolPingQuery多长时间被用一次（即，连续两次验证连接是否可用 的间隔时间）
  protected int poolPingConnectionsNotUsedFor;

  // 一个int值， 用于检查数据源的属性是否变更过（即用户名，密码，Url 是否有改动）
  private int expectedConnectionTypeCode;

  public PooledDataSource() {
    dataSource = new UnpooledDataSource();
  }

  public PooledDataSource(UnpooledDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public PooledDataSource(String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  @Override
  // 获取连接池中的一个连接对象
  public Connection getConnection() throws SQLException {
    // 从PooledConnection中取出代理连接的对象。
    return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return popConnection(username, password).getProxyConnection();
  }

  @Override
  public void setLoginTimeout(int loginTimeout) {
    DriverManager.setLoginTimeout(loginTimeout);
  }

  @Override
  public int getLoginTimeout() {
    return DriverManager.getLoginTimeout();
  }

  @Override
  public void setLogWriter(PrintWriter logWriter) {
    DriverManager.setLogWriter(logWriter);
  }

  @Override
  public PrintWriter getLogWriter() {
    return DriverManager.getLogWriter();
  }

  // 更改驱动类型，则需要关闭连接池中所有连接。
  public void setDriver(String driver) {
    dataSource.setDriver(driver);
    forceCloseAll();
  }

  public void setUrl(String url) {
    dataSource.setUrl(url);
    forceCloseAll();
  }

  public void setUsername(String username) {
    dataSource.setUsername(username);
    forceCloseAll();
  }

  public void setPassword(String password) {
    dataSource.setPassword(password);
    forceCloseAll();
  }

  // 更改自动提交模式，则需要关闭连接池中所有连接。
  public void setDefaultAutoCommit(boolean defaultAutoCommit) {
    dataSource.setAutoCommit(defaultAutoCommit);
    forceCloseAll();
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    dataSource.setDefaultTransactionIsolationLevel(defaultTransactionIsolationLevel);
    forceCloseAll();
  }

  public void setDriverProperties(Properties driverProps) {
    dataSource.setDriverProperties(driverProps);
    forceCloseAll();
  }

  /**
   * Sets the default network timeout value to wait for the database operation to complete. See {@link Connection#setNetworkTimeout(java.util.concurrent.Executor, int)}
   *
   * @param milliseconds
   *          The time in milliseconds to wait for the database operation to complete.
   * @since 3.5.2
   */
  public void setDefaultNetworkTimeout(Integer milliseconds) {
    dataSource.setDefaultNetworkTimeout(milliseconds);
    forceCloseAll();
  }

  /**
   * The maximum number of active connections.
   *
   * @param poolMaximumActiveConnections The maximum number of active connections
   */
  public void setPoolMaximumActiveConnections(int poolMaximumActiveConnections) {
    this.poolMaximumActiveConnections = poolMaximumActiveConnections;
    forceCloseAll();
  }

  /**
   * The maximum number of idle connections.
   *
   * @param poolMaximumIdleConnections The maximum number of idle connections
   */
  public void setPoolMaximumIdleConnections(int poolMaximumIdleConnections) {
    this.poolMaximumIdleConnections = poolMaximumIdleConnections;
    forceCloseAll();
  }

  /**
   * The maximum number of tolerance for bad connection happens in one thread
   * which are applying for new {@link PooledConnection}.
   *
   * @param poolMaximumLocalBadConnectionTolerance
   * max tolerance for bad connection happens in one thread
   *
   * @since 3.4.5
   */
  public void setPoolMaximumLocalBadConnectionTolerance(
      int poolMaximumLocalBadConnectionTolerance) {
    this.poolMaximumLocalBadConnectionTolerance = poolMaximumLocalBadConnectionTolerance;
  }

  /**
   * The maximum time a connection can be used before it *may* be
   * given away again.
   *
   * @param poolMaximumCheckoutTime The maximum time
   */
  public void setPoolMaximumCheckoutTime(int poolMaximumCheckoutTime) {
    this.poolMaximumCheckoutTime = poolMaximumCheckoutTime;
    forceCloseAll();
  }

  /**
   * The time to wait before retrying to get a connection.
   *
   * @param poolTimeToWait The time to wait
   */
  public void setPoolTimeToWait(int poolTimeToWait) {
    this.poolTimeToWait = poolTimeToWait;
    forceCloseAll();
  }

  /**
   * The query to be used to check a connection.
   *
   * @param poolPingQuery The query
   */
  public void setPoolPingQuery(String poolPingQuery) {
    this.poolPingQuery = poolPingQuery;
    forceCloseAll();
  }

  /**
   * Determines if the ping query should be used.
   *
   * @param poolPingEnabled True if we need to check a connection before using it
   */
  public void setPoolPingEnabled(boolean poolPingEnabled) {
    this.poolPingEnabled = poolPingEnabled;
    forceCloseAll();
  }

  /**
   * If a connection has not been used in this many milliseconds, ping the
   * database to make sure the connection is still good.
   *
   * @param milliseconds the number of milliseconds of inactivity that will trigger a ping
   */
  // 这意思是，更改数据源的一个属性， 就需要销毁连接池中的所有连接。也就是需要重新生成连接池中的连接对象。
  public void setPoolPingConnectionsNotUsedFor(int milliseconds) {
    this.poolPingConnectionsNotUsedFor = milliseconds;
    forceCloseAll();
  }

  public String getDriver() {
    return dataSource.getDriver();
  }

  public String getUrl() {
    return dataSource.getUrl();
  }

  public String getUsername() {
    return dataSource.getUsername();
  }

  public String getPassword() {
    return dataSource.getPassword();
  }

  public boolean isAutoCommit() {
    return dataSource.isAutoCommit();
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return dataSource.getDefaultTransactionIsolationLevel();
  }

  public Properties getDriverProperties() {
    return dataSource.getDriverProperties();
  }

  /**
   * @since 3.5.2
   */
  public Integer getDefaultNetworkTimeout() {
    return dataSource.getDefaultNetworkTimeout();
  }

  public int getPoolMaximumActiveConnections() {
    return poolMaximumActiveConnections;
  }

  public int getPoolMaximumIdleConnections() {
    return poolMaximumIdleConnections;
  }

  public int getPoolMaximumLocalBadConnectionTolerance() {
    return poolMaximumLocalBadConnectionTolerance;
  }

  public int getPoolMaximumCheckoutTime() {
    return poolMaximumCheckoutTime;
  }

  public int getPoolTimeToWait() {
    return poolTimeToWait;
  }

  public String getPoolPingQuery() {
    return poolPingQuery;
  }

  public boolean isPoolPingEnabled() {
    return poolPingEnabled;
  }

  public int getPoolPingConnectionsNotUsedFor() {
    return poolPingConnectionsNotUsedFor;
  }

  /**
   * Closes all active and idle connections in the pool.
   */
  // 将缓存中，所有的活动的，空闲的连接都释放掉，所谓释放，就是调用conn.close （感觉这里说的存放连接对象的缓存 就是连接池）
  public void forceCloseAll() {
    synchronized (state) {
      expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
      // 释放activeConnections缓存中的连接对象
      for (int i = state.activeConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.activeConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          // Connection.close()的api说明中，建议在调用close之前，先调用commit或者rollback，估计这里调用rollback是这个考虑。
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          // 释放连接
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
      // 释放idleConnections缓存中的连接对象
      for (int i = state.idleConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.idleConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          // Connection.close()的api说明中，建议在调用close之前，先调用commit或者rollback，估计这里调用rollback是这个考虑。
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("PooledDataSource forcefully closed/removed all connections.");
    }
  }

  public PoolState getPoolState() {
    return state;
  }

  // 将入参连接一起后，取串的hash值 并返回。
  private int assembleConnectionTypeCode(String url, String username, String password) {
    return ("" + url + username + password).hashCode();
  }

  // 当请求用完数据库连接对象后，将连接对象放回连接池。
  // 若空闲连接集合中元素个数 没有达到最大空闲连接数，则将conn放到缓存，否则释放conn。
  protected void pushConnection(PooledConnection conn) throws SQLException {

    synchronized (state) {
      // 将conn从活动连接集合中移除
      state.activeConnections.remove(conn);
      // 若conn对象有效，进if
      if (conn.isValid()) {
        // 空闲连接集合中元素个数 < 最大空闲连接数 且 数据源属性没有变更过（即用户名，密码，Url等没有改动）， 进if，向空闲连接集合中添加元素
        if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
          // 累计 连接对象被使用的时长
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          // 若没有开启自动提交，则回滚，防止影响下一次使用 （感觉这个时候，正常的数据库操作都已经提交了，所以这里回滚对已提交的数据也没有影响。）
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }

          // 新建一个连接对象，并放到空闲连接集合中
          // （conn也是一个代理连接对象，这里给conn内封装的连接对象，生成了一个新的代理连接对象。
          // 而不是将原来的conn直接添加到缓存中， 这样可以使conn内封装的连接对象不会被原有的代理连接对象conn影响）
          PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
          state.idleConnections.add(newConn);
          newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
          newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
          // 将原有的代理连接设置为无效
          conn.invalidate();

          if (log.isDebugEnabled()) {
            log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
          }
          // 通知等待获取连接的线程（不去判断是否真的有线程在等待）在popConnection方法中等待获取连接
          state.notifyAll();
        }
        // 空闲连接集合中元素个数 > 最大空闲连接数，则释放conn对象
        else {
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          // 若没有开启自动提交，则回滚，防止影响下一次使用 （感觉这个时候，正常的数据库操作都已经提交了，所以这里回滚对已提交的数据也没有影响。）
          // Connection.close()的api说明中，建议在调用close之前，先调用commit或者rollback，估计这里调用rollback是这个考虑。
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          // 超出空闲连接限制，则直接释放当前连接
          conn.getRealConnection().close();
          if (log.isDebugEnabled()) {
            log.debug("Closed connection " + conn.getRealHashCode() + ".");
          }
          // 将原有的代理连接设置为无效
          conn.invalidate();
        }
      }
      // 若conn无效，则无效计数加1
      else {
        if (log.isDebugEnabled()) {
          log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
        }
        // 连接无效，则统计无效连接个数
        state.badConnectionCount++;
      }
    }
  }

  // 从空闲连接集合中取一个连接对象，并返回，
  // （若没有空闲的连接对象，则从活动连接集合中复用一个超时的连接，若没有超时的连接，则等待）
  private PooledConnection popConnection(String username, String password) throws SQLException {
    boolean countedWait = false;
    PooledConnection conn = null;
    // 用于计算 取出一个连接对象的时间
    long t = System.currentTimeMillis();
    int localBadConnectionCount = 0;

    while (conn == null) {
      synchronized (state) {
        // 若空闲连接集合中还有元素，则取第一个元素
        if (!state.idleConnections.isEmpty()) {
          // Pool has available connection
          conn = state.idleConnections.remove(0);
          if (log.isDebugEnabled()) {
            log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
          }
        } // 若空闲连接集合中没有元素
        else {
          // Pool does not have available connection
          // 若活动的连接数量 小于 最大活动连接数，则new一个连接出来 （这说明，当没有空闲连接时，再有请求进来，则判断是否到达最大活动连接数）
          if (state.activeConnections.size() < poolMaximumActiveConnections) {
            // Can create new connection
            conn = new PooledConnection(dataSource.getConnection(), this);
            if (log.isDebugEnabled()) {
              log.debug("Created connection " + conn.getRealHashCode() + ".");
            }
          } // 活动连接数量已达到最大值，则复用一个超时的连接，若没有超时的连接，则等待
          else {
            // Cannot create new connection
            // 取活动连接集合中的第一个元素，（因为第一个元素是最早添加进去的）
            PooledConnection oldestActiveConnection = state.activeConnections.get(0);
            // 该连接对象已经被使用的时间
            long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
            // 若该连接对象超出了配置的可使用时间，则将该连接对象从活动的连接集合中移除
            if (longestCheckoutTime > poolMaximumCheckoutTime) {
              // Can claim overdue connection
              // 过期的连接个数增加
              state.claimedOverdueConnectionCount++;
              // 累积 过期的时间
              state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;
              // 累积 使用的时间
              state.accumulatedCheckoutTime += longestCheckoutTime;
              // 将该连接对象从活动的连接集合中移除（并没有close该连接，只是移除，局部变量oldestActiveConnection等待被gc）
              state.activeConnections.remove(oldestActiveConnection);
              if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                try {
                  oldestActiveConnection.getRealConnection().rollback();
                } catch (SQLException e) {
                  /*
                     Just log a message for debug and continue to execute the following
                     statement like nothing happened.
                     Wrap the bad connection with a new PooledConnection, this will help
                     to not interrupt current executing thread and give current thread a
                     chance to join the next competition for another valid/good database
                     connection. At the end of this loop, bad {@link @conn} will be set as null.
                   */
                  log.debug("Bad connection. Could not roll back");
                }
              }
              // 将oldestActiveConnection.realConnection封装到新的代理类中。（相当于超时的连接对象被复用了）
              conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
              // 感觉这里设置的时间没用， 在下面的if中，这个conn的时间属性会被重新赋值，并重新添加到活动的连接对象集合中。
              conn.setCreatedTimestamp(oldestActiveConnection.getCreatedTimestamp());
              conn.setLastUsedTimestamp(oldestActiveConnection.getLastUsedTimestamp());
              // 将之前的连接对象设置为无效
              oldestActiveConnection.invalidate();
              if (log.isDebugEnabled()) {
                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
              }
            } // 若该连接对象未超出配置的可使用时间，则等待
            else {
              // Must wait
              try {
                if (!countedWait) {
                  // 需要等待的线程数量+1
                  state.hadToWaitCount++;
                  countedWait = true;
                }
                if (log.isDebugEnabled()) {
                  log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                }
                long wt = System.currentTimeMillis();
                state.wait(poolTimeToWait);
                // 累积 等待获取连接的时间
                state.accumulatedWaitTime += System.currentTimeMillis() - wt;
              } catch (InterruptedException e) {
                // 跳出while
                break;
              }
            }
          }
        }

        // 若上面得到的连接对象不为空，则检验该连接对象是否可用
        if (conn != null) {
          // ping to server and check the connection is valid or not
          // 若该连接对象可用，则将它添加到活动的连接对象集合
          if (conn.isValid()) {
            if (!conn.getRealConnection().getAutoCommit()) {
              conn.getRealConnection().rollback();
            }
            conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
            conn.setCheckoutTimestamp(System.currentTimeMillis());
            conn.setLastUsedTimestamp(System.currentTimeMillis());
            state.activeConnections.add(conn);
            // 获取到连接对象的请求数量+1
            state.requestCount++;
            // 累积 请求到一个连接对象的时间
            state.accumulatedRequestTime += System.currentTimeMillis() - t;
          }
          // 若该连接对象不可用，则不可用连接数量+1，若该数量超过8，则抛出异常。
          else {
            if (log.isDebugEnabled()) {
              log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
            }
            // 不可用连接数量+1
            state.badConnectionCount++;
            // 记录外层while循环，遇到的不可用连接数量
            localBadConnectionCount++;
            conn = null;

            // 若不可用连接数量超出可容忍的数量，则抛出异常
            if (localBadConnectionCount > (poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance)) {
              if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Could not get a good connection to the database.");
              }
              throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
            }
          }
        }
      }

    }

    // 从上面的while循环退出，如果conn为null，则抛出异常
    if (conn == null) {
      if (log.isDebugEnabled()) {
        log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
      }
      throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
    }

    return conn;
  }

  /**
   * Method to check to see if a connection is still usable
   *
   * @param conn - the connection to check
   * @return True if the connection is still usable
   */
  // 检查入参conn对象是否可用，其实就是使用conn执行一条简单的sql来判断该conn是否可用 (实际是检查conn.realConnection是否可用)
  // 返回true表示可用 （本方法中没有关闭conn.realConnection）
  protected boolean pingConnection(PooledConnection conn) {
    boolean result = true;

    try {
      // 若连接关闭， 则result=false表示不可用。
      result = !conn.getRealConnection().isClosed();
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
      }
      // 表示不可用
      result = false;
    }

    // connection对象没有关闭， 则进if
    if (result) {
      // 若开启了侦测查询，则需要试下conn对象是否可用。
      if (poolPingEnabled) {
        // 若conn的空闲时间 超出了配置的可空闲时间， 则真的需要验证下conn对象是否可用， 否则，不用验证。
        if (poolPingConnectionsNotUsedFor >= 0 && conn.getTimeElapsedSinceLastUse() > poolPingConnectionsNotUsedFor) {
          try {
            if (log.isDebugEnabled()) {
              log.debug("Testing connection " + conn.getRealHashCode() + " ...");
            }

            // 执行侦测查询语句（其实就是执行一条简单的sql）

            Connection realConn = conn.getRealConnection();
            /**
             * 下面这段try代码等价于
             *             Statement statement = realConn.createStatement();
             *             ResultSet rs = statement.executeQuery(poolPingQuery);
             *             rs.close();
             *             statement.close();
             */
            try (Statement statement = realConn.createStatement()) {
              statement.executeQuery(poolPingQuery).close();
            }

            // 若realConn不是自动提交，则回滚（这个回滚作用应该是：不对表产生影响，因为执行的是侦测查询sql）
            if (!realConn.getAutoCommit()) {
              realConn.rollback();
            }
            result = true;
            if (log.isDebugEnabled()) {
              log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
            }
          } catch (Exception e) {
            log.warn("Execution of ping query '" + poolPingQuery + "' failed: " + e.getMessage());
            try {
              conn.getRealConnection().close();
            } catch (Exception e2) {
              //ignore
            }
            // 执行sql异常，则表示连接不可用
            result = false;
            if (log.isDebugEnabled()) {
              log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
            }
          }
        }
      }
    }
    return result;
  }

  /**
   * Unwraps a pooled connection to get to the 'real' connection
   *
   * @param conn - the pooled connection to unwrap
   * @return The 'real' connection
   */
  // 从PooledConnection对象中取realConnection属性的值，并返回（入参conn是PooledConnection类的对象）
  public static Connection unwrapConnection(Connection conn) {
    // 若conn是由newProxyInstance方法动态生成的代理连接对象，进if
    if (Proxy.isProxyClass(conn.getClass())) {
      // 获取实现了InvocationHandler接口的类对象 （就是PooledConnection类的对象）
      InvocationHandler handler = Proxy.getInvocationHandler(conn);
      if (handler instanceof PooledConnection) {
        // 返回realConnection对象
        return ((PooledConnection) handler).getRealConnection();
      }
    }
    return conn;
  }

  @Override
  protected void finalize() throws Throwable {
    forceCloseAll();
    super.finalize();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return false;
  }

  @Override
  public Logger getParentLogger() {
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  }

}
