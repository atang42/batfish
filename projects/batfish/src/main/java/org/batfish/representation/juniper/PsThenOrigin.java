package org.batfish.representation.juniper;

import java.util.List;
import java.util.Set;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.OriginType;
import org.batfish.datamodel.routing_policy.expr.LiteralOrigin;
import org.batfish.datamodel.routing_policy.statement.SetOrigin;
import org.batfish.datamodel.routing_policy.statement.Statement;

/** A {@link Statement} that sets the origin type on a route. */
public final class PsThenOrigin extends PsThen {

  private final OriginType _originType;

  public PsThenOrigin(OriginType originType, String text, Set<Integer> lineNums) {
    super(text, lineNums);
    _originType = originType;
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    Statement statement = new SetOrigin(new LiteralOrigin(_originType, null));
    statement.setText(getText());
    statements.add(statement);
  }

  public OriginType getOriginType() {
    return _originType;
  }
}
