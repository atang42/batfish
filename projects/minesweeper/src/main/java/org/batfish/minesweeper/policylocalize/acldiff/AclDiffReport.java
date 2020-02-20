package org.batfish.minesweeper.policylocalize.acldiff;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.batfish.common.bdd.PacketPrefixRegion;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.LineAction;

public class AclDiffReport {
  private Set<PacketPrefixRegion> _regions;
  private Set<PacketPrefixRegion> _subtractedRegions;
  private SingleRouterReport _report1;
  private SingleRouterReport _report2;

  private static class SingleRouterReport {
    String _router;
    IpAccessList _acl;
    List<IpAccessListLine> _lastDiffs;
    Set<IpAccessListLine> _allDiffs;
    boolean _permits;
    boolean _implicitDeny;

    SingleRouterReport(
        String r,
        IpAccessList acl,
        List<IpAccessListLine> lastLines,
        Set<IpAccessListLine> allLines) {
      _router = r;
      _acl = acl;
      _lastDiffs = new ArrayList<>(lastLines);
      _allDiffs = new HashSet<>(allLines);
      if (_lastDiffs.isEmpty() || _lastDiffs.get(0) == null) {
        _implicitDeny = true;
        _permits = false;
        _lastDiffs.clear();
      } else {
        _implicitDeny = false;
        _permits = _lastDiffs.get(0).getAction().equals(LineAction.PERMIT);
      }
    }

    SingleRouterReport(SingleRouterReport other) {
      this(other._router, other._acl, other._lastDiffs, other._allDiffs);
    }

    void combineWith(SingleRouterReport other) {
      if (!_router.equals(other._router)
          || !_acl.equals(other._acl)
          || _permits != other._permits) {
        throw new IllegalArgumentException();
      }
      _lastDiffs.addAll(other._lastDiffs);
      _allDiffs.addAll(other._allDiffs);
      _implicitDeny = _implicitDeny || other._implicitDeny;
    }

    boolean permits() {
      return _permits;
    }

    boolean hasImplicitDeny() {
      return _implicitDeny;
    }
  }

  AclDiffReport(Set<PacketPrefixRegion> regions, Set<PacketPrefixRegion> subtractedRegions,
      String r1, IpAccessList acl1, List<IpAccessListLine> last1, Set<IpAccessListLine> all1,
      String r2, IpAccessList acl2, List<IpAccessListLine> last2, Set<IpAccessListLine> all2) {
    _regions = new HashSet<>(regions);
    _subtractedRegions = new HashSet<>(subtractedRegions);
    _report1 = new SingleRouterReport(r1, acl1, last1, all1);
    _report2 = new SingleRouterReport(r2, acl2, last2, all2);
  }

  private BitSet getAclBitVector(SingleRouterReport report) {
    BitSet bitVector = new BitSet(report._acl.getLines().size());
    Set<IpAccessListLine> lineDiffs = report._allDiffs;
    int i = 0;
    for (IpAccessListLine line : report._acl.getLines()) {
      if (lineDiffs.contains(line)) {
        bitVector.set(i, true);
      } else {
        bitVector.set(i, false);
      }
      i++;
    }
    return bitVector;
  }

  private BitSet getAcl1BitVector() {
    return getAclBitVector(_report1);
  }

  private BitSet getAcl2BitVector() {
    return getAclBitVector(_report2);
  }

  public void combineWith(AclDiffReport other) {
    _regions.addAll(other._regions);
    _report1.combineWith(new SingleRouterReport(other._report1));
    _report2.combineWith(new SingleRouterReport(other._report2));
  }

  public int getLineCount() {
    return _report1._allDiffs.size() + _report2._allDiffs.size();
  }

  public static int combinedLineCount(AclDiffReport first, AclDiffReport second) {
    BitSet vec1 = (BitSet) first.getAcl1BitVector().clone();
    vec1.or(second.getAcl1BitVector());
    BitSet vec2 = (BitSet) first.getAcl2BitVector().clone();
    vec2.or(second.getAcl2BitVector());

    return vec1.cardinality() + vec2.cardinality();
  }

  public static boolean combinedSameAction(AclDiffReport first, AclDiffReport second) {
    return first._report1.permits() == second._report1.permits()
        && first._report2.permits() == second._report2.permits();
  }

  public void print(IBatfish batfish, boolean printMore, boolean differential) {

    System.out.println();
    AclToConfigLines aclToConfig = new AclToConfigLines(batfish, differential);
    System.out.println("Configuration lines for : ");
    _regions.forEach((r) -> System.out.println("  " + r));
    System.out.println(_report1._router);
    aclToConfig.printRelevantLines(
        _report1._router, _report1._acl, _regions, _report1._lastDiffs, _report1._implicitDeny, printMore);
    System.out.println();
    System.out.println(_report2._router);
    aclToConfig.printRelevantLines(
        _report2._router, _report2._acl, _regions, _report2._lastDiffs, _report2._implicitDeny, printMore);
    System.out.println();
  }

  public LineDifference toLineDifference(IBatfish batfish, boolean printMore, boolean differential) {
    AclToConfigLines aclToConfig = new AclToConfigLines(batfish, differential);
    String rel1 =
        aclToConfig.getRelevantLines(
            _report1._router,
            _report1._acl,
            _regions,
            _report1._lastDiffs,
            _report1._implicitDeny,
            printMore);
    String rel2 = aclToConfig.getRelevantLines(
            _report2._router, _report2._acl, _regions, _report2._lastDiffs, _report2._implicitDeny, printMore);
    SortedSet<String> difference = _regions
        .stream()
        .map(PacketPrefixRegion::toString)
        .collect(Collectors.toCollection(TreeSet::new));
    SortedSet<String> diffSub = _subtractedRegions.stream()
        .map(PacketPrefixRegion::toString)
        .collect(Collectors.toCollection(TreeSet::new));

    return new LineDifference(_report1._router, _report2._router,
        _report1._acl.getName(), _report2._acl.getName(),
        rel1, rel2,
        difference, diffSub);
  }
}
