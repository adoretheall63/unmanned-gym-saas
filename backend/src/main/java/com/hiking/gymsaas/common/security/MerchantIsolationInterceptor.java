package com.hiking.gymsaas.common.security;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.SystemMetaObject;

public class MerchantIsolationInterceptor extends TenantLineInnerInterceptor {
    public MerchantIsolationInterceptor() {
        super(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(CurrentIdentity.merchantId()); }
            @Override public String getTenantIdColumn() { return "merchant_id"; }
        });
    }

    @Override
    protected Column getAliasColumn(Table table) {
        String name = name(table);
        if (name.equals("customer_user") || name.equals("api_request")) {
            throw new IllegalStateException("Global identities and idempotency require their explicitly scoped mappers");
        }
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        return new Column(alias + "." + (name.equals("merchant") ? "id" : "merchant_id"));
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer timeout) {
        var statement = PluginUtils.mpStatementHandler(sh);
        var mapped = statement.mappedStatement();
        if (mapped.getSqlCommandType() == SqlCommandType.SELECT) {
            super.beforePrepare(sh, connection, timeout);
        } else if (!InterceptorIgnoreHelper.willIgnoreTenantLine(mapped.getId())) {
            var sql = statement.mPBoundSql();
            sql.sql(parserMulti(sql.sql(), mapped.getSqlCommandType() == SqlCommandType.INSERT ? statement.boundSql() : null));
        }
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object context) {
        CurrentIdentity.admin();
        // shortcut: scoped writes accept VALUES only; add explicit ownership checks before enabling INSERT SELECT/upserts.
        if (name(insert.getTable()).equals("merchant") || insert.getValues() == null
                || insert.getColumns() == null
                || (insert.getDuplicateUpdateSets() != null && !insert.getDuplicateUpdateSets().isEmpty())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        int tenantColumn = -1;
        for (int i = 0; i < insert.getColumns().size(); i++) {
            if (insert.getColumns().get(i).getColumnName().replace("`", "").equalsIgnoreCase("merchant_id")) tenantColumn = i;
        }
        if (tenantColumn >= 0) {
            var expressions = insert.getValues().getExpressions();
            if (expressions instanceof ParenthesedExpressionList<?>) {
                verify(expressions.get(tenantColumn), (BoundSql) context);
            } else {
                for (Expression expression : expressions) {
                    if (!(expression instanceof ExpressionList<?> row)) throw new BusinessException(ErrorCode.FORBIDDEN);
                    verify((Expression) row.get(tenantColumn), (BoundSql) context);
                }
            }
        }
        super.processInsert(insert, index, sql, context);
    }

    private void verify(Expression expression, BoundSql sql) {
        Object value;
        if (expression instanceof LongValue literal) value = literal.getValue();
        else if (expression instanceof JdbcParameter parameter && sql != null) {
            String property = sql.getParameterMappings().get(parameter.getIndex() - 1).getProperty();
            value = sql.hasAdditionalParameter(property) ? sql.getAdditionalParameter(property)
                    : SystemMetaObject.forObject(sql.getParameterObject()).getValue(property);
        } else throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!(value instanceof Number) || !Long.toString(CurrentIdentity.merchantId()).equals(value.toString())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object context) {
        for (var set : update.getUpdateSets()) {
            for (var column : set.getColumns()) {
                if (List.of("id", "merchant_id").contains(column.getColumnName().replace("`", "").toLowerCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
            }
        }
        super.processUpdate(update, index, sql, context);
    }

    private static String name(Table table) { return table.getName().replace("`", "").toLowerCase(Locale.ROOT); }
}
