package org.batfish.minesweeper.question.RouterTimeDiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.BatfishException;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.Graph;
import org.batfish.minesweeper.policylocalize.RoutePolicyDiff;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class RouterTimeDiffAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final List<PrefixRange> _ignoredPrefixRanges;

  public RouterTimeDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    RouterTimeDiffQuestion findQuestion = (RouterTimeDiffQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
    _ignoredPrefixRanges = findQuestion.getIgnoredPrefixRanges();
  }

  @Override
  public AnswerElement answer(NetworkSnapshot snapshot) {
    throw new BatfishException(
        String.format("%s can only be run in differential mode.", _question.getName()));
  }

  @Override
  public TableAnswerElement answerDiff(NetworkSnapshot current, NetworkSnapshot reference) {
    SpecifierContext currentContext = _batfish.specifierContext(current);

    SpecifierContext referenceContext = _batfish.specifierContext(reference);

    Set<String> routersInBoth = new TreeSet<>(_nodeSpecifier.resolve(currentContext));
    routersInBoth.retainAll(_nodeSpecifier.resolve(referenceContext));

    TableAnswerElement answerElement =
        new TableAnswerElement(new TableMetadata(RoutePolicyDiff.COLUMN_METADATA));

    List<Row> bgpDiffs = new ArrayList<>();
    List<Row> ospfDiffs = new ArrayList<>();

    Graph graph1 = new Graph(_batfish, current);
    Graph graph2 = new Graph(_batfish, reference);
    for (String routerName : routersInBoth) {
      List<Configuration> configurations =
          Arrays.asList(
              currentContext.getConfigs().get(routerName),
              referenceContext.getConfigs().get(routerName));

      RoutePolicyDiff diff =
          new RoutePolicyDiff(
              routerName + "-CURRENT",
              routerName + "-REFERENCE",
              _batfish,
              configurations,
              _ignoredPrefixRanges, graph1, graph2);
      try {
        bgpDiffs.addAll(diff.getBGPDiff());
        ospfDiffs.addAll(diff.getOspfDiff());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    bgpDiffs.forEach(answerElement::addRow);
    ospfDiffs.forEach(answerElement::addRow);

    return answerElement;
  }
}
