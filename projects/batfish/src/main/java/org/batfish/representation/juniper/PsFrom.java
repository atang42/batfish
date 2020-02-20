package org.batfish.representation.juniper;

import java.io.Serializable;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;

/** Represents a policy-statement "from" line in a {@link PsTerm} */
public abstract class PsFrom implements Serializable {

  private final String _text;

  private final SortedSet<Integer> _lineNums;

  public PsFrom(String text, Set<Integer> lineNums) {
    _text = text;
    _lineNums = new TreeSet<>(lineNums);
  }

  public String getText() {
    return _text;
  }

  public SortedSet<Integer> getLineNumbers() {
    return _lineNums;
  }

  public abstract BooleanExpr toBooleanExpr(
      JuniperConfiguration jc, Configuration c, Warnings warnings);
}
