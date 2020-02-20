package org.batfish.datamodel.routing_policy.statement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import javax.annotation.Nullable;
import org.batfish.common.Warnings;
import org.batfish.datamodel.routing_policy.Environment;
import org.batfish.datamodel.routing_policy.Result;
import org.batfish.datamodel.routing_policy.RoutingPolicy;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AddCommunity.class),
  @JsonSubTypes.Type(value = BufferedStatement.class),
  @JsonSubTypes.Type(value = CallStatement.class),
  @JsonSubTypes.Type(value = Comment.class),
  @JsonSubTypes.Type(value = DeleteCommunity.class),
  @JsonSubTypes.Type(value = If.class),
  @JsonSubTypes.Type(value = PrependAsPath.class),
  @JsonSubTypes.Type(value = RetainCommunity.class),
  @JsonSubTypes.Type(value = SetCommunity.class),
  @JsonSubTypes.Type(value = SetIsisLevel.class),
  @JsonSubTypes.Type(value = SetIsisMetricType.class),
  @JsonSubTypes.Type(value = SetLocalPreference.class),
  @JsonSubTypes.Type(value = SetMetric.class),
  @JsonSubTypes.Type(value = SetNextHop.class),
  @JsonSubTypes.Type(value = SetOrigin.class),
  @JsonSubTypes.Type(value = SetOspfMetricType.class),
  @JsonSubTypes.Type(value = SetTag.class),
  @JsonSubTypes.Type(value = SetVarMetricType.class),
  @JsonSubTypes.Type(value = SetWeight.class)
})
public abstract class Statement implements Serializable {
  private static final String PROP_COMMENT = "comment";
  private static final String PROP_TEXT = "text";
  private static final String PROP_LINE_NUMBERS = "lineNumbers";

  @Nullable private String _comment;
  @Nullable private String _text;
  @Nullable private List<Integer> _lineNums;

  protected transient List<Statement> _simplified;

  public abstract <T, U> T accept(StatementVisitor<T, U> visitor, U arg);

  /**
   * Get all the routing-policies referenced by this statement.
   *
   * @return A {@link SortedSet} containing the names of each {@link RoutingPolicy} directly or
   *     indirectly referenced by this statement
   */
  public Set<String> collectSources(
      Set<String> parentSources, Map<String, RoutingPolicy> routingPolicies, Warnings w) {
    return Collections.emptySet();
  }

  @Override
  public abstract boolean equals(Object obj);

  public abstract Result execute(Environment environment);

  @Nullable
  @JsonProperty(PROP_COMMENT)
  public final String getComment() {
    return _comment;
  }

  @Override
  public abstract int hashCode();

  @JsonProperty(PROP_COMMENT)
  public final void setComment(@Nullable String comment) {
    _comment = comment;
  }

  @Nullable
  @JsonProperty(PROP_TEXT)
  public final String getText() {
    return _text;
  }

  @JsonProperty(PROP_TEXT)
  public final void setText(@Nullable String text) {
    _text = text;
  }

  public List<Statement> simplify() {
    if (_simplified == null) {
      _simplified = ImmutableList.of(this);
    }
    return _simplified;
  }

  @Nullable
  @JsonProperty(PROP_LINE_NUMBERS)
  public final List<Integer> getLineNumbers() {
    return _lineNums;
  }

  @JsonProperty(PROP_LINE_NUMBERS)
  public final void setLineNumbers(@Nullable Collection<Integer> lineNumbers) {
    if (lineNumbers != null) {
      _lineNums = new ArrayList<>(lineNumbers);
    }
  }

  @Override
  public String toString() {
    if (_comment != null) {
      return getClass().getSimpleName() + "<" + _comment + ">";
    } else {
      return super.toString();
    }
  }
}
