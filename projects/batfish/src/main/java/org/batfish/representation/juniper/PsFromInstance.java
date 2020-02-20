package org.batfish.representation.juniper;

import java.util.Set;
import javax.annotation.Nonnull;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.MatchSourceVrf;

/** Represents a "from instance" line in a {@link PsTerm} */
public class PsFromInstance extends PsFrom {

  private String _routingInstanceName;

  public PsFromInstance(@Nonnull String routingInstanceName, String text, Set<Integer> lineNums) {
    super(text, lineNums);
    _routingInstanceName = routingInstanceName;
  }

  String getRoutingInstanceName() {
    return _routingInstanceName;
  }

  @Override
  public BooleanExpr toBooleanExpr(JuniperConfiguration jc, Configuration c, Warnings warnings) {
    return new MatchSourceVrf(_routingInstanceName);
  }
}
