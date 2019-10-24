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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * A default implementation of {@link VFS} that works for most application servers.
 *
 * @author Ben Gunter
 */
public class DefaultVFS extends VFS {
  private static final Log log = LogFactory.getLog(DefaultVFS.class);

  /** The magic header that indicates a JAR (ZIP) file. */
  private static final byte[] JAR_MAGIC = { 'P', 'K', 3, 4 };

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  /**
   * 过滤url代表的jar包中的文件，将路径以path开头的那些文件放到集合中，返回这个集合。
   *
   * 例如：
   * 入参 url = "jar:file:/D:/repository/cglib/cglib/3.2.10/cglib-3.2.10.jar!/net/sf/cglib/util"，   （.jar后面的字符有多少无所谓，在方法中会被截断，只要.jar之前的字符）
   * path = "net/sf/cglib/util"
   * 则返回结果为：
   * net/sf/cglib/util/ParallelSorter.class
   * net/sf/cglib/util/ParallelSorterEmitter.class
   * net/sf/cglib/util/SorterTemplate.class
   */
  public List<String> list(URL url, String path) throws IOException {
    InputStream is = null;
    try {
      List<String> resources = new ArrayList<>();

      // First, try to find the URL of a JAR file containing the requested resource. If a JAR
      // file is found, then we'll list child resources by reading the JAR.

      // 查找url表示的文件所在的jar包，返回该jar包对应的URL对象，若url不在jar包中，则返回null
      URL jarUrl = findJarForResource(url);
      // jarUrl不为空，表示找到url所在的jar包
      if (jarUrl != null) {
        is = jarUrl.openStream();
        if (log.isDebugEnabled()) {
          log.debug("Listing " + url);
        }
        // 过滤出jar包中，路径以path开头的那些文件
        resources = listResources(new JarInputStream(is), path);
      }
      // jarUrl为空，表示没找到url所在的jar包
      else {
        List<String> children = new ArrayList<>();
        try {
          // 若url自己就代表一个jar包
          if (isJar(url)) {
            // Some versions of JBoss VFS might give a JAR stream even if the resource
            // referenced by the URL isn't actually a JAR
            is = url.openStream();
            try (JarInputStream jarInput = new JarInputStream(is)) {
              if (log.isDebugEnabled()) {
                log.debug("Listing " + url);
              }
              for (JarEntry entry; (entry = jarInput.getNextJarEntry()) != null; ) {
                if (log.isDebugEnabled()) {
                  log.debug("Jar entry: " + entry.getName());
                }
                // 把jar包中的文件全名都添加到children中
                children.add(entry.getName());
              }
            }
          }
          // 若url不是jar包，则是个普通文件
          else {
            /*
             * Some servlet containers allow reading from directory resources like a
             * text file, listing the child resources one per line. However, there is no
             * way to differentiate between directory and file resources just by reading
             * them. To work around that, as each line is read, try to look it up via
             * the class loader as a child of the current resource. If any line fails
             * then we assume the current resource is not a directory.
             */
            is = url.openStream();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
              for (String line; (line = reader.readLine()) != null;) {
                if (log.isDebugEnabled()) {
                  log.debug("Reader entry: " + line);
                }
                lines.add(line);
                // 若找不到 path + "/" + line 对应的文件，则清空lines
                if (getResources(path + "/" + line).isEmpty()) {
                  lines.clear();
                  break;
                }
              }
            }
            if (!lines.isEmpty()) {
              if (log.isDebugEnabled()) {
                log.debug("Listing " + url);
              }
              children.addAll(lines);
            }
          }
        } catch (FileNotFoundException e) {
          /*
           * For file URLs the openStream() call might fail, depending on the servlet
           * container, because directories can't be opened for reading. If that happens,
           * then list the directory directly instead.
           */
          // 若url代表的是一个目录，那这里就直接列出目录中的文件。
          if ("file".equals(url.getProtocol())) {
            File file = new File(url.getFile());
            if (log.isDebugEnabled()) {
                log.debug("Listing directory " + file.getAbsolutePath());
            }
            if (file.isDirectory()) {
              if (log.isDebugEnabled()) {
                  log.debug("Listing " + url);
              }
              children = Arrays.asList(file.list());
            }
          }
          else {
            // No idea where the exception came from so rethrow it
            throw e;
          }
        }

        // The URL prefix to use when recursively listing child resources
        String prefix = url.toExternalForm();
        if (!prefix.endsWith("/")) {
          prefix = prefix + "/";
        }

        // Iterate over immediate children, adding files and recursing into directories
        for (String child : children) {
          String resourcePath = path + "/" + child;
          resources.add(resourcePath);
          URL childUrl = new URL(prefix + child);
          resources.addAll(list(childUrl, resourcePath));
        }
      }

      return resources;
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {
          // Ignore
        }
      }
    }
  }

  /**
   * List the names of the entries in the given {@link JarInputStream} that begin with the
   * specified {@code path}. Entries will match with or without a leading slash.
   *
   * @param jar The JAR input stream
   * @param path The leading path to match
   * @return The names of all the matching entries
   * @throws IOException If I/O errors occur
   */
  //

  /**
   * 遍历jar包中所有文件，筛选文件的路径，将文件路径以path开头的那些文件收集到list中。
   *
   * 举例：
   * 若 path = "net/sf/cglib/beans"
   * 则返回结果：
   * net/sf/cglib/beans/BeanCopier.class
   * net/sf/cglib/beans/BeanGenerator$BeanGeneratorKey.class
   * net/sf/cglib/beans/BeanGenerator.class
   * net/sf/cglib/beans/BeanMap$Generator$BeanMapKey.class
   * net/sf/cglib/beans/BeanMap$Generator.class
   * net/sf/cglib/beans/BeanMap.class
   * net/sf/cglib/beans/BeanMapEmitter$1.class
   * net/sf/cglib/beans/BeanMapEmitter$2.class
   */
  protected List<String> listResources(JarInputStream jar, String path) throws IOException {
    // Include the leading and trailing slash when matching names
    // 给入参path前面，后面加上"/"
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    if (!path.endsWith("/")) {
      path = path + "/";
    }

    // Iterate over the entries and collect those that begin with the requested path
    // 遍历jar包中所有文件，筛选文件的路径，将以path开头的那些文件收集到list中。
    List<String> resources = new ArrayList<>();
    for (JarEntry entry; (entry = jar.getNextJarEntry()) != null;) {
      // 若entry是目录，则下一轮循环
      if (!entry.isDirectory()) {
        // Add leading slash if it's missing
        StringBuilder name = new StringBuilder(entry.getName());
        if (name.charAt(0) != '/') {
          name.insert(0, '/');
        }

        // Check file name
        // 若文件名以path开头，则添加到结果list中
        if (name.indexOf(path) == 0) {
          if (log.isDebugEnabled()) {
            log.debug("Found resource: " + name);
          }
          // Trim leading slash
          resources.add(name.substring(1));
        }
      }
    }
    return resources;
  }

  /**
   * Attempts to deconstruct the given URL to find a JAR file containing the resource referenced
   * by the URL. That is, assuming the URL references a JAR entry, this method will return a URL
   * that references the JAR file containing the entry. If the JAR cannot be located, then this
   * method returns null.
   *
   * 分析入参url，找到入参url代表的文件所在的jar包，
   * 也就是说， 我们假设入参url代表某个jar包中的一个文件，本方法会返回一个URL对象，表示这个jar包。
   * 若不能找到这个jar包，则方法返回null
   *
   * @param url The URL of the JAR entry. 表示jar中一个文件的URL
   * @return The URL of the JAR file, if one is found. Null if not. 返回表示jar包的URL，没有返回null
   * @throws MalformedURLException
   */

  //若url所代表的文件在一个jar包中，则获取该Jar包对应的URL对象，并返回，否则返回null
  protected URL findJarForResource(URL url) throws MalformedURLException {
    if (log.isDebugEnabled()) {
      log.debug("Find JAR URL: " + url);
    }

    // If the file part of the URL is itself a URL, then that URL probably points to the JAR
    /**
     * 若调用url.getFile()之后，得到的字符串还可以构造一个URL，那么该url就有可能表示一个jar包
     *
     * 举例：
     * URL url = new URL("jar:file:/D:/repository/cglib/cglib/3.1/cglib-3.1.jar!/net/sf/cglib/util/StringSwitcher.class");
     * System.out.println(url.getFile());
     * 输出： file:/D:/repository/cglib/cglib/3.1/cglib-3.1.jar!/net/sf/cglib/util/StringSwitcher.class
     * URL url = new URL("file:/D:/repository/cglib/cglib/3.1/cglib-3.1.jar!/net/sf/cglib/util/StringSwitcher.class");
     * System.out.println(url.getFile());
     * 输出：/D:/repository/cglib/cglib/3.1/cglib-3.1.jar!/net/sf/cglib/util/StringSwitcher.class
     * URL url = new URL("/D:/repository/cglib/cglib/3.1/cglib-3.1.jar!/net/sf/cglib/util/StringSwitcher.class");
     * 抛出异常："java.net.MalformedURLException: no protocol: /D:/repository/cglib/cglib/3.1/cglib-3.1.jar!/net/sf/cglib/util/StringSwitcher.class"
     *
     * 以上url第一次调用url.getFile()之后，得到的字符串还可以构造一个URL对象，
     * 再调用一次url.getFile() 得到的字符串，不能构造URL对象，而是抛出异常。
     * 最开始的url中确实包含jar包的路径，调用getFile()就脱去一层协议
     */
    boolean continueLoop = true;
    while (continueLoop) {
      try {
        // 当url不能构造URL对象时，这里就会抛出异常，循环结束
        url = new URL(url.getFile());
        if (log.isDebugEnabled()) {
          log.debug("Inner URL: " + url);
        }
      } catch (MalformedURLException e) {
        // 当url的所有外层协议都被脱掉后，才会到这里
        continueLoop = false;
      }
    }

    // Look for the .jar extension and chop off everything after that

    // 串 "file:/D:/repository/cglib/cglib/3.1/cglib-3.1.jar!/net/sf/cglib/util/StringSwitcher.class"
    StringBuilder jarUrl = new StringBuilder(url.toExternalForm()); // url.toExternalForm()就是在文件路径外面加上protocol
    int index = jarUrl.lastIndexOf(".jar");
    // 若jarUrl中有".jar"
    if (index >= 0) {
      // 将".jar"后面的字符都截断。 得"file:/D:/repository/cglib/cglib/3.1/cglib-3.1.jar"
      jarUrl.setLength(index + 4);
      if (log.isDebugEnabled()) {
        log.debug("Extracted JAR URL: " + jarUrl);
      }
    }
    // 若jarUrl中没有".jar"，直接返回null
    else {
      if (log.isDebugEnabled()) {
        log.debug("Not a JAR: " + jarUrl);
      }
      return null;
    }

    // Try to open and test it
    try {
      URL testUrl = new URL(jarUrl.toString());
      // 若testUrl表示的是一个jar包，直接返回testUrl
      if (isJar(testUrl)) {
        return testUrl;
      }
      // jarUrl串最后是".jar" ，但jarUrl串所构造的testUrl对象 不代表一个jar包
      else {
        // WebLogic fix: check if the URL's file exists in the filesystem.
        if (log.isDebugEnabled()) {
          log.debug("Not a JAR: " + jarUrl);
        }

        // 将testUrl外层协议脱掉，之后赋值给jarUrl
        jarUrl.replace(0, jarUrl.length(), testUrl.getFile());
        // 生成File对象，例如： new File("/D:/repository/cglib/cglib/3.1/cglib-3.1.jar")
        File file = new File(jarUrl.toString());

        // 若file不存在，则把jarUrl路径串再encode一下
        if (!file.exists()) {
          try {
            file = new File(URLEncoder.encode(jarUrl.toString(), "UTF-8"));
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported encoding?  UTF-8?  That's unpossible.");
          }
        }

        // 再试下文件是否存在，若不存在，则最后返回null

        // 若file存在
        if (file.exists()) {
          if (log.isDebugEnabled()) {
            log.debug("Trying real file: " + file.getAbsolutePath());
          }
          // 将File对象转成URL对象（使用这种方式，可以将一个文件路径串转成URL对象，挺好）
          testUrl = file.toURI().toURL();
          // 若testUrl代表一个jar包，返回testUrl，否则返回null
          if (isJar(testUrl)) {
            return testUrl;
          }
        }
      }
    } catch (MalformedURLException e) {
      log.warn("Invalid JAR URL: " + jarUrl);
    }

    if (log.isDebugEnabled()) {
      log.debug("Not a JAR: " + jarUrl);
    }
    return null;
  }

  /**
   * Converts a Java package name to a path that can be looked up with a call to
   * {@link ClassLoader#getResources(String)}.
   *
   * @param packageName The Java package name to convert to a path
   */
  protected String getPackagePath(String packageName) {
    return packageName == null ? null : packageName.replace('.', '/');
  }

  /**
   * Returns true if the resource located at the given URL is a JAR file.
   *
   * @param url The URL of the resource to test.
   */
  // 若url代表jar包，则返回true
  protected boolean isJar(URL url) {
    return isJar(url, new byte[JAR_MAGIC.length]);
  }

  /**
   * Returns true if the resource located at the given URL is a JAR file.
   *
   * @param url The URL of the resource to test.
   * @param buffer A buffer into which the first few bytes of the resource are read. The buffer
   *            must be at least the size of {@link #JAR_MAGIC}. (The same buffer may be reused
   *            for multiple calls as an optimization.)
   */
  // 若url代表jar包，则返回true，否则返回false （将url当做普通文件读取）
  protected boolean isJar(URL url, byte[] buffer) {
    InputStream is = null;
    try {
      is = url.openStream();
      // 将jar包当做文件读取时， 它的前4个字节是{ 'P', 'K', 3, 4 }
      is.read(buffer, 0, JAR_MAGIC.length);
      if (Arrays.equals(buffer, JAR_MAGIC)) {
        if (log.isDebugEnabled()) {
          log.debug("Found JAR: " + url);
        }
        return true;
      }
    } catch (Exception e) {
      // Failure to read the stream means this is not a JAR
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {
          // Ignore
        }
      }
    }

    return false;
  }
}
