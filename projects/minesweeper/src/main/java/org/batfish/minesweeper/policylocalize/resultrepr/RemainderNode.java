package org.batfish.minesweeper.policylocalize.resultrepr;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.PrefixRange;
import org.batfish.minesweeper.bdd.BDDRoute;
import org.batfish.minesweeper.bdd.TransferBDD;

public class RemainderNode extends AbstractPrefixRangeNode {
  @Nonnull private final PrefixRangeNode _parent;
  @Nonnull private final List<PrefixRange> _excluded;
  private BDD _bdd;
  private BDDRoute _record;

  public RemainderNode(@Nonnull PrefixRangeNode parent, @Nonnull List<PrefixRange> excluded) {
    _parent = parent;
    _excluded = Collections.unmodifiableList(excluded);
  }

  public PrefixRangeNode getParent() {
    return _parent;
  }

  @Override
  @Nonnull
  public List<PrefixRangeNode> getParents() {
    return Collections.singletonList(_parent);
  }

  private void computeBDD(BDDRoute record) {
    if (_bdd == null || !record.equals(_record)) {
      _record = record;
      _bdd = TransferBDD.isRelevantFor(record, _parent.getPrefixRange());
      for (PrefixRange toExclude : _excluded) {
        BDD excluded = TransferBDD.isRelevantFor(record, toExclude);
        _bdd.andWith(excluded.not());
      }
    }
  }

  @Nonnull
  @Override
  public BDD getBDD(BDDRoute record) {
    computeBDD(record);
    return _bdd;
  }

  @Override public boolean hasPrefixRange() {
    return false;
  }

  public @Nonnull List<PrefixRange> getExcluded() {
    return _excluded;
  }
}
