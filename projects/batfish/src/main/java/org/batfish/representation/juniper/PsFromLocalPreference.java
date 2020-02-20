package org.batfish.representation.juniper;

import java.util.Set;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.IntComparator;
import org.batfish.datamodel.routing_policy.expr.LiteralInt;
import org.batfish.datamodel.routing_policy.expr.MatchLocalPreference;

/** Represents a "from local-preference" line in a {@link PsTerm} */
public final class PsFromLocalPreference extends PsFrom {

  private final int _localPreference;

  public PsFromLocalPreference(int localPreference, String text, Set<Integer> lineNums) {
    super(text, lineNums);
    _localPreference = localPreference;
  }

  public int getLocalPreference() {
    return _localPreference;
  }

  @Override
  public BooleanExpr toBooleanExpr(JuniperConfiguration jc, Configuration c, Warnings warnings) {
    return new MatchLocalPreference(IntComparator.EQ, new LiteralInt(_localPreference));
  }
}
