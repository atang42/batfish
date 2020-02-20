package org.batfish.representation.juniper;

import java.util.List;
import java.util.Set;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;

public class PsThenDefaultActionAccept extends PsThen {

  public PsThenDefaultActionAccept(String text, Set<Integer> lineNums) {
    super(text, lineNums);
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    Statement statement = Statements.SetDefaultActionAccept.toStaticStatement();
    statement.setText(getText());
    statements.add(statement);
  }
}
