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
package org.apache.ibatis.type;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.JapaneseDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */

/**
 * 类型处理器注册器。
 * 主要完成类型处理器的注册功能，同时也能对类型处理器进行管理，其内部定义了集合来进行类型处理器的存取，同时定义了存取方法。
 * 默认完成了大量常见类型处理器的注册。
 *
 *
 *
 *
 * 下面代码有三个概念，先说下
 * Class<?> javaType;  这是指jdk自带的类型，例如：String.class
 * JdbcType jdbcType;  这是JdbcType类型的枚举值，一般从typeHandler的注解中取到，
 * TypeHandler typeHandler; typeHandler是处理器对象，是TypeHandler接口的实现类的对象，用于处理javaType类型的变量。
 */
public final class TypeHandlerRegistry {

  // key是JdbcType类型的枚举值，value是该枚举值所在的TypeHandler对象
  private final Map<JdbcType, TypeHandler<?>>  jdbcTypeHandlerMap = new EnumMap<>(JdbcType.class);
  // key是jdk自带的类型， value是jdk类型对应的jdbc信息，其中key是JdbcType类型的枚举值，value是Type对应的处理对象。
  private final Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap = new ConcurrentHashMap<>();
  private final TypeHandler<Object> unknownTypeHandler = new UnknownTypeHandler(this);
  // key是handler的clazz，value是handler的对象（其中 handler是TypeHandler接口的实现类的对象）
  private final Map<Class<?>, TypeHandler<?>> allTypeHandlersMap = new HashMap<>();

  // 空的map
  private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();

  private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;

  public TypeHandlerRegistry() {

    /**
     * 以下对这些register方法的调用，目的都是填充成员变量typeHandlerMap，allTypeHandlersMap
     * 具体是：
     *  将（javaType，（jdbcType，typeHandler）） 添加到成员变量typeHandlerMap，其中 jdbcType从typeHandler的注解中取到。
     *  将（typeHandler.class，typeHandler） 添加到成员变量allTypeHandlersMap
     */

    register(Boolean.class, new BooleanTypeHandler());
    register(boolean.class, new BooleanTypeHandler());
    register(JdbcType.BOOLEAN, new BooleanTypeHandler());
    register(JdbcType.BIT, new BooleanTypeHandler());

    register(Byte.class, new ByteTypeHandler());
    register(byte.class, new ByteTypeHandler());
    register(JdbcType.TINYINT, new ByteTypeHandler());

    register(Short.class, new ShortTypeHandler());
    register(short.class, new ShortTypeHandler());
    register(JdbcType.SMALLINT, new ShortTypeHandler());

    register(Integer.class, new IntegerTypeHandler());
    register(int.class, new IntegerTypeHandler());
    register(JdbcType.INTEGER, new IntegerTypeHandler());

    register(Long.class, new LongTypeHandler());
    register(long.class, new LongTypeHandler());

    register(Float.class, new FloatTypeHandler());
    register(float.class, new FloatTypeHandler());
    register(JdbcType.FLOAT, new FloatTypeHandler());

    register(Double.class, new DoubleTypeHandler());
    register(double.class, new DoubleTypeHandler());
    register(JdbcType.DOUBLE, new DoubleTypeHandler());

    register(Reader.class, new ClobReaderTypeHandler());
    register(String.class, new StringTypeHandler());
    register(String.class, JdbcType.CHAR, new StringTypeHandler());
    register(String.class, JdbcType.CLOB, new ClobTypeHandler());
    register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
    register(JdbcType.CHAR, new StringTypeHandler());
    register(JdbcType.VARCHAR, new StringTypeHandler());
    register(JdbcType.CLOB, new ClobTypeHandler());
    register(JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(JdbcType.NVARCHAR, new NStringTypeHandler());
    register(JdbcType.NCHAR, new NStringTypeHandler());
    register(JdbcType.NCLOB, new NClobTypeHandler());

    register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
    register(JdbcType.ARRAY, new ArrayTypeHandler());

    register(BigInteger.class, new BigIntegerTypeHandler());
    register(JdbcType.BIGINT, new LongTypeHandler());

    register(BigDecimal.class, new BigDecimalTypeHandler());
    register(JdbcType.REAL, new BigDecimalTypeHandler());
    register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
    register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

    register(InputStream.class, new BlobInputStreamTypeHandler());
    register(Byte[].class, new ByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
    register(byte[].class, new ByteArrayTypeHandler());
    register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
    register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.BLOB, new BlobTypeHandler());

    register(Object.class, unknownTypeHandler);
    register(Object.class, JdbcType.OTHER, unknownTypeHandler);
    register(JdbcType.OTHER, unknownTypeHandler);

    register(Date.class, new DateTypeHandler());
    register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
    register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
    register(JdbcType.TIMESTAMP, new DateTypeHandler());
    register(JdbcType.DATE, new DateOnlyTypeHandler());
    register(JdbcType.TIME, new TimeOnlyTypeHandler());

    register(java.sql.Date.class, new SqlDateTypeHandler());
    register(java.sql.Time.class, new SqlTimeTypeHandler());
    register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

    register(String.class, JdbcType.SQLXML, new SqlxmlTypeHandler());

    register(Instant.class, new InstantTypeHandler());
    register(LocalDateTime.class, new LocalDateTimeTypeHandler());
    register(LocalDate.class, new LocalDateTypeHandler());
    register(LocalTime.class, new LocalTimeTypeHandler());
    register(OffsetDateTime.class, new OffsetDateTimeTypeHandler());
    register(OffsetTime.class, new OffsetTimeTypeHandler());
    register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
    register(Month.class, new MonthTypeHandler());
    register(Year.class, new YearTypeHandler());
    register(YearMonth.class, new YearMonthTypeHandler());
    register(JapaneseDate.class, new JapaneseDateTypeHandler());

    // issue #273
    register(Character.class, new CharacterTypeHandler());
    register(char.class, new CharacterTypeHandler());
  }

  /**
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    this.defaultEnumTypeHandler = typeHandler;
  }

  // 判断入参javaType是否有对应的处理器对象，有则返回true
  public boolean hasTypeHandler(Class<?> javaType) {
    return hasTypeHandler(javaType, null);
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
    return hasTypeHandler(javaTypeReference, null);
  }

  // 判断入参javaType是否有对应的处理器对象，有则返回true
  public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
    return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
  }

  // 判断入参javaTypeReference是否有对应的处理器对象，有则返回true
  public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
    return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
  }

  // 取入参handlerType.class对应的handlerType对象
  public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
    return allTypeHandlersMap.get(handlerType);
  }

  // 取入参type对应的处理器，并返回，
  public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
    return getTypeHandler((Type) type, null);
  }

  // 取入参type对应的处理器，并返回，
  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
    return getTypeHandler(javaTypeReference, null);
  }

  // 取入参jdbcType对应的处理器，并返回，
  public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
    return jdbcTypeHandlerMap.get(jdbcType);
  }

  // 取入参type对应的处理器，并返回，
  public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
    return getTypeHandler((Type) type, jdbcType);
  }

  // 取入参javaTypeReference对应的处理器，并返回，
  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
    return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
  }

  @SuppressWarnings("unchecked")
  // 取入参type对应的处理器，并返回，
  // 入参jdbcType是辅助作用，让返回更精准，非必须。
  private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
    // type若是ParamMap类型，返回null
    if (ParamMap.class.equals(type)) {
      return null;
    }

    // 获取type对应的map （即 jdbcType与处理器的键值对）
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
    TypeHandler<?> handler = null;

    // 从jdbcHandlerMap中取出处理器
    // 先取jdbcType对应的处理器，若没有，再取null对应的处理器，若没有，再取jdbcHandlerMap中唯一的处理器
    if (jdbcHandlerMap != null) {
      handler = jdbcHandlerMap.get(jdbcType);
      if (handler == null) {
        handler = jdbcHandlerMap.get(null);
      }
      if (handler == null) {
        // #591
        handler = pickSoleHandler(jdbcHandlerMap);
      }
    }
    // type drives generics here
    return (TypeHandler<T>) handler;
  }

  // 根据javaType查询jdbcType与处理器对应关系的键值对
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
    // 取type对应的map（key是JdbcType类型的枚举值，value是TypeHandler的实现类对象）
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(type);
    if (NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap)) {
      return null;
    }

    // 若上面用type取得的map是空，则尝试其他方式获取map
    if (jdbcHandlerMap == null && type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      // 当type是枚举类型时，进if（估计一般用不上）
      if (Enum.class.isAssignableFrom(clazz)) {
        Class<?> enumClass = clazz.isAnonymousClass() ? clazz.getSuperclass() : clazz;
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(enumClass, enumClass);
        if (jdbcHandlerMap == null) {
          register(enumClass, getInstance(enumClass, defaultEnumTypeHandler));
          return typeHandlerMap.get(enumClass);
        }
      } else {
        // 从成员变量typeHandlerMap中取clazz的父类对应的map，没有返回null
        jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
      }
    }

    // 把上面得到的jdbcHandlerMap更新到typeHandlerMap中
    // 即，将（type， NULL_TYPE_HANDLER_MAP 或者 jdbcHandlerMap）添加到成员变量typeHandlerMap
    typeHandlerMap.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
    return jdbcHandlerMap;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
    for (Class<?> iface : clazz.getInterfaces()) {
      Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(iface);
      if (jdbcHandlerMap == null) {
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
      }
      if (jdbcHandlerMap != null) {
        // Found a type handler regsiterd to a super interface
        HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<>();
        for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
          // Create a type handler instance with enum type as a constructor arg
          newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
        }
        return newMap;
      }
    }
    return null;
  }

  // 从成员变量typeHandlerMap中取clazz的父类对应的map，没有返回null
  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
    // 若superclass是Object、接口、基本类型、void 。那么返回的是null，
    // 若superclass是数组，则返回 Object.class
    Class<?> superclass =  clazz.getSuperclass();
    if (superclass == null || Object.class.equals(superclass)) {
      return null;
    }

    // 从成员变量typeHandlerMap中取superclass对应的map，没有则用superclass的父类，继续取对应的map
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(superclass);
    if (jdbcHandlerMap != null) {
      return jdbcHandlerMap;
    } else {
      return getJdbcHandlerMapForSuperclass(superclass);
    }
  }

  /**
   * 判断某一个javaType对应的map中存储的，处理器是否唯一，如果唯一，则返回该处理器，如果不唯一，返回null
   * 也就是说，正常情况下，一个javaType 只对应一个处理器。
   * 注意： 这里说的javaType不是入参中的JdbcType
   *
   * @param jdbcHandlerMap 这个是某一个javaType对应的map
   *                       （从成员变量typeHandlerMap中取key=javaType对应的map）
   * @return 如果返回TypeHandler对象，则表示javaType只对应一个处理器； 返回null，则表示javaType对应多个不同的处理器
   */
  private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
    TypeHandler<?> soleHandler = null;
    // 判断map的values中的所有对象是否是同一个对象。
    for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
      if (soleHandler == null) {
        soleHandler = handler;
      } else if (!handler.getClass().equals(soleHandler.getClass())) {
        // More than one type handlers registered.
        return null;
      }
    }
    return soleHandler;
  }

  public TypeHandler<Object> getUnknownTypeHandler() {
    return unknownTypeHandler;
  }

  // 填充当前对象的成员变量jdbcTypeHandlerMap，
  // key是JdbcType类型的枚举值，value是该枚举值所在的TypeHandler对象
  public void register(JdbcType jdbcType, TypeHandler<?> handler) {
    jdbcTypeHandlerMap.put(jdbcType, handler);
  }

  //
  // REGISTER INSTANCE
  //

  // Only handler

  @SuppressWarnings("unchecked")
  // 当只有TypeHandler对象时，则从该对象获取信息
  // 填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
  public <T> void register(TypeHandler<T> typeHandler) {
    boolean mappedTypeFound = false;
    // 从typeHandler的@MappedTypes注解中，获取javaType
    MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      for (Class<?> handledType : mappedTypes.value()) {
        // 填充
        register(handledType, typeHandler);
        mappedTypeFound = true;
      }
    }
    // @since 3.1.0 - try to auto-discover the mapped type
    // 若typeHandler是TypeReference的子类，则尝试从泛型中获取javaType
    if (!mappedTypeFound && typeHandler instanceof TypeReference) {
      try {
        TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
        register(typeReference.getRawType(), typeHandler);
        mappedTypeFound = true;
      } catch (Throwable t) {
        // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
      }
    }
    // 若typeHandler没有@MappedTypes注解，
    // 则只将（typeHandler.class，typeHandler） 添加到成员变量allTypeHandlersMap
    if (!mappedTypeFound) {
      register((Class<T>) null, typeHandler);
    }
  }


  /**
   * 将（javaType，（jdbcType，typeHandler）） 添加到成员变量typeHandlerMap，其中 jdbcType从typeHandler的注解中取到。
   * 将（typeHandler.class，typeHandler） 添加到成员变量allTypeHandlersMap
   *
   * @param javaType jdk自带的类的clazz
   * @param typeHandler 用于处理javaType类型的变量的对象（是TypeHandler接口的实现类的对象）
   * @param <T>
   */
  public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
    register((Type) javaType, typeHandler);
  }


  /**
   * 填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
   *
   * 具体是：
   * 将（javaType，（jdbcType，typeHandler）） 添加到成员变量typeHandlerMap
   * 将（typeHandler.class，typeHandler） 添加到成员变量allTypeHandlersMap
   *
   * 其中，
   * javaType是jdk自带的类型，例如：String.class
   * jdbcType是从typeHandler的注解中取到，是JdbcType类型的枚举值
   * typeHandler是处理器，用于处理javaType类型的变量
   * 题外话，clazz类型可以赋值给Type类型的变量，Type的子类都可以赋值给Type类型的变量，clazz是一种
   */
  private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
    // 取@MappedJdbcTypes注解的值
    MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);

    // 填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
    if (mappedJdbcTypes != null) {
      // 从注解中取jdbcType
      for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
        register(javaType, handledJdbcType, typeHandler);
      }
      if (mappedJdbcTypes.includeNullJdbcType()) {
        // 将（javaType，（null，typeHandler）） 添加到成员变量typeHandlerMap
        register(javaType, null, typeHandler);
      }
    } else {
      // 将（javaType，（null，typeHandler）） 添加到成员变量typeHandlerMap
      register(javaType, null, typeHandler);
    }
  }

  // 填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
  public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
    register(javaTypeReference.getRawType(), handler);
  }

  // java type + jdbc type + handler

  // 将入参（type，（jdbcType，handler）） 添加到成员变量typeHandlerMap
  // 将入参（handler.class，handler） 添加到成员变量allTypeHandlersMap
  public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
    register((Type) type, jdbcType, handler);
  }

  // 将入参（javaType，（jdbcType，handler）） 添加到成员变量typeHandlerMap
  // 将入参（handler.class，handler） 添加到成员变量allTypeHandlersMap
  private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
    if (javaType != null) {
      Map<JdbcType, TypeHandler<?>> map = typeHandlerMap.get(javaType);
      if (map == null || map == NULL_TYPE_HANDLER_MAP) {
        map = new HashMap<>();
        typeHandlerMap.put(javaType, map);
      }
      map.put(jdbcType, handler);
    }
    // 将 （handler.class，handler） 添加到成员变量allTypeHandlersMap
    allTypeHandlersMap.put(handler.getClass(), handler);
  }

  //
  // REGISTER CLASS
  //

  // Only handler type

  // 当只有TypeHandler接口的实现类对象的clazz时，
  // 从该clazz获取信息，填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
  public void register(Class<?> typeHandlerClass) {
    boolean mappedTypeFound = false;
    // 从入参typeHandlerClass的@MappedTypes注解中获取javaType
    MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      for (Class<?> javaTypeClass : mappedTypes.value()) {
        // 填充
        register(javaTypeClass, typeHandlerClass);
        mappedTypeFound = true;
      }
    }
    // 若typeHandlerClass没有@MappedTypes注解，
    // 则使用反射生成typeHandlerClass的对象，使用该对象提供的信息填充typeHandlerMap、allTypeHandlersMap
    if (!mappedTypeFound) {
      register(getInstance(null, typeHandlerClass));
    }
  }

  // java type + handler type
  // 填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
  public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
    register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
  }

  // 填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
  public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
  }

  // java type + jdbc type + handler type

  // 填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
  public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
    register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
  }

  // Construct a handler (used also from Builders)

  @SuppressWarnings("unchecked")
  /**
   * 使用反射生成一个typeHandlerClass的对象
   *
   * @param javaTypeClass TypeHandler接口的某个实现类的构造函数的参数类型clazz
   * @param typeHandlerClass 一个clazz，是TypeHandler接口的某个实现类的clazz
   * @param <T>
   * @return
   */
  public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    if (javaTypeClass != null) {
      try {
        Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
        return (TypeHandler<T>) c.newInstance(javaTypeClass);
      } catch (NoSuchMethodException ignored) {
        // ignored
      } catch (Exception e) {
        throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
      }
    }
    try {
      Constructor<?> c = typeHandlerClass.getConstructor();
      return (TypeHandler<T>) c.newInstance();
    } catch (Exception e) {
      throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
    }
  }

  // scan

  // 扫描包内的文件，找到TypeHandler的子类的clazz，
  // 使用这些clazz获取信息，填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
  public void register(String packageName) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    // 扫描包内文件，找到所有TypeHandler的子类的clazz （其中 new ResolverUtil.IsA(TypeHandler.class) 这句就是创建IsA类的一个对象）
    resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
    Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
    for (Class<?> type : handlerSet) {
      //Ignore inner classes and interfaces (including package-info.java) and abstract classes
      // 若type不是匿名内部类  且 不是接口  且 不是抽象类， 则填充当前对象的成员变量typeHandlerMap、allTypeHandlersMap
      if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
        register(type);
      }
    }
  }

  // get information

  /**
   * @since 3.2.2
   */
  // 返回TypeHandler的所有子类对象的集合
  public Collection<TypeHandler<?>> getTypeHandlers() {
    return Collections.unmodifiableCollection(allTypeHandlersMap.values());
  }

}
