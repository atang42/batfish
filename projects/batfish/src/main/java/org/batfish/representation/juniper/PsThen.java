package org.batfish.representation.juniper;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.routing_policy.statement.Statement;

public abstract class PsThen implements Serializable {

  private final String _text;

  private final SortedSet<Integer> _lineNums;

  public PsThen(String text, Set<Integer> lineNums) {
    _text = text;
    _lineNums = new TreeSet<>(lineNums);
  }

  public String getText() {
    return _text;
  }

  public SortedSet<Integer> getLineNumbers() {
    return _lineNums;
  }

  public abstract void applyTo(
      List<Statement> statements,
      JuniperConfiguration juniperVendorConfiguration,
      Configuration c,
      Warnings warnings);
}
