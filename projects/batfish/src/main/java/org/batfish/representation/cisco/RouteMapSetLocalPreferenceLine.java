package org.batfish.representation.cisco;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.LongExpr;
import org.batfish.datamodel.routing_policy.statement.SetLocalPreference;
import org.batfish.datamodel.routing_policy.statement.Statement;

public class RouteMapSetLocalPreferenceLine extends RouteMapSetLine {

  private LongExpr _localPreference;

  public RouteMapSetLocalPreferenceLine(LongExpr localPreference, String text) {
    setText(text);
    _localPreference = localPreference;
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    Statement stmt = new SetLocalPreference(_localPreference);
    stmt.setText(getText());
    statements.add(stmt);
  }

  public LongExpr getLocalPreference() {
    return _localPreference;
  }
}
