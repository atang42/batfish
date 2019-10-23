package org.batfish.representation.cisco;

import java.io.Serializable;
import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.statement.Statement;

public abstract class RouteMapSetLine implements Serializable {

  private String _text;

  public abstract void applyTo(
      List<Statement> statements, CiscoConfiguration cc, Configuration c, Warnings w);

  public String getText() {
    return _text;
  }

  public void setText(String text) {
    this._text = text;
  }
}
