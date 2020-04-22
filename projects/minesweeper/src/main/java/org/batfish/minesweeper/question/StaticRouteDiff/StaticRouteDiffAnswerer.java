package org.batfish.minesweeper.question.StaticRouteDiff;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.policylocalize.StaticRouteDiff;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class StaticRouteDiffAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final List<PrefixRange> _ignoredPrefixRanges;

  public StaticRouteDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    StaticRouteDiffQuestion findQuestion = (StaticRouteDiffQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
    _ignoredPrefixRanges = findQuestion.getIgnoredPrefixRanges();
  }

  @Override
  public TableAnswerElement answer(NetworkSnapshot snapshot) {

    SpecifierContext specifierContext = _batfish.specifierContext(snapshot);
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

    TableAnswerElement answerElement =
        new TableAnswerElement(new TableMetadata(StaticRouteDiff.COLUMN_METADATA));

    StaticRouteDiff diff = new StaticRouteDiff(_ignoredPrefixRanges);

    diff.compareStaticRoutes(
            configurations.get(0).getHostname(),
            configurations.get(1).getHostname(),
            configurations.get(0),
            configurations.get(1))
        .forEach(answerElement::addRow);
    return answerElement;
  }

  public void printStaticRoutes(NetworkSnapshot snapshot) {
    SpecifierContext specifierContext = _batfish.specifierContext(snapshot);
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);
    for (String node : nodeSet) {
      System.out.println(node);
      Configuration config = specifierContext.getConfigs().get(node);
      config
          .getDefaultVrf()
          .getStaticRoutes()
          .forEach(
              route -> {
                System.out.println(
                    "\t"
                        + route.getNetwork()
                        + " "
                        + route.getNonForwarding()
                        + " "
                        + route.getNonRouting()
                        + " "
                        + route.getAdministrativeCost()
                        + " "
                        + route.getMetric()
                        + " "
                        + route.getTag()
                        + " "
                        + route.getNextHopInterface()
                        + " "
                        + route.getNextHopIp()
                        + " "
                        + route.getNextVrf()
                        + " "
                        + route.getProtocol()
                        + " '"
                        + route.getText()
                        + "'");
              });
    }
  }
}
