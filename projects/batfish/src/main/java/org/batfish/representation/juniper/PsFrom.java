package org.batfish.representation.juniper;

import java.io.Serializable;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;

/** Represents a policy-statement "from" line in a {@link PsTerm} */
public abstract class PsFrom implements Serializable {

  private String _text;

  public PsFrom(String text) {
    _text = text;
  }

  public String getText() {
    return _text;
  }

  public abstract BooleanExpr toBooleanExpr(
      JuniperConfiguration jc, Configuration c, Warnings warnings);
}
