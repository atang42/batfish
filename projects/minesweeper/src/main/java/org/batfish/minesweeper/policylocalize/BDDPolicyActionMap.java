package org.batfish.minesweeper.policylocalize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.common.bdd.BDDInteger;
import org.batfish.datamodel.routing_policy.expr.LiteralLong;
import org.batfish.datamodel.routing_policy.expr.LongExpr;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.minesweeper.CommunityVar;
import org.batfish.minesweeper.bdd.BDDRoute;

public class BDDPolicyActionMap {

  private static class ActionStatementsPair {
    PolicyAction action;
    List<Statement> statements;

    public ActionStatementsPair() {
      action = new PolicyAction();
      statements = new ArrayList<>();
    }

    public ActionStatementsPair(PolicyAction action, List<Statement> statements) {
      this.action = new PolicyAction(action);
      this.statements = new ArrayList<>(statements);
    }

    public ActionStatementsPair(ActionStatementsPair pair) {
      this.action = new PolicyAction(pair.action);
      this.statements = new ArrayList<>(pair.statements);
    }
  }

  private static class ActionValueEncoder {
    private static int count = 0;
    private static final int _bitsUsed = 10;
    private static BDDInteger _actionVars;
    static Map<Integer, PolicyAction> _encodedValToAction;
    static Map<PolicyAction, Integer> _actionToEncodedVal;

    static {
      BDDFactory factory = BDDRoute.getFactory();
      _actionVars = BDDInteger.makeFromIndex(factory, _bitsUsed, factory.extVarNum(_bitsUsed), false);
      _actionToEncodedVal = new HashMap<>();
      _encodedValToAction = new HashMap<>();
    }

    public static int put(PolicyAction action) {
      if (_actionToEncodedVal.containsKey(action)) {
        return _actionToEncodedVal.get(action);
      }
      _encodedValToAction.put(count, action);
      _actionToEncodedVal.put(action, count);
      count++;
      return count - 1;
    }

    static PolicyAction getAction(int value) {
      return _encodedValToAction.get(value);
    }

    static int getEncodedValue(PolicyAction action) {
      return _actionToEncodedVal.get(action);
    }

    static BDD getEncodedBDD(PolicyAction action) {
      return _actionVars.value(getEncodedValue(action));
    }

    public static Optional<PolicyAction> getActionFromBDD(BDD bdd) {
      return _actionVars.getValueSatisfying(bdd)
          .map(Long::intValue)
          .map(_encodedValToAction::get);
    }
  }

  private Map<BDD, ActionStatementsPair> _bddToAction;

  public BDDPolicyActionMap() {
    _bddToAction = new HashMap<>();
    _bddToAction.put(BDDRoute.getFactory().one(), new ActionStatementsPair());
  }

  /*
  Combine BDDs that have same action
   */
  public void consolidateMap() {
    Map<ActionStatementsPair, BDD> actionBDDMap = new HashMap<>();

    // Ensure that all inputs are covered. Assume default is unknown
    BDD coveredInputs = _bddToAction.keySet().stream().reduce(BDD::or).get();
    if (!coveredInputs.isOne()) {
      _bddToAction.put(coveredInputs.not(), new ActionStatementsPair());
    }

    for (Entry<BDD, ActionStatementsPair> ent : _bddToAction.entrySet()) {
      BDD bdd = actionBDDMap.getOrDefault(ent.getValue(), BDDRoute.getFactory().zero());
      actionBDDMap.put(ent.getValue(), bdd.or(ent.getKey()));
    }
    _bddToAction.clear();
    for (Entry<ActionStatementsPair, BDD> ent : actionBDDMap.entrySet()) {
      if (!ent.getValue().isZero()) {
        _bddToAction.put(ent.getValue(), ent.getKey());
      }
    }
  }

  @Nonnull private Stream<ActionStatementsPair> getPolicyActionsMatchingBDD(BDD bdd) {
    if (_bddToAction.containsKey(bdd)) {
      return Stream.of(_bddToAction.get(bdd));
    }

    ArrayList<ActionStatementsPair> actionList = new ArrayList<>();
    Set<BDD> keys = new HashSet<>(_bddToAction.keySet());
    for (BDD key : keys) {
      BDD conjunct = bdd.and(key);

      if (conjunct.isZero()) {
        continue;
      } else if (conjunct.equals(key)) {
        actionList.add(_bddToAction.get(conjunct));
        bdd = bdd.and(conjunct.not());
      } else {
        // partition entry in table
        ActionStatementsPair existingAction = _bddToAction.get(key);
        _bddToAction.remove(key);
        _bddToAction.put(conjunct, new ActionStatementsPair(existingAction));

        BDD onlyInKey = key.and(conjunct.not());
        if (!onlyInKey.isZero()) {
          _bddToAction.put(onlyInKey, new ActionStatementsPair(existingAction));
        }
        actionList.add(_bddToAction.get(conjunct));
        bdd = bdd.and(conjunct.not());
      }
    }
    assert bdd.isZero();
    return actionList.stream();
  }

  public void setResult(BDD bdd, SymbolicResult result) {
    getPolicyActionsMatchingBDD(bdd).
        forEach(pair -> pair.action.setResult(result));
  }

  public void setMetric(BDD bdd, LongExpr metric) {
    if (metric instanceof LiteralLong) {
      getPolicyActionsMatchingBDD(bdd)
          .forEach(pair -> pair.action.setMetric(((LiteralLong) metric).getValue()));
    } else {
      throw new UnsupportedOperationException("Unsupported set metric expression");
    }
  }

  public void setLocalPref(BDD bdd, LongExpr localPref) {
    if (localPref instanceof LiteralLong) {
      getPolicyActionsMatchingBDD(bdd)
          .forEach(pair -> pair.action.setLocalPref(((LiteralLong) localPref).getValue()));
    } else {
      throw new UnsupportedOperationException("Unsupported set local pref expression");
    }
  }

  public void addCommunities(BDD bdd, Set<CommunityVar> comms) {
    getPolicyActionsMatchingBDD(bdd)
        .forEach(pair -> pair.action.setAddedCommunities(comms));
  }

  public void addStatement(BDD bdd, Statement statement) {
    getPolicyActionsMatchingBDD(bdd).forEach(pair -> pair.statements.add(statement));
  }

  @Nonnull public Set<BDD> getBDDKeys() {
    return _bddToAction.keySet();
  }

  @Nonnull public Map<BDD, List<Statement>> getStatementMap() {
    return _bddToAction.entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                Entry::getKey,
                ent -> ent.getValue().statements));
  }

  @Nonnull public static String getStatementTexts(@Nonnull List<Statement> statements) {
    StringBuilder stmtText = new StringBuilder();
    statements
        .stream()
        .filter(stmt -> stmt.getText() != null)
        .forEach(stmt -> stmtText.append(stmt.getText()).append(System.lineSeparator()));
    return stmtText.toString().trim();
  }

  @Nonnull public String getCombinedStatementTexts(@Nonnull BDD bdd) {
    return getStatementTexts(getStatements(bdd));
  }

  @Nonnull public List<Statement> getStatements(BDD bdd) {
    if (_bddToAction.containsKey(bdd)) {
      return _bddToAction.get(bdd).statements;
    }

    for (BDD equivClass : getBDDKeys()) {
      if (bdd.and(equivClass).equals(bdd)) {
        return _bddToAction.get(equivClass).statements;
      }
    }
    return new ArrayList<>();
    //throw new IllegalArgumentException("Input BDD does not match unique set of statements");
  }

  @Nonnull public PolicyAction getAction(BDD bdd) {
    if (_bddToAction.containsKey(bdd)) {
      return _bddToAction.get(bdd).action;
    }

    for (BDD equivClass : getBDDKeys()) {
      if (bdd.and(equivClass).equals(bdd)) {
        return _bddToAction.get(equivClass).action;
      }
    }
    return new PolicyAction();
    //throw new IllegalArgumentException("Input BDD does not match unique set of statements");
  }

  private void generateEncodedActionValues() {
    consolidateMap();
    _bddToAction.values()
        .stream()
        .map(pair -> pair.action)
        .forEach(ActionValueEncoder::put);
  }

  /*
  Creates a single BDD with each possible action encoded as variables in the BDD
   */
  @Nonnull public BDD mapToEncodedBDD(BDDRoute record) {
    BDDFactory factory = BDDRoute.getFactory();
    BDD acc = factory.zero();
    generateEncodedActionValues();

    for (Entry<BDD, ActionStatementsPair> e : _bddToAction.entrySet()) {
      BDD bdd = e.getKey();
      PolicyAction actionValue = e.getValue().action;
      acc.orWith(bdd.and(ActionValueEncoder.getEncodedBDD(actionValue)));
    }
    return acc;
  }

  @Nonnull public PolicyAction getAction(int value) {
    return ActionValueEncoder.getAction(value);
  }

  /*
  Returns action of a satisfied bdd if one exists
   */
  @Nonnull public Optional<PolicyAction> getActionFromBDDWithActionVars(BDD bdd) {
    return ActionValueEncoder.getActionFromBDD(bdd);
  }

  @Nonnull public static BDD getActionVariables() {
    return Arrays.stream(ActionValueEncoder._actionVars.getBitvec()).reduce(BDD::and).get();
  }

}
