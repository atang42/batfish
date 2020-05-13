package org.batfish.minesweeper.policylocalize;

import java.util.List;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.common.bdd.BDDInteger;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.SubRange;
import org.batfish.minesweeper.Protocol;
import org.batfish.minesweeper.bdd.BDDRoute;

public class RouteToBDD {

  private BDDFactory _factory;
  private BDDRoute _record;

  private static final long DEFAULT_METRIC = 100;
  private static final long DEFAULT_LOCAL_PREF = 100;
  private static final long DEFAULT_MED = 100;

  public RouteToBDD(BDDRoute record) {
    _factory = BDDRoute.getFactory();
    _record = record;
  }

  public BDD firstBitsEqual(BDD[] bits, Prefix p, int length) {
    long b = p.getStartIp().asLong();
    BDD acc = _factory.one();
    for (int i = 0; i < length; i++) {
      boolean res = Ip.getBitAtPosition(b, i);
      if (res) {
        acc = acc.and(bits[i]);
      } else {
        acc = acc.diff(bits[i]);
      }
    }
    return acc;
  }

  public BDD isRelevantFor(BDDRoute record, PrefixRange range) {
    Prefix p = range.getPrefix();
    BDD prefixMatch = firstBitsEqual(record.getPrefix().getBitvec(), p, p.getPrefixLength());

    BDDInteger prefixLength = record.getPrefixLength();
    SubRange r = range.getLengthRange();
    int lower = r.getStart();
    int upper = r.getEnd();
    BDD lenMatch = prefixLength.range(lower, upper);

    return lenMatch.and(prefixMatch);
  }

  public BDD buildPrefixBDD(PrefixRange range) {
    BDD defaultMetric = _record.getMetric().value(DEFAULT_METRIC);
    BDD defaultMed = _record.getMed().value(DEFAULT_MED);
    BDD defaultLocalPref = _record.getLocalPref().value(DEFAULT_LOCAL_PREF);
    BDD communities = _record.getCommunities().values().stream().reduce(BDD::and).get().not();

    BDD prefixBDD = isRelevantFor(_record, range);

    return prefixBDD.and(defaultMetric).and(defaultMed).and(defaultLocalPref).and(communities);
  }

  /*
   Returns BDD representing all packets matching one of the prefix ranges
  */
  public BDD buildPrefixRangesBDD(List<PrefixRange> prefixRanges) {
    BDD acc = allRoutes();
    return acc.and(prefixRanges.stream()
        .map(range -> isRelevantFor(_record, range))
        .reduce(BDD::or)
        .orElse(BDDRoute.getFactory().zero()));
  }

  public BDD allRoutes() {
    BDDInteger prefixLength = _record.getPrefixLength();
    BDD validPfxLen = prefixLength.range(0, 32);
    BDD noCommunities = _record.getCommunities()
        .values()
        .stream()
        .map(BDD::not)
        .reduce(BDD::and)
        .orElse(_factory.one());
    BDD onlyBgpHasCommunities = _record.getProtocolHistory()
        .value(Protocol.BGP)
        .ite(_factory.one(), noCommunities);
    return validPfxLen.and(onlyBgpHasCommunities);
  }
}
