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
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveFieldType(Field field, Type srcType) {
    Type fieldType = field.getGenericType();
    Class<?> declaringClass = field.getDeclaringClass();
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */

  /**
   * 解析入参method的返回值，将返回值中的泛型解析为具体类型
   *
   * 举例： srcType是Level1Mapper.class， method是Level0Mapper.class中的一个方法
   * （其中，Level1Mapper.class是Level0Mapper.class的子接口）
   */
  public static Type resolveReturnType(Method method, Type srcType) {
    Type returnType = method.getGenericReturnType();
    Class<?> declaringClass = method.getDeclaringClass();
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    Type[] paramTypes = method.getGenericParameterTypes();
    Class<?> declaringClass = method.getDeclaringClass();
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  // 从[srcType, declaringClass]这个继承结构中，将typeVar解析为具体化的类型
  // 举例： type是一个方法的返回值，srcType=Level1Mapper.class，declaringClass是Level0Mapper.class
  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    if (type instanceof TypeVariable) {
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } // type是泛型，例如：List<Double>
    else if (type instanceof ParameterizedType) {
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
    } else if (type instanceof GenericArrayType) {
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } // type是普通clazz
    else {
      return type;
    }
  }

  // GenericArrayType表示数组，例如：ArrayList<String>[] listArr;
  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
    Type componentType = genericArrayType.getGenericComponentType();
    Type resolvedComponentType = null;
    if (componentType instanceof TypeVariable) {
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
    if (resolvedComponentType instanceof Class) {
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  // 解析入参parameterizedType 中的泛型。
  // 因为ParameterizedType类型的变量就是带泛型的变量，（可能带多个泛型）
  // 本函数会将这些泛型解析成具体的类型，再返回用这些解析后的值，重新构造的ParameterizedType类型对象
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    // 取parameterizedType内的泛型，例如：List<Double> 得到 Double.class
    // 由parameterizedType.getActualTypeArguments 得到的值，
    // 类型可以是TypeVariable，ParameterizedType，WildcardType，Class，可以参考笔记
    Type[] typeArgs = parameterizedType.getActualTypeArguments();

    // 解析出数组typeArgs中每一个元素的具体化类型，来填充args数组
    Type[] args = new Type[typeArgs.length];
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
        // 解析出typeArgs[i]的具体化类型
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
        // 等号右面得到的是ParameterizedTypeImpl类型的对象（也就是最终结果args数组中会有ParameterizedType类型的元素）
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        args[i] = typeArgs[i];
      }
    }
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  // 解析入参wildcardType中，上下界的泛型
  // 返回由解析得到的值，构造成的WildcardTypeImpl对象。
  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    // 解析入参wildcardType，获取下限。
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    // 解析入参wildcardType，获取上限。例如：? extends String， 得到String.class
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    // 包装成自定义的WildcardTypeImpl类型返回
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  // 从[srcType, declaringClass]这个继承结构中，
  // 解析出 入参bounds数组中 每个元素的具体化类型，返回解析后的数组
  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }

  /**
   * 解析入参typeVar的具体化类型（就是typeVar是个泛型，需要得到它对应的具体化的类型。 例如：typeVar代表T，若代码中给 T的具体类型是String，则本函数返回String）
   * 从[srcType, declaringClass]这个继承结构中，查找typeVar的具体化类型
   *
   * 具体步骤：
   * 1.如果srcType的Class类型和declaringClass为同一个类，如果typeVar有上限，则返回typeVar的第一个上界， 否则返回Object.class
   * 2.如果不是，则代表declaringClass是srcType的父类或者实现的接口，则解析继承结构中有没有定义其具体化的类型
   *其中的继承结构，包括从srcType到declaringClass的闭合区间
   *
   * typeVar是一个方法的返回值，srcType=Level1Mapper.class，declaringClass是Level0Mapper.class
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result;
    Class<?> clazz;
    // 将入参srcType转成Class对象，赋值给clazz。若转不了，则抛出异常。
    // （可以看出，srcType只能是Class或者ParameterizedType 类型的变量，不然就会抛出异常，即srcType只能是普通类或者带泛型的类）
    if (srcType instanceof Class) {
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }

    // 到这 clazz=srcType.class

    // 若clazz等于入参declaringClass，则表示在继承结构中没有获取其具体化的类型
    // 如果typeVar有上限，则返回typeVar的第一个上界， 否则返回Object.class（这意思是，下面在继承层次中的 所有递归调用，都会在这里终结）
    if (clazz == declaringClass) {
      Type[] bounds = typeVar.getBounds();
      if (bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

    Type superclass = clazz.getGenericSuperclass();
    // 通过对clazz父类的扫描，获取入参typeVar指代的实际类型
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass); // clazz是srcType.clazz，srcType=Level1Mapper.class，declaringClass是Level0Mapper.class，superclass是clazz的父类
    if (result != null) {
      return result;
    }

    Type[] superInterfaces = clazz.getGenericInterfaces();
    for (Type superInterface : superInterfaces) {
      // 通过对clazz父接口的扫描，获取入参typeVar指代的实际类型
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface); // clazz是srcType.clazz，srcType=Level1Mapper.class，declaringClass是Level0Mapper.class，superclass是clazz的父接口
      if (result != null) {
        return result;
      }
    }
    // 如果父类或者父接口中都没有获取到typeVar的具体化类型，则返回Object.class
    return Object.class;
  }

  /**
   *  通过对父类/接口的扫描获取 入参typeVar指代的实际类型
   *  typeVar是方法返回值，srcType=Level1Mapper.class，declaringClass是Level0Mapper.class，clazz是srcType.clazz，superclass是clazz的父接口，
   */
  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    // 若superclass带泛型，则进if
    if (superclass instanceof ParameterizedType) {
      ParameterizedType parentAsType = (ParameterizedType) superclass;
      // 获取当前对象的clazz
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
      // 获取泛型信息
      TypeVariable<?>[] parentTypeVars = parentAsClass.getTypeParameters();
      // 若子类带泛型
      if (srcType instanceof ParameterizedType) {
        // 使用子类的具体化泛型，具体化一下父类的泛型，并记录在返回的ParameterizedType对象中 （感觉这句调用，才是解析入参typeVar的重点）
        parentAsType = translateParentTypeVars((ParameterizedType) srcType, clazz, parentAsType);
      }
      // 如果declaringClass和parentAsClass表示同一类型，
      // 则先判断出typeVar在parentAsClass的泛型中的位置，再从supperClass中取typeVar对应的具体化的类型，并返回
      if (declaringClass == parentAsClass) {
        for (int i = 0; i < parentTypeVars.length; i++) {
          if (typeVar == parentTypeVars[i]) {
            // 返回该位置，父类的泛型的具体类型。
            //（子类继承父类的时候，可能会具体化父类的泛型，这里取的就是父类的泛型被具体化后的类型）
            return parentAsType.getActualTypeArguments()[i];
          }
        }
      }
      //通过判断parentAsClass是否是declaringClass的子类，来就决定是否进行递归解析（因为当前函数被调用处的java类可以实现多个接口）
      if (declaringClass.isAssignableFrom(parentAsClass)) {
        return resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
    } // superclass是Class类型，还需要判断superclass是否是declaringClass的子类，再决定是否进行递归解析。（因为当前函数被调用处的java类可以实现多个接口）
    else if (superclass instanceof Class && declaringClass.isAssignableFrom((Class<?>) superclass)) {
      return resolveTypeVar(typeVar, superclass, declaringClass);
    }
    return null;
  }


  /**
   * 使用子类的具体化的泛型信息，将父类提供的泛型给标识出具体类型。
   * 返回一个ParameterizedType对象，包含父类的这些具体化的泛型信息。
   *
   *
   * @param srcType 举例： Level1Mapper<E, F>
   * @param srcClass 是srcType.clazz
   * @param parentType 是srcClass的父类/接口
   * @return
   */
  private static ParameterizedType translateParentTypeVars(ParameterizedType srcType, Class<?> srcClass, ParameterizedType parentType) {
    Type[] parentTypeArgs = parentType.getActualTypeArguments();
    Type[] srcTypeArgs = srcType.getActualTypeArguments();
    TypeVariable<?>[] srcTypeVars = srcClass.getTypeParameters();
    Type[] newParentArgs = new Type[parentTypeArgs.length];
    boolean noChange = true;
    for (int i = 0; i < parentTypeArgs.length; i++) {
      // parentTypeArgs[i]是泛型，则尝试用子类的具体化泛型去辨别parentTypeArgs[i]的具体类型
      if (parentTypeArgs[i] instanceof TypeVariable) {
        for (int j = 0; j < srcTypeVars.length; j++) {
          // 子类的泛型中，有一个和父类相同（这表示子类在继承父类时，没有具体化该位置的泛型）
          // 类似： public interface Level1Mapper<E, F> extends Level0Mapper<E, F, String> {} 中的E和F。 子类中的E和父类的E是同一个对象
          // 这段代码的意思应该是 相等表示父类泛型还没有被具体化，就取子类对泛型的具体化的类型。
          if (srcTypeVars[j] == parentTypeArgs[i]) {
            noChange = false;
            // 取子类对泛型的具体化的类型。
            newParentArgs[i] = srcTypeArgs[j];
          }
        }
      } // parentTypeArgs[i]已经具体化了，直接保存到newParentArgs
      else {
        newParentArgs[i] = parentTypeArgs[i];
      }
    }
    // 对父类重新构造一个ParameterizedType对象返回
    return noChange ? parentType : new ParameterizedTypeImpl((Class<?>)parentType.getRawType(), null, newParentArgs);
  }

  private TypeParameterResolver() {
    super();
  }

  static class ParameterizedTypeImpl implements ParameterizedType {
    private Class<?> rawType;

    private Type ownerType;

    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  static class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;

    private Type[] upperBounds;

    WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  static class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
