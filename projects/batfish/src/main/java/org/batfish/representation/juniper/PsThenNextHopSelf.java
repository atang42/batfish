package org.batfish.representation.juniper;

import java.util.List;
import java.util.TreeSet;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.SelfNextHop;
import org.batfish.datamodel.routing_policy.statement.SetNextHop;
import org.batfish.datamodel.routing_policy.statement.Statement;

/** A {@link Statement} that sets the next hop to self. */
public final class PsThenNextHopSelf extends PsThen {

  public static final PsThenNextHopSelf INSTANCE = new PsThenNextHopSelf();

  private PsThenNextHopSelf() {
    // TODO: Add line numbers for next hop self
    super("next-hop self", new TreeSet<>());
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings w) {
    Statement statement = new SetNextHop(SelfNextHop.getInstance());
    statements.add(statement);
  }
}
