/**
 *    Copyright 2009-2018 the original author or authors.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */

/**
 *  通过对 Reflector 和 PropertyTokenizer 组合使用， 实现了对复杂的属性表达式的解析，并实现了获取指定属性描述信息的功能。
 *  暂时认为一个MetaClass只对应处理一个类
 */
public class MetaClass {

  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector; // 一个类对应一个Reflector对象

  // 生成一个MetaClass对象，
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    // 取得type对应的Reflector对象。（若缓存中没有该type对应的Reflector对象，则会新建一个）
    this.reflector = reflectorFactory.findForClass(type);
  }

  // 构造一个MetaClass对象，并返回，（会为type生成对应的reflector对象）
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  // 为属性名name构造一个MetaClass （生成的MetaClass对象中，reflector是该属性的类型对应的Reflector对象）
  // （一个属性对应一个MetaClass对象， 不同属性对应不同的MetaClass对象。这些metaClass对象之间 reflectorFactory相同，reflector对象不同）
  public MetaClass metaClassForProperty(String name) {
    // 取name的getter方法的返回值的类型
    Class<?> propType = reflector.getGetterType(name);
    // 对上句得到的propType创建MetaClass对象。
    return MetaClass.forClass(propType, reflectorFactory);
  }

  // 获取name对应的属性名
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  // 获取name对应的属性名
  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      // 将name中的"_"去掉
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  // 获取可读属性的名称集合（获取的是 reflector对应的那个类的可读属性集合）
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  // 获取可写属性的名称集合（获取的是 reflector对应的那个类的可写属性集合）
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  // 获取name对应的setter函数的参数类型（若name是由"."连接的多个名字组成，则取最后那个名字对应的setter函数的参数类型）
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 为属性名构造一个MetaClass对象（MetaClass对象中的reflector就是 属性的类型所对应的Reflector对象）
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * 返回名字为name的属性的getter方法的返回值的类型，
   * 若name由多个部分组成（用"."分隔），则返回的是最后一部分对应的getter方法的返回值的类型。
   */
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 当name由多个部分组成时，才进if
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  // 为入参prop构造一个MetaClass对象
  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    // 先获取参数prop的getter方法的返回值的类型
    Class<?> propType = getGetterType(prop);
    // 再为propType构造一个MetaClass对象，并返回
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 获取参数prop.name的getter方法的返回值的类型（可以处理prop.name的类型是集合类型的情况）
   *
   * @param prop 包含属性名的对象， prop.name就是属性名
   * @return
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 取prop的getter的返回值类型
    Class<?> type = reflector.getGetterType(prop.getName());
    // getter的返回值是集合类型
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 取prop的getter的返回值类型 （和上面不同的是，这里取到的返回值类型可以带泛型）
      Type returnType = getGenericGetterType(prop.getName());

      // 若返回值的类型带泛型，则给type赋值
      // 例如：returnType=List<String> 是ParameterizedType类型， 则进入if，得到type=String.class。试过，
      // 为什么type不是List.class 而是String.class 所以感觉这个if里有bug
      if (returnType instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  /**
   * 解析名字为propertyName的字段的类型，若找不到就返回null
   * （返回Type类型，说明有时候返回的类型带泛型，例如 List<String> ）
   */
  private Type getGenericGetterType(String propertyName) {
    try {
      // 获取propertyName对应的Invoker对象，（这个Invoker对象内有 propertyName的getter方法，或者有propertyName的字段本身）
      Invoker invoker = reflector.getGetInvoker(propertyName);

      // 下面代码作用是，解析出名字为propertyName的字段的类型。

      // Invoker对象内有 propertyName的getter方法
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        // 解析getter的返回值的类型
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } // Invoker对象内有 propertyName的字段本身
      else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        // 解析field的类型
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }

    // 解析不出类型，返回null
    return null;
  }

  // 判断是否有name对应的setter方法。（实际判断的是reflector对应的类中是否有name的setter方法， 每个reflector都有自己的对应类）
  // 若name由多个部分组成（用"."分隔），则返回的是最后一部分是否有对应的setter方法
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  // 判断是否有name对应的getter方法。（实际判断的是reflector对应的类中是否有name的getter方法， 每个reflector都有自己的对应类）
  // 若name由多个部分组成（用"."分隔），则返回的是最后一部分是否有对应的getter方法
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  // 取name对应的Invoker对象
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  // 取name对应的Invoker对象
  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  // 解析name，返回它对应的属性名。
  // （若name是以"."连接的字符串，则分开解析每一部分，并将各个部分对应的属性名用"."连接在一起返回）
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
