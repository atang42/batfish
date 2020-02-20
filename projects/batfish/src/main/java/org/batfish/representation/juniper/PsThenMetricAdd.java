package org.batfish.representation.juniper;

import java.util.List;
import java.util.Set;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.IncrementMetric;
import org.batfish.datamodel.routing_policy.statement.SetMetric;
import org.batfish.datamodel.routing_policy.statement.Statement;

/** A {@link Statement} that increments the metric on a route. */
public final class PsThenMetricAdd extends PsThen {

  private final long _metric;

  public PsThenMetricAdd(long metric, String text, Set<Integer> lineNums) {
    super(text, lineNums);
    _metric = metric;
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    Statement statement = new SetMetric(new IncrementMetric(_metric));
    statement.setText(getText());
    statements.add(statement);
  }

  public long getMetric() {
    return _metric;
  }
}
