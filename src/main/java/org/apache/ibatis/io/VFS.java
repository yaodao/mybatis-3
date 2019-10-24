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
package org.apache.ibatis.io;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * Provides a very simple API for accessing resources within an application server.
 *
 * @author Ben Gunter
 */
public abstract class VFS {
  private static final Log log = LogFactory.getLog(VFS.class);

  /** The built-in implementations. */
  // 内部已有的VFS实现类
  public static final Class<?>[] IMPLEMENTATIONS = { JBoss6VFS.class, DefaultVFS.class };

  /** The list to which implementations are added by {@link #addImplClass(Class)}. */
  // 存放用户自定义的VFS实现类
  public static final List<Class<? extends VFS>> USER_IMPLEMENTATIONS = new ArrayList<>();

  /** Singleton instance holder. */
  // 这个类就是持有一个VFS实现类的对象（该VFS实现类的对象是单例）
  private static class VFSHolder {
    // 持有一个VFS实现类的对象
    static final VFS INSTANCE = createVFS();

    @SuppressWarnings("unchecked")
    // 创建一个有效的VFS的实现类的对象，并返回
    static VFS createVFS() {
      // 向impls添加用户自定义的VFS实现类和内部已有的实现类
      // （自定义的类在内部已有类的前面，表示会优先创建自定义类的对象）
      List<Class<? extends VFS>> impls = new ArrayList<>();
      impls.addAll(USER_IMPLEMENTATIONS);
      impls.addAll(Arrays.asList((Class<? extends VFS>[]) IMPLEMENTATIONS));

      // Try each implementation class until a valid one is found
      // 创建一个有效的VFS的实现类对象
      VFS vfs = null;
      // 当vfs对象有效时，跳出for循环，所以当找到第一个有效的vfs对象时，就跳出了循环。
      for (int i = 0; vfs == null || !vfs.isValid(); i++) {
        Class<? extends VFS> impl = impls.get(i);
        try {
          // 反射生成VFS类的对象。
          vfs = impl.getDeclaredConstructor().newInstance();
          // 验证vfs对象是否合法
          if (!vfs.isValid()) {
            if (log.isDebugEnabled()) {
              log.debug("VFS implementation " + impl.getName() +
                  " is not valid in this environment.");
            }
          }
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
          log.error("Failed to instantiate " + impl, e);
          return null;
        }
      }

      if (log.isDebugEnabled()) {
        log.debug("Using VFS adapter " + vfs.getClass().getName());
      }

      return vfs;
    }
  }

  /**
   * Get the singleton {@link VFS} instance. If no {@link VFS} implementation can be found for the
   * current environment, then this method returns null.
   */
  // 获取一个VFS实现类的对象
  public static VFS getInstance() {
    return VFSHolder.INSTANCE;
  }

  /**
   * Adds the specified class to the list of {@link VFS} implementations. Classes added in this
   * manner are tried in the order they are added and before any of the built-in implementations.
   *
   * @param clazz The {@link VFS} implementation class to add.
   */
  // 把VFS实现类的clazz对象添加到成员变量USER_IMPLEMENTATIONS中
  public static void addImplClass(Class<? extends VFS> clazz) {
    if (clazz != null) {
      USER_IMPLEMENTATIONS.add(clazz);
    }
  }

  /** Get a class by name. If the class is not found then return null. */
  // 返回className对应的clazz对象
  protected static Class<?> getClass(String className) {
    try {
      // 加载className类，只链接不执行初始化
      return Thread.currentThread().getContextClassLoader().loadClass(className);
//      return ReflectUtil.findClass(className);
    } catch (ClassNotFoundException e) {
      if (log.isDebugEnabled()) {
        log.debug("Class not found: " + className);
      }
      return null;
    }
  }

  /**
   * Get a method by name and parameter types. If the method is not found then return null.
   *
   * @param clazz The class to which the method belongs.
   * @param methodName The name of the method.
   * @param parameterTypes The types of the parameters accepted by the method.
   */
  // 通过名字和参数类型，从clazz中获取方法对象，如果没有返回null
  protected static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
    if (clazz == null) {
      return null;
    }
    try {
      return clazz.getMethod(methodName, parameterTypes);
    } catch (SecurityException e) {
      log.error("Security exception looking for method " + clazz.getName() + "." + methodName + ".  Cause: " + e);
      return null;
    } catch (NoSuchMethodException e) {
      log.error("Method not found " + clazz.getName() + "." + methodName + "." + methodName + ".  Cause: " + e);
      return null;
    }
  }

  /**
   * Invoke a method on an object and return whatever it returns.
   *
   * @param method The method to invoke.
   * @param object The instance or class (for static methods) on which to invoke the method.
   * @param parameters The parameters to pass to the method.
   * @return Whatever the method returns.
   * @throws IOException If I/O errors occur
   * @throws RuntimeException If anything else goes wrong
   */
  @SuppressWarnings("unchecked")
  // 调用object的method方法，返回调用结果
  protected static <T> T invoke(Method method, Object object, Object... parameters)
      throws IOException, RuntimeException {
    try {
      return (T) method.invoke(object, parameters);
    } catch (IllegalArgumentException | IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof IOException) {
        throw (IOException) e.getTargetException();
      } else {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Get a list of {@link URL}s from the context classloader for all the resources found at the
   * specified path.
   *
   * @param path The resource path.
   * @return A list of {@link URL}s, as returned by {@link ClassLoader#getResources(String)}.
   * @throws IOException If I/O errors occur
   */

  /**
   * 将path能定位的所有资源，都转成URL对象，添加到集合，并返回该集合。
   * 例如：
   * path="org/springframework"
   *
   * 返回URL对象如下： （可以看出，由path可以定位到所有 含有该path的jar包，而且path越长，能定位的jar包越少，也就是越精确）
   * （这里的jar包含有该path，意思是jar包中，某个包名以path做前缀，该jar包就符合条件）
   * jar:file:/D:/repository/org/springframework/spring-core/5.1.5.RELEASE/spring-core-5.1.5.RELEASE.jar!/org/springframework
   * jar:file:/D:/repository/org/springframework/spring-web/5.1.5.RELEASE/spring-web-5.1.5.RELEASE.jar!/org/springframework
   * jar:file:/D:/repository/org/springframework/spring-beans/5.1.5.RELEASE/spring-beans-5.1.5.RELEASE.jar!/org/springframework
   * jar:file:/D:/repository/org/springframework/spring-webmvc/5.1.5.RELEASE/spring-webmvc-5.1.5.RELEASE.jar!/org/springframework
   *
   */
  protected static List<URL> getResources(String path) throws IOException {
    return Collections.list(Thread.currentThread().getContextClassLoader().getResources(path));
  }

  /** Return true if the {@link VFS} implementation is valid for the current environment. */
  public abstract boolean isValid();

  /**
   * Recursively list the full resource path of all the resources that are children of the
   * resource identified by a URL.
   *
   * @param url The URL that identifies the resource to list.
   * @param forPath The path to the resource that is identified by the URL. Generally, this is the
   *            value passed to {@link #getResources(String)} to get the resource URL.
   * @return A list containing the names of the child resources.
   * @throws IOException If I/O errors occur
   */
  protected abstract List<String> list(URL url, String forPath) throws IOException;

  /**
   * Recursively list the full resource path of all the resources that are children of all the
   * resources found at the specified path.
   *
   * @param path The path of the resource(s) to list.
   * @return A list containing the names of the child resources.
   * @throws IOException If I/O errors occur
   */

  /**
   * 查找由app加载器加载的类中，包名以path开头的所有类。并返回
   *
   * 举例： path = "net/sf/cglib/util"
   * 则返回集合中元素类似：
   * net/sf/cglib/util/ParallelSorter.class
   * net/sf/cglib/util/ParallelSorterEmitter.class
   */
  public List<String> list(String path) throws IOException {
    List<String> names = new ArrayList<>();
    // getResources(path) 将查找含有path的jar包，并返回代表该jar包的URL对象的列表
    for (URL url : getResources(path)) {
      // 将满足条件的文件加入names （list()方法会遍历 url代表的jar包中的文件，将路径以path开头的那些文件放到集合中返回。）
      names.addAll(list(url, path));
    }
    return names;
  }
}
