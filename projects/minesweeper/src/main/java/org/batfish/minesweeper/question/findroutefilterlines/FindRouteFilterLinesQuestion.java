package org.batfish.minesweeper.question.findroutefilterlines;

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

public class FindRouteFilterLinesQuestion extends Question {

  private static final String PROP_NODES = "nodes";
  private static final String PROP_ROUTE_POLICIES = "routePolicies";
  private static final String PROP_ACTION = "action";
  private static final String PROP_HEADERS = "headers";
  private static final String PROP_PREFIX_RANGE = "prefixRange";
  private static final String PROP_INCLUDED_PACKET = "includedPacket";
  private static final String PROP_INTERFACE = "interface";
  private static final String PROP_ACCEPTED = "accepted";
  private static final String PROP_IGNORE_COMPOSITES = "ignoreComposites";

  @Nullable private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nullable private final String _policies;
  @Nonnull private final RoutingPolicySpecifier _policySpecifier;

  @Nullable private final String _prefixRange;
  @Nullable private final String _includedPacket;
  @Nullable private final String _interface;
  @Nullable private final String _accepted;

  //@Nullable private final Action _action;
  //@Nonnull private final PacketHeaderConstraints _headerConstraints;

  @JsonCreator
  private static FindRouteFilterLinesQuestion create(
      @JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_ROUTE_POLICIES) @Nullable String filters,
      @JsonProperty(PROP_ACTION) @Nullable Action action,
      @JsonProperty(PROP_PREFIX_RANGE) @Nullable String prefix,
      @JsonProperty(PROP_INCLUDED_PACKET) @Nullable String includedPacket,
      @JsonProperty(PROP_INTERFACE) @Nullable String intf,
      @JsonProperty(PROP_ACCEPTED) @Nullable String accepted,
      @JsonProperty(PROP_HEADERS) @Nullable PacketHeaderConstraints headerConstraints,
      @JsonProperty(PROP_IGNORE_COMPOSITES) @Nullable Boolean ignoreComposites) {
    return new FindRouteFilterLinesQuestion(nodes, filters, prefix, includedPacket, intf, accepted);
  }

  public FindRouteFilterLinesQuestion(@Nullable String nodes, @Nullable String filters,
      @Nullable String prefix, @Nullable String includedPacket, @Nullable String intf, @Nullable String accepted) {
    _nodes = nodes;
    _nodeSpecifier =
        SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
    _policies = filters;
    _policySpecifier = SpecifierFactories.getRoutingPolicySpecifierOrDefault(filters,
        ParboiledRoutingPolicySpecifier.parse(".*"));
    _prefixRange = prefix;
    _includedPacket = includedPacket;
    _interface = intf;
    _accepted = accepted;
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

  @JsonProperty(PROP_PREFIX_RANGE)
  @Nullable public String getPrefixRange() {
    return _prefixRange;
  }

  @JsonProperty(PROP_INCLUDED_PACKET)
  @Nullable public String getIncludedPacket() {
    return _includedPacket;
  }

  @JsonProperty(PROP_INTERFACE)
  @Nullable public String getInterface() {
    return _interface;
  }

  @JsonProperty(PROP_ACCEPTED)
  @Nullable public String getAccepted() {
    return _accepted;
  }
}
