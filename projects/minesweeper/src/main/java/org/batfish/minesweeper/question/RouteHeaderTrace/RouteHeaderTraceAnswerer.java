package org.batfish.minesweeper.question.RouteHeaderTrace;

import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.policylocalize.RouteHeaderTrace;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class RouteHeaderTraceAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final List<PrefixRange> _ranges;

  public RouteHeaderTraceAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    RouteHeaderTraceQuestion traceQuestion = (RouteHeaderTraceQuestion) question;
    _nodeSpecifier = traceQuestion.getNodeSpecifier();
    _ranges = traceQuestion.getPrefixRanges();
  }

  @Override public AnswerElement answer(NetworkSnapshot snapshot) {
    SpecifierContext specifierContext = _batfish.specifierContext(snapshot);
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);

    TableAnswerElement answerElement =
        new TableAnswerElement(new TableMetadata(RouteHeaderTrace.COLUMN_METADATA));
    for (String node : nodeSet) {
      RouteHeaderTrace trace =
          new RouteHeaderTrace(node, _batfish, specifierContext.getConfigs().get(node), _ranges);
      trace.getBGPDiff().forEach(answerElement::addRow);
    }

    return answerElement;
  }
}
