package org.batfish.representation.cisco;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.CommunityList;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.NamedCommunitySet;
import org.batfish.datamodel.routing_policy.statement.DeleteCommunity;
import org.batfish.datamodel.routing_policy.statement.Statement;

public class RouteMapSetDeleteCommunityLine extends RouteMapSetLine {

  private final String _listName;

  public RouteMapSetDeleteCommunityLine(String listName, String text) {
    setText(text);
    _listName = listName;
  }

  @Override
  public void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w) {
    CommunityList list = c.getCommunityLists().get(_listName);
    if (list != null) {
      Statement stmt = new DeleteCommunity(new NamedCommunitySet(_listName));
      stmt.setText(getText());
      statements.add(stmt);
    }
  }

  public String getListName() {
    return _listName;
  }
}
