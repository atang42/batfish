package org.batfish.minesweeper.question.acldiff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.SortedSet;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.minesweeper.policylocalize.acldiff.LineDifference;

public final class AclDiffAnswerElement extends AnswerElement {
  private static final String PROP_DIFFS = "differences";

  private SortedSet<LineDifference> _differences;

  @JsonCreator
  private static AclDiffAnswerElement create(
      @JsonProperty(PROP_DIFFS) SortedSet<LineDifference> diffs) {
    return new AclDiffAnswerElement(diffs);
  }

  AclDiffAnswerElement(SortedSet<LineDifference> diffs) {
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
