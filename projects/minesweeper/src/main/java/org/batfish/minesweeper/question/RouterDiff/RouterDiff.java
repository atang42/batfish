package org.batfish.minesweeper.question.RouterDiff;

import com.google.auto.service.AutoService;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.datamodel.questions.Question;
import org.batfish.question.QuestionPlugin;

@AutoService(Plugin.class)
public class RouterDiff extends QuestionPlugin {

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new RouterDiffAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new RouterDiffQuestion(null, null);
  }
}
