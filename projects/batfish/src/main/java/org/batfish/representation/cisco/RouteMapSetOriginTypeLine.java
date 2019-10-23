package org.batfish.representation.cisco;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.OriginExpr;
import org.batfish.datamodel.routing_policy.statement.SetOrigin;
import org.batfish.datamodel.routing_policy.statement.Statement;

public class RouteMapSetOriginTypeLine extends RouteMapSetLine {

  private OriginExpr _originExpr;

  public RouteMapSetOriginTypeLine(OriginExpr originExpr, String text) {
    setText(text);
    _originExpr = originExpr;
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    Statement stmt = new SetOrigin(_originExpr);
    stmt.setText(getText());
    statements.add(stmt);
  }

  public OriginExpr getOriginExpr() {
    return _originExpr;
  }

  public void setOriginExpr(OriginExpr originExpr) {
    _originExpr = originExpr;
  }
}
