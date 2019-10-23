package org.batfish.minesweeper.question.findroutefilterlines;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.PacketHeaderConstraints;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.questions.Question;
import org.batfish.question.findmatchingfilterlines.FindMatchingFilterLinesQuestion.Action;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.RoutingPolicySpecifier;
import org.batfish.specifier.SpecifierFactories;
import org.batfish.specifier.parboiled.ParboiledRoutingPolicySpecifier;

public class FindRouteFilterLinesQuestion extends Question {

  private static final String PROP_NODES = "nodes";
  private static final String PROP_ROUTE_POLICIES = "routePolicies";
  private static final String PROP_ACTION = "action";
  private static final String PROP_HEADERS = "headers";
  private static final String PROP_PREFIX = "prefix";
  private static final String PROP_IGNORE_COMPOSITES = "ignoreComposites";

  @Nullable private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nullable private final String _policies;
  @Nonnull private final RoutingPolicySpecifier _policySpecifier;

  @Nonnull private final String _prefix;

  //@Nullable private final Action _action;
  //@Nonnull private final PacketHeaderConstraints _headerConstraints;

  @JsonCreator
  private static FindRouteFilterLinesQuestion create(
      @JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_ROUTE_POLICIES) @Nullable String filters,
      @JsonProperty(PROP_ACTION) @Nullable Action action,
      @JsonProperty(PROP_PREFIX) @Nullable String prefix,
      @JsonProperty(PROP_HEADERS) @Nullable PacketHeaderConstraints headerConstraints,
      @JsonProperty(PROP_IGNORE_COMPOSITES) @Nullable Boolean ignoreComposites) {
    return new FindRouteFilterLinesQuestion(nodes, filters, prefix);
  }

  public FindRouteFilterLinesQuestion(@Nullable String nodes, @Nullable String filters,
      @Nullable String prefix) {
    _nodes = nodes;
    _nodeSpecifier =
        SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
    _policies = filters;
    _policySpecifier = SpecifierFactories.getRoutingPolicySpecifierOrDefault(filters,
        ParboiledRoutingPolicySpecifier.parse(".*"));
    _prefix = prefix;
  }

  @Override public boolean getDataPlane() {
    return false;
  }

  @Override public String getName() {
    return "findroutefilterlines";
  }

  @JsonProperty(PROP_NODES)
  @Nullable
  private String getNodes() {
    return _nodes;
  }

  @Nonnull
  @JsonIgnore
  NodeSpecifier getNodeSpecifier() {
    return _nodeSpecifier;
  }

  @Nullable
  @JsonProperty(PROP_ROUTE_POLICIES)
  private String getPolicies() {
    return _policies;
  }

  @Nonnull
  @JsonIgnore RoutingPolicySpecifier getPolicySpecifier() {
    return _policySpecifier;
  }

  @JsonProperty(PROP_PREFIX)
  @Nonnull public String getPrefix() {
    return _prefix;
  }
}
