package org.batfish.representation.cisco;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;

public class RouteMapSetCommunityNoneLine extends RouteMapSetLine {

  public RouteMapSetCommunityNoneLine(String text) {
    setText(text);
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    Statement stmt = Statements.DeleteAllCommunities.toStaticStatement();
    stmt.setText(getText());
    statements.add(stmt);
  }
}
