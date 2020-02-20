package org.batfish.representation.juniper;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public final class PsTerm implements Serializable {

  private final PsFroms _froms;
  private final String _name;
  private final Set<PsThen> _thens;

  public PsTerm(String name) {
    _froms = new PsFroms();
    _name = name;
    _thens = new LinkedHashSet<>();
  }

  public PsFroms getFroms() {
    return _froms;
  }

  public String getName() {
    return _name;
  }

  public Set<PsThen> getThens() {
    return _thens;
  }

  public boolean hasAtLeastOneFrom() {
    return _froms.hasAtLeastOneFrom();
  }

  public String getText() {
    StringBuilder builder = new StringBuilder();
    builder.append(getName()).append(System.lineSeparator());
    builder.append(indent(_froms.getText()))
        .append(System.lineSeparator());

    StringBuilder thenBuilder = new StringBuilder();
    if (_thens.size() == 1) {
      thenBuilder.append("then ");
      getThens().forEach(then -> thenBuilder.append(then.getText()).append("\n"));
    } else if (_thens.size() > 1) {
      thenBuilder.append("then {\n");
      getThens().forEach(then -> thenBuilder.append("\t")
        .append(then.getText())
        .append("\n"));
      thenBuilder.append("}");
    }
    builder.append(indent(thenBuilder.toString()));
    return builder.toString();
  }

  public SortedSet<Integer> getLineNumbers() {
    SortedSet<Integer> result = new TreeSet<>(_froms.getLineNumbers());
    _thens.forEach(then -> result.addAll(then.getLineNumbers()));
    return result;
  }

  private String indent(String s) {
    return "\t" + s.replaceAll("\n", "\n\t");
  }
}
