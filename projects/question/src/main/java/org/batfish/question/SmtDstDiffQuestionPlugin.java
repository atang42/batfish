package org.batfish.question;

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
import org.batfish.datamodel.questions.smt.DifferenceQuestion;

@AutoService(Plugin.class)
public class SmtDstDiffQuestionPlugin extends QuestionPlugin {

  public static class DifferenceAnswerer extends Answerer {

    public DifferenceAnswerer(Question question, IBatfish batfish) {
      super(question, batfish);
    }

    @Override
    public AnswerElement answer() {
      DstDifferenceQuestion q = (DstDifferenceQuestion) _question;

      Pattern routerRegex;

      try {
        routerRegex = Pattern.compile(q.getRouterRegex());
      } catch (PatternSyntaxException e) {
        throw new BatfishException(
            String.format(
                "One of the supplied regexes %s is not a valid java regex.", q.getRouterRegex()),
            e);
      }

      return _batfish.smtDstDifference(q, routerRegex);
    }
  }

  public static class DstDifferenceQuestion extends DifferenceQuestion {

    @Override
    public String getName() {
      return "smt-dst-diff";
    }
  }

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new DifferenceAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new DstDifferenceQuestion();
  }
}
