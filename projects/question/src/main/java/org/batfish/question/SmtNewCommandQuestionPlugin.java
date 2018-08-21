package org.batfish.question;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.service.AutoService;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.batfish.common.Answerer;
import org.batfish.common.BatfishException;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.questions.smt.HeaderQuestion;

@AutoService(Plugin.class)
public class SmtNewCommandQuestionPlugin extends QuestionPlugin {

  public static class DifferenceAnswerer extends Answerer {

    public DifferenceAnswerer(Question question, IBatfish batfish) {
      super(question, batfish);
    }

    @Override
    public AnswerElement answer() {
      DifferenceQuestion q = (DifferenceQuestion) _question;

      Pattern routerRegex;
      Prefix prefix = Prefix.parse(q.getPrefix());

      try {
        routerRegex = Pattern.compile(q.getRouterRegex());
      } catch (PatternSyntaxException e) {
        throw new BatfishException(
            String.format(
                "One of the supplied regexes %s is not a valid java regex.", q.getRouterRegex()),
            e);
      }

      return _batfish.smtDifference(q, routerRegex, prefix, q.getMaxLength());
    }
  }

  public static class DifferenceQuestion extends HeaderQuestion {

    private static final String NODE_REGEX_VAR = "nodeRegex";
    private static final String NODE_PREFIX_VAR = "prefix";
    private static final String NODE_MAX_LENGTH_VAR = "maxLength";

    private String _routerRegex;
    private String _prefix;
    private int _maxLength;

    public DifferenceQuestion() {
      _routerRegex = ".*";
      _prefix = "0.0.0.0/0";
      _maxLength = 24;
    }

    @JsonProperty(NODE_REGEX_VAR)
    public String getRouterRegex() {
      return _routerRegex;
    }

    @JsonProperty(NODE_REGEX_VAR)
    public void setRouterRegex(String routerRegex) {
      this._routerRegex = routerRegex;
    }

    @JsonProperty(NODE_PREFIX_VAR)
    public String getPrefix() {
      return _prefix;
    }

    @JsonProperty(NODE_PREFIX_VAR)
    public void setPrefix(String prefix) {
      this._prefix = prefix;
    }

    @JsonProperty(NODE_MAX_LENGTH_VAR)
    public int getMaxLength() {
      return _maxLength;
    }

    @JsonProperty(NODE_MAX_LENGTH_VAR)
    public void setMaxLength(int maxLength) {
      _maxLength = maxLength;
    }

    @Override
    public boolean getDataPlane() {
      return false;
    }

    @Override
    public String getName() {
      return "smt-diff";
    }
  }

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new DifferenceAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new DifferenceQuestion();
  }
}
