package org.batfish.representation.juniper;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

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
    builder.append("FROM:").append(System.lineSeparator());
    builder.append("\t")
        .append(getFroms().getText()
            .replace(System.lineSeparator(), System.lineSeparator() + "\t")
            .trim())
        .append(System.lineSeparator());
    builder.append("THEN:").append(System.lineSeparator());
    getThens().forEach(then -> builder.append("\t")
        .append(then.getText())
        .append(System.lineSeparator()));
    return builder.toString();
  }
}
