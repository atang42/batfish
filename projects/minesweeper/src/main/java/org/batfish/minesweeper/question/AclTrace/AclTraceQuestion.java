package org.batfish.minesweeper.question.AclTrace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.PacketHeaderConstraints;
import org.batfish.datamodel.questions.Question;
import org.batfish.specifier.AllNodesNodeSpecifier;
import org.batfish.specifier.FilterSpecifier;
import org.batfish.specifier.NameRegexFilterSpecifier;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierFactories;

public class AclTraceQuestion extends Question {

  private static final String PROP_NODES = "nodes";
  private static final String PROP_HEADER_CONSTRAINTS = "headers";
  private static final String PROP_FILTERS = "filters";

  @Nonnull private final String _nodes;
  @Nonnull private final NodeSpecifier _nodeSpecifier;
  private final PacketHeaderConstraints _headerConstraints;
  @Nonnull private final String _filters;


  @JsonCreator
  public AclTraceQuestion(@JsonProperty(PROP_NODES) @Nullable String nodes,
      @JsonProperty(PROP_HEADER_CONSTRAINTS) @Nullable PacketHeaderConstraints headers, @JsonProperty(PROP_FILTERS) @Nullable String filters) {
    _nodes = (nodes != null) ? nodes : ".*";
    _nodeSpecifier =
        SpecifierFactories.getNodeSpecifierOrDefault(nodes, AllNodesNodeSpecifier.INSTANCE);
    _filters = (filters != null) ? filters : ".*";
    _headerConstraints = (headers != null) ? headers : PacketHeaderConstraints.unconstrained();

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

  @JsonProperty(PROP_HEADER_CONSTRAINTS)
  @Nonnull
  public PacketHeaderConstraints getHeaderConstraints() {
    return _headerConstraints;
  }

  @JsonProperty(PROP_FILTERS)
  @Nonnull
  public String getFilters() {
    return _filters;
  }

  @JsonIgnore
  @Nonnull public FilterSpecifier getFilterSpecifier() {
    return new NameRegexFilterSpecifier(Pattern.compile(_filters));
  }

  @Override public boolean getDataPlane() {
    return false;
  }

  @Override public String getName() {
    return "acltrace";
  }
}
