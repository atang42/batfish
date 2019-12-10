package org.batfish.representation.juniper;

import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;

public final class PsThenNextPolicy extends PsThen {

  public static final PsThenNextPolicy INSTANCE = new PsThenNextPolicy();

  private PsThenNextPolicy() {
    super("next policy;");
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings w) {
    Statement statement = Statements.FallThrough.toStaticStatement();
    statements.add(statement);
  }
}
