package org.batfish.minesweeper.question.findroutefilterlines;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.answers.StringAnswerElement;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.minesweeper.Graph;
import org.batfish.minesweeper.TransferResult;
import org.batfish.minesweeper.bdd.PolicyQuotient;
import org.batfish.minesweeper.bdd.TransferBDD;
import org.batfish.minesweeper.bdd.TransferReturn;
import org.batfish.specifier.NodeSpecifier;
import org.batfish.specifier.RoutingPolicySpecifier;
import org.batfish.specifier.SpecifierContext;

public class FindRouteFilterLinesAnswerer extends Answerer {

  @Nonnull private final NodeSpecifier _nodeSpecifier;
  @Nonnull private final RoutingPolicySpecifier _policySpecifier;
  @Nonnull private final Prefix _prefix;

  public FindRouteFilterLinesAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
    FindRouteFilterLinesQuestion findQuestion = (FindRouteFilterLinesQuestion) question;
    _nodeSpecifier = findQuestion.getNodeSpecifier();
    _policySpecifier = findQuestion.getPolicySpecifier();
    _prefix = Prefix.parse(findQuestion.getPrefix());
  }

  @Override public AnswerElement answer() {
    SpecifierContext specifierContext = _batfish.specifierContext();
    Graph graph = new Graph(_batfish);
    PolicyQuotient policyQuotient = new PolicyQuotient(graph);
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);
    for (String node : nodeSet) {
      Configuration config = specifierContext.getConfigs().get(node);
      Set<RoutingPolicy> policySet = _policySpecifier.resolve(node, specifierContext);
      for (RoutingPolicy policy : policySet) {
        TransferBDD transferBDD =
            new TransferBDD(graph, config, policy.getStatements(), policyQuotient);
        TransferResult<TransferReturn, BDD> transferResult = transferBDD.compute(new TreeSet<>());

        List<Statement> stmts = transferBDD.getStatementsActingOnPrefix(_prefix, transferResult.getReturnValue().getFirst());
        System.out.println(policy.getName());
        System.out.println("Relevant Statements");
        stmts.stream().filter(stmt -> stmt.getText() != null)
            .forEach(stmt ->
                System.out.println(stmt.getText()));
      }
    }
    return new StringAnswerElement("DONE");
  }

  private Set<RoutingPolicy> getSpecifiedRoutingPolicies() {
    Set<RoutingPolicy> result = new HashSet<>();
    SpecifierContext specifierContext = _batfish.specifierContext();
    Set<String> nodeSet = _nodeSpecifier.resolve(specifierContext);
    for (String node : nodeSet) {
      Set<RoutingPolicy> policySet = _policySpecifier.resolve(node, specifierContext);
      result.addAll(policySet);
    }
    return result;
  }

}
