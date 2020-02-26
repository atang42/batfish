package org.batfish.minesweeper.question.StaticRouteTimeDiff;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import org.batfish.common.Answerer;
import org.batfish.common.BatfishException;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.policylocalize.StaticRouteDiff;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.SpecifierContext;

public class StaticRouteTimeDiffAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final List<PrefixRange> _ignoredPrefixRanges;

  public StaticRouteTimeDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    StaticRouteTimeDiffQuestion findQuestion = (StaticRouteTimeDiffQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
    _ignoredPrefixRanges = findQuestion.getIgnoredPrefixRanges();
  }

  @Override
  public TableAnswerElement answerDiff() {
    _batfish.pushBaseSnapshot();
    SpecifierContext currentContext = _batfish.specifierContext();
    _batfish.popSnapshot();

    _batfish.pushDeltaSnapshot();
    SpecifierContext referenceContext = _batfish.specifierContext();
    _batfish.popSnapshot();

    Set<String> routersInBoth = new TreeSet<>(_nodeSpecifier.resolve(currentContext));
    routersInBoth.retainAll(_nodeSpecifier.resolve(referenceContext));

    TableAnswerElement answerElement =
        new TableAnswerElement(new TableMetadata(StaticRouteDiff.COLUMN_METADATA));

    for (String routerName : routersInBoth) {
      List<Configuration> configurations =
          Arrays.asList(
              currentContext.getConfigs().get(routerName),
              referenceContext.getConfigs().get(routerName));

      if (configurations.size() < 2) {
        System.err.println("Fewer than 2 specified nodes: ");
        configurations.stream().map(Configuration::getHostname).forEach(System.out::println);
        throw new IllegalArgumentException(
            "Fewer than 2 specified nodes: "
                + configurations
                    .stream()
                    .map(Configuration::getHostname)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(""));
      } else if (configurations.size() > 2) {
        System.err.println("More than 2 specified nodes: ");
        configurations.stream().map(Configuration::getHostname).forEach(System.out::println);
        throw new IllegalArgumentException(
            "More than 2 specified nodes: "
                + configurations
                    .stream()
                    .map(Configuration::getHostname)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(""));
      }
      StaticRouteDiff diff = new StaticRouteDiff(_ignoredPrefixRanges);
      try {
        List<Row> rowList =
            diff.compareStaticRoutes(
                routerName + "-CURRENT",
                routerName + "-REFERENCE",
                configurations.get(0),
                configurations.get(1));
        rowList.forEach(answerElement::addRow);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return answerElement;
  }

  @Override
  public TableAnswerElement answer() {
    throw new BatfishException(
        String.format("%s can only be run in differential mode.", _question.getName()));
  }

  public void printStaticRoutes() {
    SpecifierContext specifierContext = _batfish.specifierContext();
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
