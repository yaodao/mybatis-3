/**
 *    Copyright 2009-2016 the original author or authors.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * References a generic type.
 *
 * @param <T> the referenced type
 * @since 3.1.0
 * @author Simone Tripodi
 */
public abstract class TypeReference<T> {

  // 泛型的具体类型clazz
  private final Type rawType;

  protected TypeReference() {
    // 这里的getClass() 取到的是子类的clazz对象（试过）
    // 构造函数内调用getSuperclassTypeParameter方法，给rawType赋值
    rawType = getSuperclassTypeParameter(getClass());
  }

  /**
   * 取参数clazz的父类的泛型的具体类型。
   * （若clazz的父类没有指定泛型的类型，则抛出异常）
   *
   * 返回的是父类的泛型的具体类型的clazz对象。
   *
   * 因为java泛型会被擦除，感觉这个函数就是为了防止类型被擦除，而提前记录下来。
   */
  Type getSuperclassTypeParameter(Class<?> clazz) {
    // 取clazz的父类，父类可能是Class类型，可能是ParameterizedType类型
    // （若父类的泛型已明确，则返回ParameterizedType类型，若父类泛型未确定，则返回Class类型）
    Type genericSuperclass = clazz.getGenericSuperclass();

    // 若父类是Class类型，则进if，继续向上找父类
    // 其实就是要找到一个父类，该父类的泛型已经确定类型。
    if (genericSuperclass instanceof Class) {
      // try to climb up the hierarchy until meet something useful
      if (TypeReference.class != genericSuperclass) {
        return getSuperclassTypeParameter(clazz.getSuperclass());
      }

      // 若从子类到TypeReference 没有一个类指定泛型的具体类型，则抛出异常。
      throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
        + "Remove the extension or add a type parameter to it.");
    }

    // 到这里，说明找到了一个泛型已经是确定的类型的父类，（即 genericSuperclass是ParameterizedType类型）

    // 得到泛型的具体类型（例如： ArrayList 得到 ArrayList.class）
    Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
    // TODO remove this when Reflector is fixed to return Types
    if (rawType instanceof ParameterizedType) {
      // 具体类型里面还有泛型，则取raw类 （例如： ArrayList<String> 得到 ArrayList.class）
      rawType = ((ParameterizedType) rawType).getRawType();
    }

    return rawType;
  }

  public final Type getRawType() {
    return rawType;
  }

  @Override
  public String toString() {
    return rawType.toString();
  }

}
