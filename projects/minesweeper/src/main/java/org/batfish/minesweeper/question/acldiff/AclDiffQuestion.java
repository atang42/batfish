package org.batfish.minesweeper.question.acldiff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;

public final class AclDiffQuestion extends Question {
  private NodesSpecifier _nodeRegex;
  private String _aclRegex;
  private boolean _printMore;

  private static final String PROP_NODE_REGEX = "nodes";
  private static final String PROP_ACL_REGEX = "filters";

  AclDiffQuestion() {
    this(null, null);
  }

  private AclDiffQuestion(@Nullable NodesSpecifier regex, @Nullable String aclRegex) {
    _nodeRegex = regex != null ? regex : NodesSpecifier.ALL;
    _aclRegex = aclRegex != null ? aclRegex : ".*";
    _printMore = false;
  }

  @JsonCreator
  public AclDiffQuestion create(
      @JsonProperty(PROP_NODE_REGEX) @Nullable NodesSpecifier regex,
      @JsonProperty(PROP_ACL_REGEX) @Nullable String aclRegex) {
    return new AclDiffQuestion(regex, aclRegex);
  }

  @Override
  public boolean getDataPlane() {
    return false;
  }

  @Override
  public String getName() {
    return "aclDiff";
  }

  @JsonProperty(PROP_NODE_REGEX)
  public NodesSpecifier getNodeRegex() {
    return _nodeRegex;
  }

  @JsonProperty(PROP_NODE_REGEX)
  public NodesSpecifier setNodeRegex(NodesSpecifier regex) {
    return _nodeRegex = regex;
  }

  @JsonProperty(PROP_ACL_REGEX)
  public String getAclRegex() {
    return _aclRegex;
  }

  @JsonProperty(PROP_ACL_REGEX)
  public String setAclRegex(String regex) {
    return _aclRegex = regex;
  }

  public boolean getPrintMore() {
    return _printMore;
  }

  public boolean setPrintMore(boolean printMore) {
    return this._printMore = printMore;
  }
}
