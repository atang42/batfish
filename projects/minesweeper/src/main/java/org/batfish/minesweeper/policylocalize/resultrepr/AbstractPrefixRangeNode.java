package org.batfish.minesweeper.policylocalize.resultrepr;

import java.util.List;
import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.batfish.minesweeper.bdd.BDDRoute;

public abstract class AbstractPrefixRangeNode {

  @Nonnull
  public abstract List<PrefixRangeNode> getParents();

  @Nonnull
  public abstract BDD getBDD(BDDRoute record);

  public boolean intersectsBDD(BDD bdd, BDDRoute record) {
    return getBDD(record).andSat(bdd);
  }

  public abstract boolean hasPrefixRange();
}
