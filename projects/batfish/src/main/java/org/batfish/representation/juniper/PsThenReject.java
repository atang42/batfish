package org.batfish.representation.juniper;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.TreeSet;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BooleanExprs;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.Statements;

public final class PsThenReject extends PsThen {

  public static final PsThenReject INSTANCE = new PsThenReject();

  private PsThenReject() {
    // TODO: Add line numbers for reject
    super("reject", new TreeSet<>());
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    If ifStatement =
        new If(
            BooleanExprs.CALL_EXPR_CONTEXT,
            ImmutableList.of(Statements.ReturnFalse.toStaticStatement()),
            ImmutableList.of(Statements.ExitReject.toStaticStatement()));
    ifStatement.setText(getText());
    statements.add(ifStatement);
  }
}
