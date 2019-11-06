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
package org.apache.ibatis.datasource.unpooled;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.io.Resources;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
// 不使用连接池，每次连接数据库，都会新建一个Connection对象
public class UnpooledDataSource implements DataSource {

  private ClassLoader driverClassLoader;
  private Properties driverProperties;
  // key是Driver的实现类的类全名， value是Driver的实现类的对象
  private static Map<String, Driver> registeredDrivers = new ConcurrentHashMap<>();

  // jdbc具体的驱动类的全名
  private String driver;
  private String url;
  private String username;
  private String password;

  private Boolean autoCommit;
  private Integer defaultTransactionIsolationLevel;
  private Integer defaultNetworkTimeout;

  static {
    // 获取全部已加载的jdbc驱动对象（就是获取Driver的实现类的对象）， http://c.biancheng.net/view/4782.html
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      // 将(drive对象的类全名， driver对象)添加到成员变量registeredDrivers中
      registeredDrivers.put(driver.getClass().getName(), driver);
    }
  }

  public UnpooledDataSource() {
  }

  public UnpooledDataSource(String driver, String url, String username, String password) {
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public UnpooledDataSource(String driver, String url, Properties driverProperties) {
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  @Override
  // 获取到数据库的连接对象
  public Connection getConnection() throws SQLException {
    return doGetConnection(username, password);
  }

  @Override
  // 获取到数据库的连接对象（用户名密码就是连接数据库用的））
  public Connection getConnection(String username, String password) throws SQLException {
    return doGetConnection(username, password);
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

  public ClassLoader getDriverClassLoader() {
    return driverClassLoader;
  }

  public void setDriverClassLoader(ClassLoader driverClassLoader) {
    this.driverClassLoader = driverClassLoader;
  }

  public Properties getDriverProperties() {
    return driverProperties;
  }

  public void setDriverProperties(Properties driverProperties) {
    this.driverProperties = driverProperties;
  }

  public synchronized String getDriver() {
    return driver;
  }

  public synchronized void setDriver(String driver) {
    this.driver = driver;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Boolean isAutoCommit() {
    return autoCommit;
  }

  public void setAutoCommit(Boolean autoCommit) {
    this.autoCommit = autoCommit;
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return defaultTransactionIsolationLevel;
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    this.defaultTransactionIsolationLevel = defaultTransactionIsolationLevel;
  }

  /**
   * @since 3.5.2
   */
  public Integer getDefaultNetworkTimeout() {
    return defaultNetworkTimeout;
  }

  /**
   * Sets the default network timeout value to wait for the database operation to complete. See {@link Connection#setNetworkTimeout(java.util.concurrent.Executor, int)}
   *
   * @param defaultNetworkTimeout
   *          The time in milliseconds to wait for the database operation to complete.
   * @since 3.5.2
   */
  public void setDefaultNetworkTimeout(Integer defaultNetworkTimeout) {
    this.defaultNetworkTimeout = defaultNetworkTimeout;
  }

  // 获取到数据库的连接对象 （用户名密码就是连接数据库用的）
  private Connection doGetConnection(String username, String password) throws SQLException {
    Properties props = new Properties();
    if (driverProperties != null) {
      // 使用driverProperties中的（key，value）值，替换props中原有的value值。
      props.putAll(driverProperties);
    }
    if (username != null) {
      // 添加（"user", username）
      props.setProperty("user", username);
    }
    if (password != null) {
      // 添加（"password", password）
      props.setProperty("password", password);
    }
    // 获取到数据库的连接对象
    return doGetConnection(props);
  }

  // 获取到数据库的连接对象
  private Connection doGetConnection(Properties properties) throws SQLException {
    // 实例化jdbc的具体驱动类
    initializeDriver();
    // 创建到数据库的连接对象
    Connection connection = DriverManager.getConnection(url, properties);
    // 配置Connection对象的属性
    configureConnection(connection);
    return connection;
  }

  // 判断成员变量registeredDrivers中， driver串 对应的驱动对象是否存在，
  // 若不存在，则加载该driver串对应的类，并添加到成员变量registeredDrivers中，并向DriverManager注册该driver对象
  private synchronized void initializeDriver() throws SQLException {
    // 若registeredDrivers中不包含该driver驱动，则进if
    if (!registeredDrivers.containsKey(driver)) {
      Class<?> driverType;
      try {
        // 加载driver串对应的clazz
        if (driverClassLoader != null) {
          driverType = Class.forName(driver, true, driverClassLoader);
        } else {
          driverType = Resources.classForName(driver);
        }
        // DriverManager requires the driver to be loaded via the system ClassLoader.
        // http://www.kfu.com/~nsayer/Java/dyn-jdbc.html
        // 生成该驱动的一个对象
        Driver driverInstance = (Driver)driverType.getDeclaredConstructor().newInstance();
        // 向DriverManager注册该驱动对象
        DriverManager.registerDriver(new DriverProxy(driverInstance));

        // 成员变量registeredDrivers中添加（驱动类全名，驱动对象）
        registeredDrivers.put(driver, driverInstance);
      } catch (Exception e) {
        throw new SQLException("Error setting driver on UnpooledDataSource. Cause: " + e);
      }
    }
  }

  // 配置Connection对象的属性 ，包括最大连接时间，自动提交状态，隔离级别
  // （暂时认为每个conn对象都可以单独设置自己的属性值，从而影响该次连接）
  private void configureConnection(Connection conn) throws SQLException {
    if (defaultNetworkTimeout != null) {
      // 设置当前连接对象conn，等待数据库响应的最长时间
      conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), defaultNetworkTimeout);
    }
    if (autoCommit != null && autoCommit != conn.getAutoCommit()) {
      // 设置当前连接对象conn 的自动提交状态 （暂时认为每个conn对象都可以单独设置自己的自动提交状态值）
      conn.setAutoCommit(autoCommit);
    }
    if (defaultTransactionIsolationLevel != null) {
      // 设置当前连接对象conn 的隔离级别
      conn.setTransactionIsolation(defaultTransactionIsolationLevel);
    }
  }

  // Driver对象的代理类
  // （就是简单的包装， 调用DriverProxy的方法，实际就是调用成员变量driver的方法）
  private static class DriverProxy implements Driver {
    private Driver driver;

    DriverProxy(Driver d) {
      this.driver = d;
    }

    @Override
    public boolean acceptsURL(String u) throws SQLException {
      return this.driver.acceptsURL(u);
    }

    @Override
    public Connection connect(String u, Properties p) throws SQLException {
      return this.driver.connect(u, p);
    }

    @Override
    public int getMajorVersion() {
      return this.driver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
      return this.driver.getMinorVersion();
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
      return this.driver.getPropertyInfo(u, p);
    }

    @Override
    public boolean jdbcCompliant() {
      return this.driver.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() {
      return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  @Override
  public Logger getParentLogger() {
    // requires JDK version 1.6
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  }

}
