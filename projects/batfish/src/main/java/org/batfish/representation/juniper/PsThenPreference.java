package org.batfish.representation.juniper;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.LiteralInt;
import org.batfish.datamodel.routing_policy.statement.SetAdministrativeCost;
import org.batfish.datamodel.routing_policy.statement.Statement;

/**
 * Represents the action in Juniper's routing policy(policy statement) which sets the preference for
 * a matched route
 */
public final class PsThenPreference extends PsThen {

  private final int _preference;

  public PsThenPreference(int preference, String text) {
    super(text);
    _preference = preference;
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    Statement statement = new SetAdministrativeCost(new LiteralInt(_preference));
    statement.setText(getText());
    statements.add(statement);
  }

  public int getPreference() {
    return _preference;
  }
}
