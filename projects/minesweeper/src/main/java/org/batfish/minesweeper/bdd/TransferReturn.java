package org.batfish.minesweeper.bdd;

import javax.annotation.Nonnull;
import net.sf.javabdd.BDD;
import org.batfish.minesweeper.utils.Tuple;

public class TransferReturn extends Tuple<BDDRoute, BDD> {

  @Nonnull private BDD _exit;
  @Nonnull private BDD _return;

  TransferReturn(BDDRoute r, BDD b) {
    super(r, b);
    _exit  = BDDRoute.getFactory().zero();
    _return  = BDDRoute.getFactory().zero();
  }

  public String debug() {
    return getFirst().dot(getSecond());
  }

  /*
  Gets BDD representing space of announcements that reach an exit statement, stopping all processing
  by all parent policies
   */
  @Nonnull
  public BDD getExit() {
    return _exit;
  }

  public void setExit(@Nonnull BDD exit) {
    this._exit = exit;
  }

  /*
  Gets BDD representing space of announcements that reach an exit statement, stopping all processing
  by current policy but continues evaluating parent policies
   */
  @Nonnull
  public BDD getReturn() {
    return _return;
  }

  public void setReturn(@Nonnull BDD ret) {
    this._return = ret;
  }
}
