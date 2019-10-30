package com.hebaibai.plumber.core.executer;

import com.alibaba.fastjson.JSONObject;
import com.hebaibai.plumber.config.Config;
import com.hebaibai.plumber.core.SqlEventData;
import com.hebaibai.plumber.core.EventDataExecuter;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 执行 mysql sql
 *
 * @author hjx
 */
@Slf4j
public class MysqlEventExecuter implements EventDataExecuter {

    /**
     * 异步的数据库操作客户端
     */
    private AsyncSQLClient sqlClient;

    /**
     * 数据库配置
     */
    private MysqlDataSourceConfig dataTargetConfig;

    @Override
    public void init(Vertx vertx, Config config) {
        JsonObject json = dataTargetConfig.getJson();
        log.debug("sql client :{}", json);
        sqlClient = MySQLClient.createShared(vertx, json, "plumber_pool:" + dataTargetConfig.getHost());
    }

    @Override
    public void setConfig(JSONObject config) {
        if (config.containsKey(DATA_TARGET)) {
            this.dataTargetConfig = config.getObject(DATA_TARGET, MysqlDataSourceConfig.class);
        } else {
            throw new RuntimeException(DATA_TARGET + "not find");
        }
    }

    @Override
    public void execute(SqlEventData sqlEventData) throws Exception {
        String type = sqlEventData.getType();
        //拼装sql
        StringBuilder sqlBuilder = new StringBuilder();
        List<String> wheres = new ArrayList<>();
        switch (type) {
            //insert
            case SqlEventData.TYPE_INSERT:
                List<String> columns = new ArrayList<>();
                List<String> columnValues = new ArrayList<>();
                for (Map.Entry<String, String> entry : sqlEventData.getAfter().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    columns.add("`" + key + "`");
                    if (value == null) {
                        columnValues.add("null");
                    } else {
                        columnValues.add("'" + value + "'");
                    }
                }
                sqlBuilder.append("REPLACE INTO ");
                sqlBuilder.append(sqlEventData.getTargetTable());
                sqlBuilder.append(" ( ").append(String.join(", ", columns));
                sqlBuilder.append(" ) VALUES ( ").append(String.join(", ", columnValues));
                sqlBuilder.append(");");
                insert(sqlBuilder.toString());
                break;

            //delete
            case SqlEventData.TYPE_DELETE:
                for (String key : sqlEventData.getKey()) {
                    String value = sqlEventData.getAfter().get(key);
                    if (value == null) {
                        wheres.add(key + " = null ");
                    } else {
                        wheres.add("`" + key + "`" + " = '" + value + "' ");
                    }
                }
                sqlBuilder.append("DELETE FROM ");
                sqlBuilder.append(sqlEventData.getTargetTable());
                sqlBuilder.append(" WHERE ");
                sqlBuilder.append(String.join("and ", wheres));
                delete(sqlBuilder.toString());
                break;

            //update
            case SqlEventData.TYPE_UPDATE:
                List<String> updates = new ArrayList<>();
                for (Map.Entry<String, String> entry : sqlEventData.getAfter().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    //变动之前的值
                    String beforValue = sqlEventData.getBefor().get(key);
                    //数据没有变化的，跳过
                    if (Objects.equals(value, beforValue)) {
                        continue;
                    }
                    if (value == null) {
                        updates.add("`" + key + "` = null");
                    } else {
                        updates.add("`" + key + "` = '" + value + "'");
                    }
                }
                for (String key : sqlEventData.getKey()) {
                    String value = sqlEventData.getBefor().get(key);
                    if (value == null) {
                        wheres.add(key + " = null ");
                    } else {
                        wheres.add("`" + key + "`" + " = '" + value + "' ");
                    }
                }
                // 没有更新项, 跳过
                if (updates.size() == 0) {
                    break;
                }
                sqlBuilder.append("UPDATE ")
                        .append(sqlEventData.getTargetTable())
                        .append(" SET ");
                sqlBuilder.append(String.join(", ", updates));
                sqlBuilder.append(" WHERE ");
                sqlBuilder.append(String.join("AND ", wheres));
                update(sqlBuilder.toString());
                break;

            //不做处理
            default:
                break;
        }
        System.out.println(sqlBuilder.toString());
    }

    /**
     * 执行查询
     *
     * @param sql
     */
    private void query(String sql) {
        log.info(sql);
        sqlClient.query(sql, res -> {
            if (!res.succeeded()) {
                log.error("sql-query-->失败:", res.cause());
            }
        });
    }

    /**
     * 执行更新
     *
     * @param sql
     */
    private void update(String sql) {
        log.info(sql);
        sqlClient.update(sql, res -> {
            if (!res.succeeded()) {
                res.cause().printStackTrace();
                log.error("sql-update-->失败:", res.cause());
            }
        });
    }

    /**
     * 执行删除
     *
     * @param sql
     */
    private void delete(String sql) {
        log.info(sql);
        sqlClient.update(sql, res -> {
            if (!res.succeeded()) {
                res.cause().printStackTrace();
                log.error("sql-delete:", res.cause());
            }
        });
    }

    /**
     * 执行新增
     *
     * @param sql
     */
    private void insert(String sql) {
        log.info(sql);
        sqlClient.update(sql, res -> {
            if (!res.succeeded()) {
                res.cause().printStackTrace();
                log.error("sql-insert:", res.cause());
            }
        });
    }

}
