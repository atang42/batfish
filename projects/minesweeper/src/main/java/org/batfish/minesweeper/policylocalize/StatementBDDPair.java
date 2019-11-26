package org.batfish.minesweeper.policylocalize;

import net.sf.javabdd.BDD;
import org.batfish.datamodel.routing_policy.statement.Statement;

public class StatementBDDPair {
  private Statement _statement;
  private BDD _bdd;

  public StatementBDDPair(Statement statement, BDD bdd) {
    this._statement = statement;
    this._bdd = bdd;
  }

  public Statement getStatement() {
    return _statement;
  }

  public void setStatement(Statement statement) {
    this._statement = statement;
  }

  public BDD getBdd() {
    return _bdd;
  }

  public void set_bdd(BDD bdd) {
    this._bdd = bdd;
  }
}
