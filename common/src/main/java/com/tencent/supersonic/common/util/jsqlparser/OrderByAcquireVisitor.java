package com.tencent.supersonic.common.util.jsqlparser;

import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.OrderByVisitorAdapter;

public class OrderByAcquireVisitor extends OrderByVisitorAdapter {

    private Set<String> fields;

    public OrderByAcquireVisitor(Set<String> fields) {
        this.fields = fields;
    }

    @Override
    public void visit(OrderByElement orderBy) {
        Expression expression = orderBy.getExpression();
        if (expression instanceof Column) {
            fields.add(((Column) expression).getColumnName());
        }
        super.visit(orderBy);
    }
}