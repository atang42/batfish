package org.batfish.representation.juniper;

import java.util.List;
import java.util.Set;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.LiteralLong;
import org.batfish.datamodel.routing_policy.statement.SetLocalPreference;
import org.batfish.datamodel.routing_policy.statement.Statement;

@ParametersAreNonnullByDefault
public final class PsThenLocalPreference extends PsThen {

  private final long _localPreference;

  public PsThenLocalPreference(long localPreference, String text, Set<Integer> lineNums) {
    super(text, lineNums);
    _localPreference = localPreference;
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    Statement statement = new SetLocalPreference(new LiteralLong(_localPreference));
    statements.add(statement);
  }

  public long getLocalPreference() {
    return _localPreference;
  }
}
