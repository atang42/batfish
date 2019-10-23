package org.batfish.representation.cisco;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.LongExpr;
import org.batfish.datamodel.routing_policy.statement.SetMetric;
import org.batfish.datamodel.routing_policy.statement.Statement;

public class RouteMapSetMetricLine extends RouteMapSetLine {

  private LongExpr _metric;

  public RouteMapSetMetricLine(LongExpr metric, String text) {
    setText(text);
    _metric = metric;
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    Statement stmt = new SetMetric(_metric);
    stmt.setText(getText());
    statements.add(stmt);
  }

  public LongExpr getMetric() {
    return _metric;
  }
}
