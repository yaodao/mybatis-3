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
package org.apache.ibatis.jdbc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Clinton Begin
 * @author Jeff Butler
 * @author Adam Gent
 * @author Kazuki Shimizu
 */

/**
 *
 * 通过当前AbstractSQL类的方法 给SQLStatement对象的成员变量赋值（SQLStatement是当前类的内部静态类）
 * 在当前代码中，
 * 就是使用AbstractSQL类的方法，给成员变量sql的属性赋值
 * 之后，在其他代码中，使用的是AbstractSQL对象的sql变量。
 *
 */
public abstract class AbstractSQL<T> {

  private static final String AND = ") \nAND (";
  private static final String OR = ") \nOR (";

  private final SQLStatement sql = new SQLStatement();

  public abstract T getSelf();


  /**
   * 大写字母的函数都是当前类的函数，
   * 主要功能是 给成员变量sql的属性赋值
   * 其中 sql是SQLStatement类型的对象，SQLStatement是当前类的内部静态类。
   */

  public T UPDATE(String table) {
    sql().statementType = SQLStatement.StatementType.UPDATE;
    sql().tables.add(table);
    return getSelf();
  }

  public T SET(String sets) {
    sql().sets.add(sets);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T SET(String... sets) {
    sql().sets.addAll(Arrays.asList(sets));
    return getSelf();
  }

  public T INSERT_INTO(String tableName) {
    sql().statementType = SQLStatement.StatementType.INSERT;
    sql().tables.add(tableName);
    return getSelf();
  }

  public T VALUES(String columns, String values) {
    INTO_COLUMNS(columns);
    INTO_VALUES(values);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T INTO_COLUMNS(String... columns) {
    sql().columns.addAll(Arrays.asList(columns));
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T INTO_VALUES(String... values) {
    List<String> list = sql().valuesList.get(sql().valuesList.size() - 1);
    Collections.addAll(list, values);
    return getSelf();
  }

  public T SELECT(String columns) {
    sql().statementType = SQLStatement.StatementType.SELECT;
    sql().select.add(columns);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T SELECT(String... columns) {
    sql().statementType = SQLStatement.StatementType.SELECT;
    sql().select.addAll(Arrays.asList(columns));
    return getSelf();
  }

  public T SELECT_DISTINCT(String columns) {
    sql().distinct = true;
    SELECT(columns);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T SELECT_DISTINCT(String... columns) {
    sql().distinct = true;
    SELECT(columns);
    return getSelf();
  }

  public T DELETE_FROM(String table) {
    sql().statementType = SQLStatement.StatementType.DELETE;
    sql().tables.add(table);
    return getSelf();
  }

  public T FROM(String table) {
    sql().tables.add(table);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T FROM(String... tables) {
    sql().tables.addAll(Arrays.asList(tables));
    return getSelf();
  }

  public T JOIN(String join) {
    sql().join.add(join);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T JOIN(String... joins) {
    sql().join.addAll(Arrays.asList(joins));
    return getSelf();
  }

  public T INNER_JOIN(String join) {
    sql().innerJoin.add(join);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T INNER_JOIN(String... joins) {
    sql().innerJoin.addAll(Arrays.asList(joins));
    return getSelf();
  }

  public T LEFT_OUTER_JOIN(String join) {
    sql().leftOuterJoin.add(join);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T LEFT_OUTER_JOIN(String... joins) {
    sql().leftOuterJoin.addAll(Arrays.asList(joins));
    return getSelf();
  }

  public T RIGHT_OUTER_JOIN(String join) {
    sql().rightOuterJoin.add(join);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T RIGHT_OUTER_JOIN(String... joins) {
    sql().rightOuterJoin.addAll(Arrays.asList(joins));
    return getSelf();
  }

  public T OUTER_JOIN(String join) {
    sql().outerJoin.add(join);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T OUTER_JOIN(String... joins) {
    sql().outerJoin.addAll(Arrays.asList(joins));
    return getSelf();
  }

  public T WHERE(String conditions) {
    sql().where.add(conditions);
    sql().lastList = sql().where;
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T WHERE(String... conditions) {
    sql().where.addAll(Arrays.asList(conditions));
    sql().lastList = sql().where;
    return getSelf();
  }

  public T OR() {
    sql().lastList.add(OR);
    return getSelf();
  }

  public T AND() {
    sql().lastList.add(AND);
    return getSelf();
  }

  public T GROUP_BY(String columns) {
    sql().groupBy.add(columns);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T GROUP_BY(String... columns) {
    sql().groupBy.addAll(Arrays.asList(columns));
    return getSelf();
  }

  public T HAVING(String conditions) {
    sql().having.add(conditions);
    sql().lastList = sql().having;
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T HAVING(String... conditions) {
    sql().having.addAll(Arrays.asList(conditions));
    sql().lastList = sql().having;
    return getSelf();
  }

  public T ORDER_BY(String columns) {
    sql().orderBy.add(columns);
    return getSelf();
  }

  /**
   * @since 3.4.2
   */
  public T ORDER_BY(String... columns) {
    sql().orderBy.addAll(Arrays.asList(columns));
    return getSelf();
  }

  /**
   * Set the limit variable string(e.g. {@code "#{limit}"}).
   *
   * @param variable a limit variable string
   * @return a self instance
   * @see #OFFSET(String)
   * @since 3.5.2
   */
  public T LIMIT(String variable) {
    sql().limit = variable;
    sql().limitingRowsStrategy = SQLStatement.LimitingRowsStrategy.OFFSET_LIMIT;
    return getSelf();
  }

  /**
   * Set the limit value.
   *
   * @param value an offset value
   * @return a self instance
   * @see #OFFSET(long)
   * @since 3.5.2
   */
  public T LIMIT(int value) {
    return LIMIT(String.valueOf(value));
  }

  /**
   * Set the offset variable string(e.g. {@code "#{offset}"}).
   *
   * @param variable a offset variable string
   * @return a self instance
   * @see #LIMIT(String)
   * @since 3.5.2
   */
  public T OFFSET(String variable) {
    sql().offset = variable;
    sql().limitingRowsStrategy = SQLStatement.LimitingRowsStrategy.OFFSET_LIMIT;
    return getSelf();
  }

  /**
   * Set the offset value.
   *
   * @param value an offset value
   * @return a self instance
   * @see #LIMIT(int)
   * @since 3.5.2
   */
  public T OFFSET(long value) {
    return OFFSET(String.valueOf(value));
  }

  /**
   * Set the fetch first rows variable string(e.g. {@code "#{fetchFirstRows}"}).
   *
   * @param variable a fetch first rows variable string
   * @return a self instance
   * @see #OFFSET_ROWS(String)
   * @since 3.5.2
   */
  public T FETCH_FIRST_ROWS_ONLY(String variable) {
    sql().limit = variable;
    sql().limitingRowsStrategy = SQLStatement.LimitingRowsStrategy.ISO;
    return getSelf();
  }

  /**
   * Set the fetch first rows value.
   *
   * @param value a fetch first rows value
   * @return a self instance
   * @see #OFFSET_ROWS(long)
   * @since 3.5.2
   */
  public T FETCH_FIRST_ROWS_ONLY(int value) {
    return FETCH_FIRST_ROWS_ONLY(String.valueOf(value));
  }

  /**
   * Set the offset rows variable string(e.g. {@code "#{offset}"}).
   *
   * @param variable a offset rows variable string
   * @return a self instance
   * @see #FETCH_FIRST_ROWS_ONLY(String)
   * @since 3.5.2
   */
  public T OFFSET_ROWS(String variable) {
    sql().offset = variable;
    sql().limitingRowsStrategy = SQLStatement.LimitingRowsStrategy.ISO;
    return getSelf();
  }

  /**
   * Set the offset rows value.
   *
   * @param value an offset rows value
   * @return a self instance
   * @see #FETCH_FIRST_ROWS_ONLY(int)
   * @since 3.5.2
   */
  public T OFFSET_ROWS(long value) {
    return OFFSET_ROWS(String.valueOf(value));
  }

  /*
   * used to add a new inserted row while do multi-row insert.
   *
   * @since 3.5.2
   */
  public T ADD_ROW() {
    sql().valuesList.add(new ArrayList<>());
    return getSelf();
  }

  private SQLStatement sql() {
    return sql;
  }

  public <A extends Appendable> A usingAppender(A a) {
    sql().sql(a);
    return a;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    // 构造sql串，并追加到sb中
    sql().sql(sb);
    // 这里会返回一个sql语句串
    return sb.toString();
  }

  private static class SafeAppendable {
    private final Appendable a;
    private boolean empty = true;

    public SafeAppendable(Appendable a) {
      super();
      this.a = a;
    }

    public SafeAppendable append(CharSequence s) {
      try {
        if (empty && s.length() > 0) {
          empty = false;
        }
        a.append(s);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    public boolean isEmpty() {
      return empty;
    }

  }

  /**
   * 该类用于构造四种类型的sql语句串（DELETE, INSERT, SELECT, UPDATE）
   *
   * 调用该类的sql(Appendable a)方法后，就构造完成一个sql语句串，并追加到a中了，
   * 之后使用a.toString()就可以得到该sql语句串。
   */
  private static class SQLStatement {

    public enum StatementType {
      DELETE, INSERT, SELECT, UPDATE
    }

    private enum LimitingRowsStrategy {
      NOP {
        @Override
        protected void appendClause(SafeAppendable builder, String offset, String limit) {
          // NOP
        }
      },
      ISO {
        @Override
        protected void appendClause(SafeAppendable builder, String offset, String limit) {
          if (offset != null) {
            builder.append(" OFFSET ").append(offset).append(" ROWS");
          }
          if (limit != null) {
            builder.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");
          }
        }
      },
      OFFSET_LIMIT {
        @Override
        protected void appendClause(SafeAppendable builder, String offset, String limit) {
          if (limit != null) {
            builder.append(" LIMIT ").append(limit);
          }
          if (offset != null) {
            builder.append(" OFFSET ").append(offset);
          }
        }
      };

      protected abstract void appendClause(SafeAppendable builder, String offset, String limit);

    }
    // sql语句串的类型
    StatementType statementType;
    List<String> sets = new ArrayList<>();
    List<String> select = new ArrayList<>();
    List<String> tables = new ArrayList<>();
    List<String> join = new ArrayList<>();
    List<String> innerJoin = new ArrayList<>();
    List<String> outerJoin = new ArrayList<>();
    List<String> leftOuterJoin = new ArrayList<>();
    List<String> rightOuterJoin = new ArrayList<>();
    List<String> where = new ArrayList<>();
    List<String> having = new ArrayList<>();
    List<String> groupBy = new ArrayList<>();
    List<String> orderBy = new ArrayList<>();
    // 暂时看来该变量被赋值后，没有被用到
    List<String> lastList = new ArrayList<>();
    List<String> columns = new ArrayList<>();
    // 记录集合（一个元素 就是 一条记录的所有字段值）
    List<List<String>> valuesList = new ArrayList<>();
    boolean distinct;
    String offset;
    String limit;
    LimitingRowsStrategy limitingRowsStrategy = LimitingRowsStrategy.NOP;

    public SQLStatement() {
      // Prevent Synthetic Access
      valuesList.add(new ArrayList<>());
    }

    /**
     * 调用 sqlClause(builder, "SELECT DISTINCT", select, "", "", ", ");
     * 入参select举例：["P.ID", "P.USERNAME, P.PASSWORD", "P.FULL_NAME"]
     * 则，builder后追加串 "SELECT DISTINCT P.ID, P.USERNAME, P.PASSWORD, P.FULL_NAME"
     */

    // sqlClause(builder, "WHERE", where, "(", ")", " AND ");
    // sqlClause(builder, "INSERT INTO", tables, "", "", "");
    // sqlClause(builder, "INNER JOIN", innerJoin, "", "", "\nINNER JOIN ");
    // 本函数只负责向builder追加参数， 入参parts是现成的条件（不含有sql语句关键字的条件，不需要再处理的条件）
    private void sqlClause(SafeAppendable builder, String keyword, List<String> parts, String open, String close,
                           String conjunction) {
      // parts列表不空，才会向builder追加串，进if。
      if (!parts.isEmpty()) {
        if (!builder.isEmpty()) {
          // builder不为空，就追加一个换行
          builder.append("\n");
        }
        builder.append(keyword);
        builder.append(" ");
        builder.append(open);
        String last = "________";
        for (int i = 0, n = parts.size(); i < n; i++) {
          String part = parts.get(i);
          // i>0 表示只有一个元素的时候，不需要加分隔符
          if (i > 0 && !part.equals(AND) && !part.equals(OR) && !last.equals(AND) && !last.equals(OR)) {
            // 追加分隔符
            builder.append(conjunction);
          }
          builder.append(part);
          // 这句作用是
          // 避免 串 ") AND (" 或者串 ") \nOR (" 后直接跟conjunction
          last = part;
        }
        builder.append(close);
      }
    }


    /**
     *
     * 向builder追加完整的select语句。这是SQLStatement类内的函数。
     * 当前SQLStatement对象的成员变量 select/tables/where/groupBy/having/orderBy 都不为空时， 构造的select语句才是完整的
     * 返回的串形如 "org.apache.ibatis.jdbc.AbstractSQL$SafeAppendable@411f53a0" 返回值不会被用到，可忽略
     *
     */
    private String selectSQL(SafeAppendable builder) {
      if (distinct) {
        // 举例： select为 ["P.ID", "P.USERNAME, P.PASSWORD", "P.FULL_NAME"]
        // 调用sqlClause()后，
        // builder后面追加串 "SELECT DISTINCT P.ID, P.USERNAME, P.PASSWORD, P.FULL_NAME"
        sqlClause(builder, "SELECT DISTINCT", select, "", "", ", ");
      } else {
        sqlClause(builder, "SELECT", select, "", "", ", ");
      }

      // builder后追加串 "FROM PERSON P"
      sqlClause(builder, "FROM", tables, "", "", ", ");
      joins(builder);
      sqlClause(builder, "WHERE", where, "(", ")", " AND ");
      sqlClause(builder, "GROUP BY", groupBy, "", "", ", ");
      sqlClause(builder, "HAVING", having, "(", ")", " AND ");
      sqlClause(builder, "ORDER BY", orderBy, "", "", ", ");
      limitingRowsStrategy.appendClause(builder, offset, limit);
      return builder.toString();
    }

    // 向builder追加表连接的串，这里涵盖了所有种类的表连接。
    // 为什么不需要先判断连接类型，再追加该连接类型的串呢？？
    // 因为，当列表 join/innerJoin/outerJoin/leftOuterJoin 某个为空的时候，不会向builder追加串
    private void joins(SafeAppendable builder) {
      sqlClause(builder, "JOIN", join, "", "", "\nJOIN ");
      sqlClause(builder, "INNER JOIN", innerJoin, "", "", "\nINNER JOIN ");
      sqlClause(builder, "OUTER JOIN", outerJoin, "", "", "\nOUTER JOIN ");
      sqlClause(builder, "LEFT OUTER JOIN", leftOuterJoin, "", "", "\nLEFT OUTER JOIN ");
      sqlClause(builder, "RIGHT OUTER JOIN", rightOuterJoin, "", "", "\nRIGHT OUTER JOIN ");
    }

    /**
     * 追加字符串如下：(串里有空格)
       INSERT INTO items
        (name, city, price, number, picture)
       VALUES ('耐克运动鞋', '广州', 500, 1000, '003.jpg')
       , ('耐克运动鞋2', '广州2', 500, 1000, '002.jpg')
       , ('耐克运动鞋3', '广州3', 500, 1000, '001.jpg')
     */
    private String insertSQL(SafeAppendable builder) {
      sqlClause(builder, "INSERT INTO", tables, "", "", "");
      sqlClause(builder, "", columns, "(", ")", ", ");
      // 生成串 VALUES (...), (...)
      for (int i = 0; i < valuesList.size(); i++) {
        sqlClause(builder, i > 0 ? "," : "VALUES", valuesList.get(i), "(", ")", ", ");
      }
      // 这个返回的串形如
      // org.apache.ibatis.jdbc.AbstractSQL$SafeAppendable@411f53a0
      return builder.toString();
    }

    /**
     * 追加字符串如下：
     DELETE FROM items
     WHERE (name = ?) AND (picture = ?)
      LIMIT 10
     */
    private String deleteSQL(SafeAppendable builder) {
      sqlClause(builder, "DELETE FROM", tables, "", "", "");
      sqlClause(builder, "WHERE", where, "(", ")", " AND ");
      // 给builder追加串 " LIMIT 10"
      limitingRowsStrategy.appendClause(builder, null, limit);
      return builder.toString();
    }

    /**
     * 追加字符串如下：
     UPDATE items
     SET name = #{name}, picture = #{picture}
     WHERE (name = #{name} AND picture = #{picture})
      LIMIT 10
     */
    private String updateSQL(SafeAppendable builder) {
      sqlClause(builder, "UPDATE", tables, "", "", "");
      // 可以使用UPDATE JOIN，多表更新
      joins(builder);
      sqlClause(builder, "SET", sets, "", "", ", ");
      sqlClause(builder, "WHERE", where, "(", ")", " AND ");
      limitingRowsStrategy.appendClause(builder, null, limit);
      return builder.toString();
    }

    /*
     * 使用当前对象的那些列表属性，构造一个sql串，并追加到builder
     * 真正实现了使用列表属性构造sql串 是里面的***SQL() 函数，这些函数类似于平时使用的do***() 函数
     *
     * 这是SQLStatement类的函数
     * 返回的串形如 "org.apache.ibatis.jdbc.AbstractSQL$SafeAppendable@411f53a0" 返回值不会被用到，可忽略
     */
    public String sql(Appendable a) {
      SafeAppendable builder = new SafeAppendable(a);
      if (statementType == null) {
        return null;
      }

      String answer;

      switch (statementType) {
        case DELETE:
          answer = deleteSQL(builder);
          break;

        case INSERT:
          answer = insertSQL(builder);
          break;

        case SELECT:
          answer = selectSQL(builder);
          break;

        case UPDATE:
          answer = updateSQL(builder);
          break;

        default:
          answer = null;
      }

      return answer;
    }
  }
}
