package com.tencent.supersonic.common.util.jsqlparser;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
public class DateRangeReplaceVisitor extends ExpressionVisitorAdapter {

  @Override
  public void visit(MinorThanEquals expr) {
    super.visit(expr);
    String date = expr.getRightExpression().toString();
    log.info("right value:{}", date);
    Optional<String> nextDay = getNextDay(date);
    nextDay.ifPresent(s -> expr.setRightExpression(new StringValue(s)));
  }


  public static Optional<String> getNextDay(String originDate) {
    String date = originDate.replace("'", "");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    try {
      LocalDate localDate = LocalDate.parse(date, formatter);
      localDate = localDate.plusDays(1);
      return Optional.of(localDate.format(formatter));
    } catch (Exception e) {
      log.error("parse date error:{}", date, e);
      return Optional.empty();
    }
  }
}
