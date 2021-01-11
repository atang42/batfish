package org.batfish.minesweeper.policylocalize.acldiff.headerpresent;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.minesweeper.bdd.BDDRoute;

public class HeaderRemainderNode {
  @Nonnull private final HeaderNode _parent;
  @Nonnull private final List<HeaderNode> _excluded;
  private BDD _bdd;

  public HeaderRemainderNode(@Nonnull HeaderNode parent, @Nonnull List<HeaderNode> excluded, BDDPacket record) {
    _parent = parent;
    _excluded = Collections.unmodifiableList(excluded);
  }

  public HeaderNode getParent() {
    return _parent;
  }

  @Nonnull
  public List<HeaderNode> getParents() {
    return Collections.singletonList(_parent);
  }

  private void computeBDD() {
    if (_bdd == null) {
      _bdd = _parent.getBDD();
      for (HeaderNode toExclude : _excluded) {
        BDD excluded = toExclude.getBDD();
        _bdd.andWith(excluded.not());
      }
    }
  }

  @Nonnull
  public BDD getBDD() {
    computeBDD();
    return _bdd;
  }

  @Nonnull
  public boolean intersectsBDD(BDD bdd) {
    return getBDD().andSat(bdd);
  }

  public @Nonnull List<HeaderNode> getExcluded() {
    return _excluded;
  }
}
