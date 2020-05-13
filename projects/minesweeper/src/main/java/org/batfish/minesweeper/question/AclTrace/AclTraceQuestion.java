package org.batfish.minesweeper.question.AclTrace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.questions.Question;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierFactories;

public class AclTraceQuestion extends Question {

  private static final String PROP_NODES = "nodes";
  private static final String PROP_PREFIX = "prefix";


  @Nonnull private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final String _prefix;
  @Nonnull private final Prefix _prefixRepr;


  @JsonCreator
  public AclTraceQuestion(@JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_PREFIX) @Nullable String ranges) {
    _nodes = (nodes != null) ? nodes : ".*";
    _nodeSpecifier =
        SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
    if (ranges == null || ranges.isEmpty()) {
      _prefix = "";
      _prefixRepr = Prefix.ZERO;
    } else {
      _prefix = ranges;
      _prefixRepr = Prefix.parse(ranges);
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

  @JsonProperty(PROP_PREFIX)
  @Nonnull
  public String getRanges() {
    return _prefix;
  }

  @Nonnull
  public Prefix getPrefix() {
    return _prefixRepr;
  }

  @Override public boolean getDataPlane() {
    return false;
  }

  @Override public String getName() {
    return "acltrace";
  }
}
