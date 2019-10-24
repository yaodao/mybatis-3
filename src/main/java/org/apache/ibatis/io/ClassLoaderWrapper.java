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

import java.io.InputStream;
import java.net.URL;

/**
 * A class to wrap access to multiple class loaders making them work as one
 *
 * @author Clinton Begin
 */
// 该类实现 使用类加载器在classpath下找资源文件，并将资源文件转换成URL或者InputStream，供外部的代码使用。
public class ClassLoaderWrapper {

  ClassLoader defaultClassLoader;
  ClassLoader systemClassLoader;

  ClassLoaderWrapper() {
    try {
      // 获取AppClassLoader（系统类装载器）
      systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (SecurityException ignored) {
      // AccessControlException on Google App Engine
    }
  }

  /**
   * 下面的方法中，处理的资源文件，都是在classpath下的资源文件。
   */

  /**
   * Get a resource as a URL using the current class path
   *
   * @param resource - the resource to locate
   * @return the resource or null
   */
  // 将文件路径转成URL对象，并返回该URL对象
  public URL getResourceAsURL(String resource) {
    return getResourceAsURL(resource, getClassLoaders(null));
  }

  /**
   * Get a resource from the classpath, starting with a specific class loader
   *
   * @param resource    - the resource to find
   * @param classLoader - the first classloader to try
   * @return the stream or null
   */
  // 将文件路径转成URL对象，并返回该URL对象
  public URL getResourceAsURL(String resource, ClassLoader classLoader) {
    return getResourceAsURL(resource, getClassLoaders(classLoader));
  }

  /**
   * Get a resource from the classpath
   *
   * @param resource - the resource to find
   * @return the stream or null
   */
  // 在classpath下找资源文件，并将资源文件转成InputStream对象，
  // 返回该InputStream对象
  public InputStream getResourceAsStream(String resource) {
    return getResourceAsStream(resource, getClassLoaders(null));
  }

  /**
   * Get a resource from the classpath, starting with a specific class loader
   *
   * @param resource    - the resource to find
   * @param classLoader - the first class loader to try
   * @return the stream or null
   */
  // 在classpath下找资源文件，并将资源文件转成InputStream对象，
  // 返回该InputStream对象
  public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
    return getResourceAsStream(resource, getClassLoaders(classLoader));
  }

  /**
   * Find a class on the classpath (or die trying)
   *
   * @param name - the class to look for
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  // 加载name指定的类
  public Class<?> classForName(String name) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(null));
  }

  /**
   * Find a class on the classpath, starting with a specific classloader (or die trying)
   *
   * @param name        - the class to look for
   * @param classLoader - the first classloader to try
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  // 加载name指定的类，先用入参classLoader加载
  public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(classLoader));
  }

  /**
   * Try to get a resource from a group of classloaders
   *
   * @param resource    - the resource to get （资源文件，例如： "org/apache/ibatis/databases/jpetstore/jpetstore-hsqldb.properties"）
   * @param classLoader - the classloaders to examine
   * @return the resource or null
   */
  // 将文件转成InputStream对象，并返回该InputStream对象。（即 使用类加载器将入参resource转成InputStream对象，之后可以直接从流对象中读取文件内容）
  // 若在classpath下找不到该文件，则返回null
  InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
    for (ClassLoader cl : classLoader) {
      if (null != cl) {

        // try to find the resource as passed
        InputStream returnValue = cl.getResourceAsStream(resource);

        // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
        if (null == returnValue) {
          returnValue = cl.getResourceAsStream("/" + resource);
        }

        if (null != returnValue) {
          return returnValue;
        }
      }
    }
    return null;
  }

  /**
   * Get a resource as a URL using the current class path
   *
   * @param resource    - the resource to locate （一般是 .properties文件，例如 "org/apache/ibatis/databases/jpetstore/jpetstore-hsqldb.properties"）
   * @param classLoader - the class loaders to examine
   * @return the resource or null
   */

  // 将文件路径转成URL对象，并返回该URL对象。（即 使用类加载器将入参resource转成URL对象）
  // 若在classpath下找不到该文件，则返回null
  URL getResourceAsURL(String resource, ClassLoader[] classLoader) {

    URL url;

    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        // look for the resource as passed in...
        // 返回一个URL, 该URL是对资源resource的定位
        url = cl.getResource(resource);

        // ...but some class loaders want this leading "/", so we'll add it
        // and try again if we didn't find the resource
        if (null == url) {
          url = cl.getResource("/" + resource);
        }

        // "It's always in the last place I look for it!"
        // ... because only an idiot would keep looking for it after finding it, so stop looking already.
        if (null != url) {
          return url;
        }

      }

    }

    // didn't find it anywhere.
    return null;

  }

  /**
   * Attempt to load a class from a group of classloaders
   *
   * @param name        - the class to load
   * @param classLoader - the group of classloaders to examine
   * @return the class
   * @throws ClassNotFoundException - Remember the wisdom of Judge Smails: Well, the world needs ditch diggers, too.
   */
  // 使用类加载器数组中的类加载器，加载name对应的clazz，
  // 都加载不到则抛出异常。
  Class<?> classForName(String name, ClassLoader[] classLoader) throws ClassNotFoundException {

    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        try {

          Class<?> c = Class.forName(name, true, cl);

          if (null != c) {
            return c;
          }

        } catch (ClassNotFoundException e) {
          // we'll ignore this until all classloaders fail to locate the class
        }

      }

    }

    throw new ClassNotFoundException("Cannot find class: " + name);

  }


  /**
   * 返回类加载器的数组
   * @param classLoader 数组的第一个元素（类加载器）
   * @return
   */
  ClassLoader[] getClassLoaders(ClassLoader classLoader) {
    return new ClassLoader[]{
        classLoader,
        defaultClassLoader,
        // 当前线程中保存的类加载器
        Thread.currentThread().getContextClassLoader(),
        // 加载当前类的类加载器
        getClass().getClassLoader(),
        systemClassLoader};
  }

}
