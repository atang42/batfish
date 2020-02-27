package org.batfish.minesweeper.policylocalize.acldiff.representation;

import java.util.List;
import javax.annotation.Nonnull;
import org.batfish.datamodel.IpAccessListLine;

/*
A Header space attached with _lines and texts of a particular router config
 */
public class LineMappedHeaderSpace {
  @Nonnull private final String _routerName;
  @Nonnull private final AbstractHeaderSpace _space;
  @Nonnull private final List<IpAccessListLine> _lines;

  public LineMappedHeaderSpace(@Nonnull String routerName, @Nonnull AbstractHeaderSpace space,
      @Nonnull List<IpAccessListLine> lineToText) {
    this._routerName = routerName;
    this._space = space;
    this._lines = lineToText;
  }

  @Nonnull public String getRouterName() {
    return _routerName;
  }

  @Nonnull public AbstractHeaderSpace getSpace() {
    return _space;
  }

  @Nonnull public List<IpAccessListLine> getLineToText() {
    return _lines;
  }

  @Nonnull public List<IpAccessListLine> getLines() {
    return _lines;
  }

  /*
  @Nonnull public String getText() {
    StringBuilder builder = new StringBuilder();
    for (Entry<Integer, String> e : _lines) {
      builder.append(String.format("%6d %s\n", e.getKey(), e.getValue()));
    }
    return builder.toString();
  }
  */

}
