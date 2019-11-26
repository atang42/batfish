package org.batfish.representation.juniper;

import java.io.Serializable;
import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.statement.Statement;

public abstract class PsThen implements Serializable {

  private String _text;

  public PsThen(String text) {
    _text = text;
  }

  public String getText() {
    return _text;
  }

  public abstract void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings);
}
