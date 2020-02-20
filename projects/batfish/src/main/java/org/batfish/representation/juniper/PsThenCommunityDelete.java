package org.batfish.representation.juniper;

import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.communities.CommunityMatchExprReference;
import org.batfish.datamodel.routing_policy.communities.CommunitySetDifference;
import org.batfish.datamodel.routing_policy.communities.InputCommunities;
import org.batfish.datamodel.routing_policy.communities.SetCommunities;
import org.batfish.datamodel.routing_policy.statement.Statement;

public final class PsThenCommunityDelete extends PsThen {

  public PsThenCommunityDelete(String name, String text, Set<Integer> lineNums) {
    super(text, lineNums);
    _name = name;
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    if (!c.getCommunityMatchExprs().containsKey(_name)) {
      // undefined reference
      return;
    }
    Statement statement =
        new SetCommunities(
            new CommunitySetDifference(
                InputCommunities.instance(), new CommunityMatchExprReference(_name)));
    statement.setText(getText());
    statements.add(statement);
  }

  public @Nonnull String getName() {
    return _name;
  }

  private final String _name;
}
