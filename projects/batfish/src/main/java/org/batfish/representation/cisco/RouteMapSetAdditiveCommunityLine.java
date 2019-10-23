package org.batfish.representation.cisco;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.bgp.community.StandardCommunity;
import org.batfish.datamodel.routing_policy.expr.LiteralCommunitySet;
import org.batfish.datamodel.routing_policy.statement.AddCommunity;
import org.batfish.datamodel.routing_policy.statement.Statement;

public class RouteMapSetAdditiveCommunityLine extends RouteMapSetLine {

  private List<StandardCommunity> _communities;

  public RouteMapSetAdditiveCommunityLine(List<StandardCommunity> communities, String text) {
    setText(text);
    _communities = communities;
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    Statement stmt = new AddCommunity(new LiteralCommunitySet(_communities));
    stmt.setText(getText());
    statements.add(stmt);
  }

  public List<StandardCommunity> getCommunities() {
    return _communities;
  }
}
