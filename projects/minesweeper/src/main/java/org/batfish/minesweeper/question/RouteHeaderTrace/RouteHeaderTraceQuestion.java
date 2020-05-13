package org.batfish.minesweeper.question.RouteHeaderTrace;

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
import org.batfish.minesweeper.question.RouterDiff.RouterDiffQuestion;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierFactories;

public class RouteHeaderTraceQuestion extends Question {

  private static final String PROP_NODES = "nodes";
  private static final String PROP_PREFIX_RANGE = "prefixRange";


  @Nonnull private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final String _prefixRanges;
  @Nonnull private final List<PrefixRange> _prefixRangeList;


  private static RouterDiffQuestion create(
      @JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_PREFIX_RANGE) @Nullable String ranges) {
    return new RouterDiffQuestion(nodes, ranges);
  }

  @JsonCreator
  public RouteHeaderTraceQuestion(@JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_PREFIX_RANGE) @Nullable String ranges) {
    _nodes = (nodes != null) ? nodes : ".*";
    _nodeSpecifier =
        SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
    if (ranges == null || ranges.isEmpty()) {
      _prefixRanges = "";
      _prefixRangeList = new ArrayList<>();
    } else {
      _prefixRanges = ranges;
      _prefixRangeList =
          Arrays.stream(_prefixRanges.split(","))
              .map(PrefixRange::fromString)
              .collect(Collectors.toList());
    }
  }

  @JsonProperty(PROP_NODES)
  @Nonnull
  public String getNodes() {
    return _nodes;
  }

  @JsonIgnore
  @Nonnull public NodeSpecifier getNodeSpecifier() {
    return _nodeSpecifier;
  }

  @JsonProperty(PROP_PREFIX_RANGE)
  @Nonnull
  public String getRanges() {
    return _prefixRanges;
  }

  @Nonnull
  public List<PrefixRange> getPrefixRanges() {
    return _prefixRangeList;
  }

  @Override public boolean getDataPlane() {
    return false;
  }

  @Override public String getName() {
    return "routeheadertrace";
  }
}
