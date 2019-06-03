package org.batfish.question.bdddiff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.service.AutoService;
import java.util.SortedSet;
import javax.annotation.Nullable;
import org.batfish.bdddiff.BddDiff;
import org.batfish.bdddiff.LineDifference;
import org.batfish.common.Answerer;
import org.batfish.common.BatfishException;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.question.QuestionPlugin;

@AutoService(Plugin.class)
public class BDDTimeDiffQuestionPlugin extends QuestionPlugin {
  
  public static final class BDDTimeDiffQuestion extends Question {
    private NodesSpecifier _nodeRegex;
    private String _aclRegex;
    private boolean _printMore;

    private static final String PROP_NODE_REGEX = "nodeRegex";
    private static final String PROP_ACL_REGEX = "aclRegex";
    private static final String PROP_PRINT_MORE = "printMore";

    public BDDTimeDiffQuestion() {
      this(null, null, true);
    }

    public BDDTimeDiffQuestion(@Nullable NodesSpecifier regex,
        @Nullable String aclRegex,
        boolean printMore) {
      _nodeRegex = regex != null ? regex : NodesSpecifier.ALL;
      _aclRegex = aclRegex != null ? aclRegex : ".*";
      _printMore = printMore;
      this.setDifferential(true);
    }

    @JsonCreator
    public BDDTimeDiffQuestionPlugin.BDDTimeDiffQuestion create(
        @JsonProperty(PROP_NODE_REGEX) @Nullable NodesSpecifier regex,
        @JsonProperty(PROP_ACL_REGEX) @Nullable String aclRegex,
        @JsonProperty(PROP_PRINT_MORE) boolean printMore
    ) {
      BDDTimeDiffQuestion question = new BDDTimeDiffQuestion(regex, aclRegex, printMore);
      return question;
    }

    @Override
    public boolean getDataPlane() {
      return false;
    }

    @Override
    public String getName() {
      return "BDDTimeDiff";
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

    @JsonProperty(PROP_PRINT_MORE)
    public boolean getPrintMore() {
      return _printMore;
    }

    @JsonProperty(PROP_PRINT_MORE)
    public boolean setPrintMore(boolean printMore) {
      return this._printMore = printMore;
    }
  }

  public static final class BDDTimeDiffAnswerElement extends AnswerElement {
    private static final String PROP_DIFFS = "differences";

    SortedSet<LineDifference> _differences;

    @JsonCreator
    private static BDDTimeDiffAnswerElement create(@JsonProperty(PROP_DIFFS) SortedSet<LineDifference> diffs) {
      return new BDDTimeDiffAnswerElement(diffs);
    }

    public BDDTimeDiffAnswerElement(SortedSet<LineDifference> diffs) {
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

  public static final class BDDTimeDiffAnswerer extends Answerer {

    public BDDTimeDiffAnswerer(Question question, IBatfish batfish) {
      super(question, batfish);
    }

    @Override
    public AnswerElement answer() {
      throw new BatfishException(
          String.format("%s can only be run in differential mode.", _question.getName()));
    }

    @Override public AnswerElement answerDiff() {
      BDDTimeDiffQuestion question = (BDDTimeDiffQuestion) _question;
      NodesSpecifier regex = question.getNodeRegex();
      String aclRegex = question.getAclRegex();
      boolean printMore = question.getPrintMore();
      BddDiff diffChecker = new BddDiff();
      SortedSet<LineDifference> diffs =
          diffChecker.getTimeDiff(_batfish, regex, aclRegex, printMore);
      return new BDDTimeDiffAnswerElement(diffs);
    }
  }

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new BDDTimeDiffAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new BDDTimeDiffQuestion();
  }
}
