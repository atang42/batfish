package org.batfish.representation.juniper;

import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.communities.CommunitySetReference;
import org.batfish.datamodel.routing_policy.communities.SetCommunities;
import org.batfish.datamodel.routing_policy.statement.Statement;

@ParametersAreNonnullByDefault
public final class PsThenCommunitySet extends PsThen {

  public PsThenCommunitySet(String name, JuniperConfiguration configuration, String text,
      Set<Integer> lineNums) {
    super(text, lineNums);
    _name = name;
    _configuration = configuration;
  }

  @Override
  public void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings) {
    if (!c.getCommunitySets().containsKey(_name)) {
      // undefined reference; or not converted because it contains only regexes
      return;
    }
    _configuration.getOrCreateNamedCommunitiesUsedForSet().add(_name);
    Statement statement = new SetCommunities(new CommunitySetReference(_name));
    statement.setText(getText());
    statements.add(statement);
  }

  public @Nonnull String getName() {
    return _name;
  }

  private JuniperConfiguration _configuration;
  private final String _name;
}
