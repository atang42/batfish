package org.batfish.minesweeper.question.RouterDiff;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.policylocalize.RoutePolicyDiff;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class RouterDiffAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final List<PrefixRange> _ignoredPrefixRanges;

  public RouterDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    RouterDiffQuestion findQuestion = (RouterDiffQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
    _ignoredPrefixRanges = findQuestion.getIgnoredPrefixRanges();
  }

  @Override
  public TableAnswerElement answer() {

    SpecifierContext specifierContext = _batfish.specifierContext();
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);

    if (nodeSet.size() < 2) {
      System.err.println("Fewer than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      throw new IllegalArgumentException(
          "Fewer than 2 specified nodes: "
              + nodeSet.stream().reduce((a, b) -> a + "\n" + b).orElse(""));
    } else if (nodeSet.size() > 2) {
      System.err.println("More than 2 specified nodes: ");
      nodeSet.forEach(System.out::println);
      throw new IllegalArgumentException(
          "More than 2 specified nodes: "
              + nodeSet.stream().reduce((a, b) -> a + "\n" + b).orElse(""));
    }

    List<Configuration> configurations =
        nodeSet.stream().map(specifierContext.getConfigs()::get).collect(Collectors.toList());

    RoutePolicyDiff diff =
        new RoutePolicyDiff(
            configurations.get(0).getHostname(),
            configurations.get(1).getHostname(),
            _batfish,
            configurations,
            _ignoredPrefixRanges);

    TableAnswerElement answerElement =
        new TableAnswerElement(new TableMetadata(RoutePolicyDiff.COLUMN_METADATA));
    diff.getBGPDiff().forEach(answerElement::addRow);
    diff.getOspfDiff().forEach(answerElement::addRow);

    return answerElement;
  }
}
