package org.batfish.representation.cisco;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.routing_policy.expr.IpNextHop;
import org.batfish.datamodel.routing_policy.statement.SetNextHop;
import org.batfish.datamodel.routing_policy.statement.Statement;

public class RouteMapSetNextHopLine extends RouteMapSetLine {

  private List<Ip> _nextHops;

  public RouteMapSetNextHopLine(List<Ip> nextHops, String text) {
    setText(text);
    _nextHops = nextHops;
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    // TODO: something with destination-vrf
    Statement stmt = new SetNextHop(new IpNextHop(_nextHops));
    stmt.setText(getText());
    statements.add(stmt);
  }

  public List<Ip> getNextHops() {
    return _nextHops;
  }
}
