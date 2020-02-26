package org.batfish.minesweeper.question.StaticRouteTimeDiff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.questions.Question;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierFactories;

public class StaticRouteTimeDiffQuestion extends Question {

  private static final String PROP_NODES = "nodes";
  private static final String PROP_IGNORED_PREFIX_RANGES = "ignored";

  @Nonnull private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final String _ignored;
  @Nonnull private final List<PrefixRange> _ignoredPrefixRanges;

  //@Nullable private final Action _action;
  //@Nonnull private final PacketHeaderConstraints _headerConstraints;

  @JsonCreator
  private static StaticRouteTimeDiffQuestion create(
      @JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_IGNORED_PREFIX_RANGES) @Nullable String ignored) {
    return new StaticRouteTimeDiffQuestion(nodes, ignored);
  }

  public StaticRouteTimeDiffQuestion(@Nullable String nodes, @Nullable String ignored) {
    _nodes = (nodes != null) ? nodes : ".*";
    _nodeSpecifier =
        SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
    if (ignored == null || ignored.isEmpty()) {
      _ignored = "";
      _ignoredPrefixRanges = new ArrayList<>();
    } else {
      _ignored = ignored;
      _ignoredPrefixRanges =
          Arrays.stream(_ignored.split(","))
              .map(PrefixRange::fromString)
              .collect(Collectors.toList());
    }
    setDifferential(true);
  }

  @Override public boolean getDataPlane() {
    return false;
  }

  @Override public String getName() {
    return "staticRouteTimeDiff";
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

  @JsonProperty(PROP_IGNORED_PREFIX_RANGES)
  @Nonnull
  public String getIgnored() {
    return _ignored;
  }

  @Nonnull
  public List<PrefixRange> getIgnoredPrefixRanges() {
    return _ignoredPrefixRanges;
  }



}
