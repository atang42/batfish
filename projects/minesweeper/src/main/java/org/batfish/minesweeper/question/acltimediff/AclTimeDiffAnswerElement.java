package org.batfish.minesweeper.question.acltimediff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.SortedSet;
import org.batfish.minesweeper.policylocalize.acldiff.LineDifference;
import org.batfish.datamodel.answers.AnswerElement;

public final class AclTimeDiffAnswerElement extends AnswerElement {
  private static final String PROP_DIFFS = "differences";

  private SortedSet<LineDifference> _differences;

  @JsonCreator
  private static AclTimeDiffAnswerElement create(@JsonProperty(PROP_DIFFS) SortedSet<LineDifference> diffs) {
    return new AclTimeDiffAnswerElement(diffs);
  }

  public AclTimeDiffAnswerElement(SortedSet<LineDifference> diffs) {
    _differences = diffs;
  }

  @JsonProperty(PROP_DIFFS)
  public SortedSet<LineDifference> getDifferences() {
    return _differences;
  }

  @JsonProperty(PROP_DIFFS)
  public void setDifferences(SortedSet<LineDifference> differences) {
    this._differences = differences;
  }
}
