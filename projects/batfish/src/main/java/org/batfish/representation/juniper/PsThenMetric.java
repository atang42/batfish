package org.batfish.representation.juniper;

import java.util.List;
import java.util.Set;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.LiteralLong;
import org.batfish.datamodel.routing_policy.statement.SetMetric;
import org.batfish.datamodel.routing_policy.statement.Statement;

public final class PsThenMetric extends PsThen {

  private final long _metric;

  public PsThenMetric(long metric, String text, Set<Integer> lineNums) {
    super(text, lineNums);
    _metric = metric;
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    Statement statement = new SetMetric(new LiteralLong(_metric));
    statement.setText(getText());
    statements.add(statement);
  }

  public long getMetric() {
    return _metric;
  }
}
