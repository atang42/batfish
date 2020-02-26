package org.batfish.minesweeper.question.RouterTimeDiff;

import com.google.auto.service.AutoService;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.datamodel.questions.Question;
import org.batfish.question.QuestionPlugin;

@AutoService(Plugin.class)
public class RouterTimeDiffPlugin extends QuestionPlugin {

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new RouterTimeDiffAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new RouterTimeDiffQuestion(null, null);
  }
}
