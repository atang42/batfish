package org.batfish.minesweeper.question;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.service.AutoService;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.minesweeper.bdd.BddDiff;
import org.batfish.question.QuestionPlugin;


@AutoService(Plugin.class)
public class BDDDiffQuestionPlugin extends QuestionPlugin {

  public static final class BDDTestQuestion extends Question {
    private NodesSpecifier _nodeRegex;
    private String _aclRegex;
    private boolean _printMore;

    private static final String PROP_NODE_REGEX = "nodeRegex";
    private static final String PROP_ACL_REGEX = "aclRegex";
    private static final String PROP_PRINT_MORE = "printMore";

    public BDDTestQuestion() {
      _nodeRegex = NodesSpecifier.ALL;
      _aclRegex = ".*";
    }

    @Override
    public boolean getDataPlane() {
      return false;
    }

    @Override
    public String getName() {
      return "bdd-diff";
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
    public boolean SetPrintMore(boolean _printMore) {
      return this._printMore = _printMore;
    }
  }

  public static final class BDDAnswerElement extends AnswerElement {

  }

  public static final class BDDTestAnswerer extends Answerer {

    public BDDTestAnswerer(Question question, IBatfish batfish) {
      super (question, batfish);
    }

    @Override public AnswerElement answer() {
      BDDTestQuestion question = (BDDTestQuestion) _question;
      NodesSpecifier regex = question.getNodeRegex();
      String aclRegex = question.getAclRegex();
      boolean printMore = question.getPrintMore();
      new BddDiff().findDiffWithLines(_batfish, regex, aclRegex, printMore);
      //new BddDiff().forallTest();
      //new BddDiff().checkRoutingPolicy(_batfish, question.getNodeRegex());
      return new BDDAnswerElement();
    }
  }

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new BDDTestAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new BDDTestQuestion();
  }
}
