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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
// 每个Reflector对象都对应一个类，
// 在Reflector中缓存了 属性名，属性名对应的set，get方法，set的参数类型，get的返回值类型 这些信息 （反射操作需要使用的类的元信息）
public class Reflector {

  // 当前对象对应的类的clazz对象
  private final Class<?> type;
  // 可读属性的名称集合，可读属性就是存在相应getter 方法的属性，初始值为空数纽
  private final String[] readablePropertyNames;
  // 可写属性的名称集合，可写属性就是存在相应setter 方法的属性，初始值为空数纽
  private final String[] writablePropertyNames;
  // key是属性名，value是对该属性的set方法的一个包装对象
  private final Map<String, Invoker> setMethods = new HashMap<>();
  // key是属性名，value是对该属性的get方法的一个包装对象
  private final Map<String, Invoker> getMethods = new HashMap<>();
  // key是属性名， value是setter方法的参数类型
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  //  key是属性名， value是getter方法的返回值类型
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  // 类的任意的一个无参构造函数
  private Constructor<?> defaultConstructor;

  // 所有属性名称的集合 （key是大写的属性名，value是属性名）
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  // 解析clazz，给当前类的成员变量赋值。
  public Reflector(Class<?> clazz) {
    type = clazz;
    // 给defaultConstructor赋值
    addDefaultConstructor(clazz);
    // 给getMethods和getTypes赋值
    addGetMethods(clazz);
    // 给setMethods 和setTypes赋值
    addSetMethods(clazz);
    addFields(clazz);
    // 可读的属性名
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // 可写的属性名
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    // 将所有属性名汇集到map，（key是大写的属性名，value是属性名）
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  // 从clazz中找一个无参的构造函数对象，并赋给当前对象的成员变量defaultConstructor
  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  // 取出clazz的所有get方法，处理成一个属性名对应一个方法，再添加到成员变量getMethods 和getTypes中
  private void addGetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 获取clazz及其父类/父接口中的所有方法对象，
    Method[] methods = getClassMethods(clazz);
    // 得到所有无参的get方法，将（从get方法名中提取的属性名，method对象）添加到conflictingMethods中
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 将conflictingGetters处理成一个属性名对应一个方法，再添加到成员变量getMethods 和getTypes中
    resolveGetterConflicts(conflictingGetters);
  }


  /**
   * 处理入参conflictingGetters中的entry，对entry中的每个key，只留一个方法
   * 从该方法中取信息，为成员变量getMethods，getTypes添加新元素
   * （就是对每个属性名，过滤出一个方法，并将该 属性名 和 方法的信息添加到成员变量getMethods，getTypes中）
   *
   * 具体：
   * 这里又一次再处理重复方法，其实仍然是子类重写父类方法引起的问题。 我们在addUniqueMethods中已经过滤了一遍重载方法，为什么这里又有呢？
   * 原因是重载不仅仅是可能一模一样，也有可能返回值不同。比如父类返回List，子类返回ArrayList。 而在我们解析Signature里面返回值是算在了里面的。所以就可能出现2个方法，除了返回值不一样，其他都一样
   * 此时我们取返回值范围为较小的那个。即 ArrayList
   *
   * @param conflictingGetters key是从方法中提取的属性名，value是key对应的方法对象的集合
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      boolean isAmbiguous = false;
      // 遍历value中的元素，得到一个返回值类型最小的method赋给winner， value是对同一个属性进行操作的方法的集合。
      // （返回值类型最小，就是返回值的类型是最底层的子类）
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          // 取第一个method元素
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        /**
         * 如果两个method对象返回的类型是相同的那么判断是否是布尔类型，因为这种情况只有布尔类型才会出现，
         * 其他是不会出现这种问题的，因为其他情况在遍历方法的时候就通过签名过滤掉了，
         * 但是布尔类型不会，因为布尔类型会存在说isUse和getUse这两种情况，
         * 这两种其实jdk都是支持的，mybatis只会保留isUse
         * 如果返回类型不相同，那么mybatis只保留返回类型是子类的
         */
        if (candidateType.equals(winnerType)) {
          // 若candidate的返回值不是boolean.class类型，则有歧义
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
          } // 若candidate的方法名以is开头，则替换winner
          else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        } // candidateType和 winnerType需要是父子关系，最后让winner中保存子类型
        else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        } // 若candidateType和 winnerType不是父子关系，则有歧义
        else {
          isAmbiguous = true;
          break;
        }
      } // for
      addGetMethod(propName, winner, isAmbiguous);
    } // for
  }

  /**
   * 从入参method中取信息，为成员变量getMethods，getTypes添加新的entry
   * 该函数处理单个method
   *
   * @param name 从方法中提取的属性名
   * @param method 方法对象
   * @param isAmbiguous 是否有歧义
   */
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // isAmbiguous=true 生成一个AmbiguousMethodInvoker对象，
    // isAmbiguous=false 生成一个MethodInvoker对象
    MethodInvoker invoker = isAmbiguous
        ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName()))
        : new MethodInvoker(method);
    // 添加到成员变量getMethods
    getMethods.put(name, invoker);
    // 得到method的返回值
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    // 将（name，clazz）添加到成员变量getTypes，clazz是由returnType转的。
    getTypes.put(name, typeToClass(returnType));
  }

  // 取出clazz的所有set方法，处理成一个属性名对应一个方法，再添加到成员变量setMethods 和setTypes中
  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 获取clazz及其父类/父接口中的所有方法对象，
    Method[] methods = getClassMethods(clazz);
    // 获取那些只有一个参数的set方法，将（从set方法名中提取的属性名，method对象）添加到conflictingMethods中
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 将conflictingSetters处理成一个属性名对应一个方法，再添加到成员变量setMethods 和setTypes中
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * 将（name，list（method））添加到conflictingMethods中 （这意思是可能有同一个属性名 对应多个方法的情况，所有本函数取名addMethodConflict）
   *
   *
   * 这里有一个疑问，为什么会存在方法名相同，但是方法会有多个的呢？因为在getClassMethods的时候，
   * 它会取类本身以及它的interface的方法。假如子类重写了父类中的方法， 如果返回值相同，
   * 则可以通过键重复来去掉。 但是， 如果方法返回值是父类相同方法的返回值类型的子类，
   * 则就会导致两个方法是同一个方法， 但是签名不同,所以就导致了，出现name一样，但是方法不一样的情况。
   *
   * @param conflictingMethods key是属性名，value是该属性名对应的方法对象的集合
   * @param name 从方法中取出的属性名
   * @param method 方法对象
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    if (isValidPropertyName(name)) {
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  /**
   * 处理入参conflictingSetters中的entry，对entry中的每个key，只留一个方法
   * 从该方法中取信息，为成员变量setMethods，setTypes添加新元素
   * （就是对每个属性名，过滤出一个方法，并将该 属性名 和 方法的信息添加到成员变量setMethods，setTypes中）
   *
   *
   * 解决一个属性名对应多个setter方法的问题，确保 一个属性名只对应一个方法，
   * 属性名相同的setter会有多个方法是因为参数的类型存在父类，子类的关系
   *
   * @param conflictingSetters key是从get方法中提取的属性名，value是key对应的get方法对象的集合
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      // 取propName对应setter方法集合
      List<Method> setters = conflictingSetters.get(propName);
      // 取propName对应getter方法的返回值
      Class<?> getterType = getTypes.get(propName);

      // getter方法是否有歧义（就是同一个属性 存在多个不同返回类型的getter）
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      Method match = null;

      // 遍历propName对应的setter方法集合，找到和getter方法最搭的那个setter方法。
      // 若没有最搭的方法，则找参数的类型最小的那个setter
      for (Method setter : setters) {
        // 若getter方法没有歧义，且setter方法的参数类型就是getter方法的返回值类型， 这是最匹配的setter方法
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }

        if (!isSetterAmbiguous) {
          // 找一个参数类型较小的setter返回，没有则返回null
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      } // for
      if (match != null) {
        // 为成员变量setMethods，setTypes添加新元素
        addSetMethod(propName, match);
      }
    } // for
  }

  // 若setter1和setter2的参数类型有父子关系，则返回参数类型是子类的那个setter
  // 若没有，则返回null
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    // 若setter1为空，则返回setter2
    if (setter1 == null) {
      return setter2;
    }
    // 取setter1和setter2的参数的类型
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    // 返回参数类型较底的setter （较低就是最下面的子类）
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }

    // 到这，说明setter1和setter2的参数类型不相关，这时返回null
    // 问题： 不相关还要添加元素到setMethods和setTypes，why？？

    // 生成一个AmbiguousMethodInvoker对象
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
            property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    // 给成员变量setMethods添加元素
    setMethods.put(property, invoker);
    // 获取setter1的参数的具体类型
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    // 将（属性名，setter方法的参数类型的clazz）添加到成员变量setTypes
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  /**
   * 取method的信息，添加到成员变量setMethods和setTypes
   *
   * @param name 从方法对象中取到的属性名
   * @param method 方法对象
   */
  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    // 将method的 泛型的参数类型解析为具体类型
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    // 将（name，clazz）添加到成员变量getTypes，clazz是由method的参数类型转的。
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  // 返回src对应的clazz对象。（src是数组时，返回数组对象的clazz）
  // https://www.jianshu.com/p/e8eeff12c306 这篇文章举的例子挺多，可以参考
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    }
    // 若src是带泛型的类型，例如 ArrayList<String>
    else if (src instanceof ParameterizedType) {
      // 则result=ArrayList.class
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    }
    // 若src是 元素带泛型 的数组
    else if (src instanceof GenericArrayType) {
      // 返回数组内元素的类型
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      // 若数组元素是普通类型
      if (componentType instanceof Class) {
        // 返回数组对象的clazz
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      }
      // 若数组元素带泛型
      else {
        // 递归取得数组元素的类型，返回数组对象的clazz
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  // 解析clazz的所有属性字段，并将解析结果添加到成员变量 setMethods/setTypes 或 getMethods/getTypes 中
  private void addFields(Class<?> clazz) {
    // 取clazz的所有字段
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      // 若setMethods中不包括field的名字，则将field的信息 填充到成员变量setMethods和setTypes
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        // field不是final且不是static，则填充成员变量setMethods和setTypes
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }

      // 若getMethods中不包括field的名字，则将field的信息 填充到成员变量getMethods和getTypes
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 若clazz还有父类，则递归继续添加父类的字段 到成员变量
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  // 从入参field取信息，填充成员变量setMethods和setTypes
  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      // 将（字段名， SetFieldInvoker对象）添加到成员变量setMethods
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      // 将（字段名， 字段的类型clazz）添加到成员变量setTypes
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  // 从入参field取信息，填充成员变量getMethods和getTypes
  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      // 将（字段名，GetFieldInvoker对象）添加到getMethods
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      // 将（字段名，字段的类型clazz）添加到getTypes
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  // 判断入参name是否为合理的属性名
  private boolean isValidPropertyName(String name) {
    // 以$开头 或者 等于"serialVersionUID"  或者  等于"class"， 都不合法，返回false
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  // 返回clazz所有的方法对象，包括clazz的父类/父接口中的方法， 相同的方法保留最下层子类的方法（其实就是保留clazz的方法，因为clazz就是最下层子类）。
  private Method[] getClassMethods(Class<?> clazz) {
    // key是方法签名，value是方法对象
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      // 取currentClass的所有方法，将（方法签名, 方法对象）添加到uniqueMethods
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 取currentClass的所有直接实现的接口，将接口中的方法添加到uniqueMethods
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        // 取anInterface的所有方法，将（方法签名, 方法对象）添加到uniqueMethods
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      // 取currentClass的父类clazz
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  // 遍历入参methods数组中的每个对象，将（signature, method）添加到uniqueMethods
  // 其中，signature是由单个method对象生成的签名
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 不是桥接方法，进if
      if (!currentMethod.isBridge()) {
        // 得到自定义的方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 将（signature，currentMethod）添加到uniqueMethods，（同样签名的方法，后面的不覆盖前面，也就是保留最下层子类中的方法）
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 返回入参method对应的字符串，
   *
   * 例如：  private String opNum(Integer p1, Integer p2){}
   * 返回串 "java.lang.String#opNum:java.lang.Integer,java.lang.Integer"
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  // 检验是否可以存取类的成员（包括成员变量，成员方法）
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  // 返回当前对象对应的类的clazz对象
  public Class<?> getType() {
    return type;
  }

  // 获取无参构造函数，没有抛出异常
  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  // 判断类是否有无参的构造函数
  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  // 从成员变量setMethods中取propertyName对应的Invoker对象，没有则抛出异常
  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  // 从成员变量getMethods中取propertyName对应的Invoker对象，没有则抛出异常
  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  // 获取propertyName对应的setter方法的参数类型
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  // 获取propertyName对应的getter方法的返回值类型
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  // 获取可读属性的名称集合，可读属性就是存在相应getter 方法的属性
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  // 可写属性的名称集合，可写属性就是存在相应setter 方法的属性
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  // 检查下 类中指定的属性名，是否有setter方法，有则返回true
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  // 检查下 类中指定的属性名，是否有getter方法，有则返回true
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  // 返回name对应的属性名
  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
