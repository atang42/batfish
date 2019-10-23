package org.batfish.representation.cisco;

import java.io.Serializable;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;

public abstract class RouteMapMatchLine implements Serializable {

  private String _text;

  public RouteMapMatchLine(String text) {
    _text = text;
  }

  public abstract BooleanExpr toBooleanExpr(Configuration c, CiscoConfiguration cc, Warnings w);

  public String getText() {
    return _text;
  }

  public void setText(String text) {
    this._text = text;
  }
}
