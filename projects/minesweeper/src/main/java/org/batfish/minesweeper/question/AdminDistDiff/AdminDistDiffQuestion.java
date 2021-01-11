package org.batfish.minesweeper.question.AdminDistDiff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.questions.Question;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierFactories;

public class AdminDistDiffQuestion extends Question {

  private static final String PROP_NODES = "nodes";
  private static final String PROP_IGNORED_PREFIX_RANGES = "ignored";

  @Nonnull private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;

  //@Nullable private final Action _action;
  //@Nonnull private final PacketHeaderConstraints _headerConstraints;

  @JsonCreator
  private static AdminDistDiffQuestion create(
      @JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_IGNORED_PREFIX_RANGES) @Nullable String ignored) {
    return new AdminDistDiffQuestion(nodes, ignored);
  }

  public AdminDistDiffQuestion(@Nullable String nodes, @Nullable String ignored) {
    _nodes = (nodes != null) ? nodes : ".*";
    _nodeSpecifier =
        SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
  }

  @Override public boolean getDataPlane() {
    return false;
  }

  @Override public String getName() {
    return "admindistdiff";
  }

  @JsonProperty(PROP_NODES)
  @Nonnull
  public String getNodes() {
    return _nodes;
  }

  @Nonnull
  @JsonIgnore
  public NodeSpecifier getNodeSpecifier() {
    return _nodeSpecifier;
  }


}
