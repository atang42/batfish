package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nonnull;

/*
A Header space attached with lines and texts of a particular router config
 */
public class LineMappedHeaderSpace {
  @Nonnull private final String _routerName;
  @Nonnull private final AbstractHeaderSpace _space;
  @Nonnull private final TreeMap<Integer, String> _lineToText;

  public LineMappedHeaderSpace(@Nonnull String routerName, @Nonnull ConjunctHeaderSpace space,
      @Nonnull TreeMap<Integer, String> lineToText) {
    this._routerName = routerName;
    this._space = space;
    this._lineToText = lineToText;
  }

  @Nonnull public String getRouterName() {
    return _routerName;
  }

  @Nonnull public AbstractHeaderSpace getSpace() {
    return _space;
  }

  @Nonnull public TreeMap<Integer, String> getLineToText() {
    return _lineToText;
  }

  public Set<Integer> getLines() {
    return _lineToText.keySet();
  }

  public String getText() {
    StringBuilder builder = new StringBuilder();
    for (Entry<Integer, String> e : _lineToText.entrySet()) {
      builder.append(String.format("%6d %s\n", e.getKey(), e.getValue()));
    }
    return builder.toString();
  }

}
