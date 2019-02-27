package org.batfish.question.bddtest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.service.AutoService;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.question.QuestionPlugin;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.symbolic.bdd.BDDTest;

@AutoService(Plugin.class)
public class BDDTestQuestionPlugin extends QuestionPlugin {

  public static final class BDDTestQuestion extends Question {
    private NodesSpecifier _nodeRegex;

    private static final String PROP_NODE_REGEX = "nodeRegex";

    public BDDTestQuestion() {
      _nodeRegex = NodesSpecifier.ALL;
    }

    @Override
    public boolean getDataPlane() {
      return false;
    }

    @Override
    public String getName() {
      return "bddtest";
    }

    @JsonProperty(PROP_NODE_REGEX)
    public NodesSpecifier getNodeRegex() {
      return _nodeRegex;
    }

    @JsonProperty(PROP_NODE_REGEX)
    public NodesSpecifier setNodeRegex(NodesSpecifier regex) {
      return _nodeRegex = regex;
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
      new BDDTest().doTestWithLines(_batfish, regex);
      //new BDDTest().forallTest();
      //new BDDTest().checkRoutingPolicy(_batfish, question.getNodeRegex());
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
