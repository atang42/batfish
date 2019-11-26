package org.batfish.representation.juniper;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.AsExpr;
import org.batfish.datamodel.routing_policy.expr.ExplicitAs;
import org.batfish.datamodel.routing_policy.expr.LiteralAsList;
import org.batfish.datamodel.routing_policy.statement.PrependAsPath;
import org.batfish.datamodel.routing_policy.statement.Statement;

/** A {@link Statement} that prepends AS numbers to AS paths. */
public final class PsThenAsPathPrepend extends PsThen {

  private final List<Long> _asPath;

  public PsThenAsPathPrepend(Iterable<Long> asPath, String text) {
    super(text);
    _asPath = ImmutableList.copyOf(asPath);
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings w) {
    List<AsExpr> asList =
        _asPath.stream().map(ExplicitAs::new).collect(ImmutableList.toImmutableList());
    Statement statement = new PrependAsPath(new LiteralAsList(asList));
    statement.setText(getText());
    statements.add(statement);
  }
}
