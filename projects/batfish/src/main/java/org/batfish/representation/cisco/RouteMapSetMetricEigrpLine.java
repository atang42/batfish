package org.batfish.representation.cisco;

import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.eigrp.EigrpMetricValues;
import org.batfish.datamodel.routing_policy.expr.LiteralEigrpMetric;
import org.batfish.datamodel.routing_policy.statement.SetEigrpMetric;
import org.batfish.datamodel.routing_policy.statement.Statement;

@ParametersAreNonnullByDefault
public final class RouteMapSetMetricEigrpLine extends RouteMapSetLine {
  private final EigrpMetricValues _metric;

  public RouteMapSetMetricEigrpLine(EigrpMetricValues metric, String text) {
    setText(text);
    _metric = metric;
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    Statement stmt = new SetEigrpMetric(new LiteralEigrpMetric(_metric));
    stmt.setText(getText());
    statements.add(stmt);
  }
}
