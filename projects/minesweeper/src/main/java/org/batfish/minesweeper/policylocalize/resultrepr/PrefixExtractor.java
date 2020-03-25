package org.batfish.minesweeper.policylocalize.resultrepr;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.RouteFilterLine;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.SubRange;
import org.batfish.datamodel.routing_policy.expr.ExplicitPrefixSet;
import org.batfish.minesweeper.bdd.BDDRoute;

public class PrefixExtractor {
  @Nonnull private final Set<PrefixRange> _prefixRanges;
  @Nonnull private final BDDRoute _record;
  @Nullable private PrefixRangeDAG _graph;
  private boolean _changesOccurred;

  public PrefixExtractor(@Nonnull Collection<RouteFilterList> routeFilterLists, @Nonnull BDDRoute record) {
    _prefixRanges = new TreeSet<>();
    _record = record;
    addRouteFilterLists(routeFilterLists);
  }

  /*
  Add prefix ranges for explicit prefix sets
   */
  public void addExplicitPrefixSet(@Nonnull ExplicitPrefixSet prefixSet) {
    _prefixRanges.addAll(prefixSet.getPrefixSpace().getPrefixRanges());
    _changesOccurred = true;
  }

  /*
  Add prefix ranges for route filter lists
   */
  public void addRouteFilterList(@Nonnull RouteFilterList routeFilterList) {
    for (RouteFilterLine line : routeFilterList.getLines()) {
      IpWildcard ipWildcard = line.getIpWildcard();
      SubRange lengthRange = line.getLengthRange();

      PrefixRange range = new PrefixRange(ipWildcard.toPrefix(), lengthRange);
      _prefixRanges.add(range);
    }
    _changesOccurred = true;
  }

  /*
  Add prefix ranges from collection of RouteFilterLists
   */
  private void addRouteFilterLists(@Nonnull Collection<RouteFilterList> routeFilterLists) {
    for (RouteFilterList routeFilterList : routeFilterLists) {
      addRouteFilterList(routeFilterList);
    }
  }

  /*
  Returns the prefix ranges match the input bdd

  The input BDD represents a set of route announcements
  The output consists of two lists of PrefixRanges. The included ranges are those, whose BDD
  representations overlap with the input BDD. The excluded ranges are those that are subranges of
  the included BDD but do not match the input BDD
   */
  public List<IncludedExcludedPrefixRanges> getIncludedExcludedPrefixRanges(BDD bdd) {
    if (_graph == null || _changesOccurred) {
      _graph = PrefixRangeDAG.build(_prefixRanges);
      _changesOccurred = false;
    }
    return _graph.getRangesMatchingBDD(bdd, _record);
  }

}
