package org.batfish.minesweeper.question.StaticRouteDiff;

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

public class StaticRouteDiffQuestion extends Question {

  private static final String PROP_NODES = "nodes";
  private static final String PROP_IGNORED_PREFIX_RANGES = "ignored";

  @Nonnull private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final String _ignored;
  @Nonnull private final List<PrefixRange> _ignoredPrefixRanges;

  //@Nullable private final Action _action;
  //@Nonnull private final PacketHeaderConstraints _headerConstraints;

  @JsonCreator
  private static StaticRouteDiffQuestion create(
      @JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_IGNORED_PREFIX_RANGES) @Nullable String ignored) {
    return new StaticRouteDiffQuestion(nodes, ignored);
  }

  public StaticRouteDiffQuestion(@Nullable String nodes, @Nullable String ignored) {
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
  }

  @Override public boolean getDataPlane() {
    return false;
  }

  @Override public String getName() {
    return "staticroutediff";
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
