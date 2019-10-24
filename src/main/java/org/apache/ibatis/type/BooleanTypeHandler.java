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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 */
public class BooleanTypeHandler extends BaseTypeHandler<Boolean> {

  @Override
  /**
   * 因为上层方法已经对parameter进行了判空，传到这里的parameter肯定不是空，
   * 所以这里的方法名字是 setNonNullParameter
   *
   * 将ps的第i个位置设置为parameter。
   * parameter是java的数据类型，各个厂商在实现jdbc的时候，会将这个java类型转换成数据库中的类型。
   */
  public void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setBoolean(i, parameter);
  }

  @Override
  // 从rs中取当前行的columnName对应的字段值，
  // 当表中该字段值为NULL时，返回null；否则，则返回该字段值。
  public Boolean getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    boolean result = rs.getBoolean(columnName);
    return !result && rs.wasNull() ? null : result;
  }

  @Override
  // 从rs中取当前行的第columnIndex个字段的值，
  // 当表中该字段值为NULL时，返回null；否则，则返回该字段值。
  public Boolean getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    boolean result = rs.getBoolean(columnIndex);
    return !result && rs.wasNull() ? null : result;
  }

  @Override
  public Boolean getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    boolean result = cs.getBoolean(columnIndex);
    return !result && cs.wasNull() ? null : result;
  }
}
