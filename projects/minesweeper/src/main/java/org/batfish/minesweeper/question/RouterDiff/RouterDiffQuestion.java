package org.batfish.minesweeper.question.RouterDiff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.PacketHeaderConstraints;
import org.batfish.datamodel.questions.Question;
import org.batfish.question.findmatchingfilterlines.FindMatchingFilterLinesQuestion.Action;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.RoutingPolicySpecifier;
import org.batfish.specifier.SpecifierFactories;
import org.batfish.specifier.parboiled.ParboiledRoutingPolicySpecifier;

public class RouterDiffQuestion extends Question {

  private static final String PROP_NODES = "nodes";

  @Nonnull private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;

  //@Nullable private final Action _action;
  //@Nonnull private final PacketHeaderConstraints _headerConstraints;

  @JsonCreator
  private static RouterDiffQuestion create(
      @JsonProperty(PROP_NODES) @Nullable String nodes) {
    return new RouterDiffQuestion(nodes);
  }

  public RouterDiffQuestion(@Nullable String nodes) {
    _nodes = (nodes != null) ? nodes : ".*";
    _nodeSpecifier =
        SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
  }

  @Override public boolean getDataPlane() {
    return false;
  }

  @Override public String getName() {
    return "routerdiff";
  }

  @JsonProperty(PROP_NODES)
  @Nonnull
  private String getNodes() {
    return _nodes;
  }

  @Nonnull
  @JsonIgnore
  NodeSpecifier getNodeSpecifier() {
    return _nodeSpecifier;
  }

}
