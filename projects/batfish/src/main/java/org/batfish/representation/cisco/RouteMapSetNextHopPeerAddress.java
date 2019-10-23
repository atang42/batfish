package org.batfish.representation.cisco;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BgpPeerAddressNextHop;
import org.batfish.datamodel.routing_policy.statement.SetNextHop;
import org.batfish.datamodel.routing_policy.statement.Statement;

public class RouteMapSetNextHopPeerAddress extends RouteMapSetLine {

  public RouteMapSetNextHopPeerAddress(String text) {
    setText(text);
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    Statement stmt = new SetNextHop(PeerAddressNextHop.getInstance());
    stmt.setText(getText());
    statements.add(stmt);
  }
}
