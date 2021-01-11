package org.batfish.minesweeper.policylocalize.acldiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.ExprAclLine;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.AclLine;
import org.batfish.datamodel.LineAction;
import org.batfish.minesweeper.policylocalize.SymbolicResult;
import org.batfish.minesweeper.policylocalize.acldiff.headerpresent.AclHeaderPresent;
import org.batfish.minesweeper.policylocalize.acldiff.headerpresent.IncludedExcludedHeaderSpaces;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;

public class AclDiffReport {
  private Set<ConjunctHeaderSpace> _regions;
  private Set<ConjunctHeaderSpace> _subtractedRegions;
  private SingleRouterReport _report1;
  private SingleRouterReport _report2;
  private AclHeaderPresent _headerPresent;

  private static class SingleRouterReport {
    final String _router;
    final IpAccessList _acl;
    final SymbolicResult _result;
    List<AclLine> _lastDiffs;
    Set<AclLine> _allDiffs;
    boolean _implicitDeny;

    SingleRouterReport(
        String r,
        IpAccessList acl,
        List<AclLine> lastLines,
        Set<AclLine> allLines) {
      _router = r;
      _acl = acl;
      _lastDiffs = new ArrayList<>(lastLines);
      _allDiffs = new HashSet<>(allLines);
      if (_lastDiffs.isEmpty() || _lastDiffs.get(0) == null) {
        _implicitDeny = true;
        _result = SymbolicResult.REJECT;
        _lastDiffs.clear();
      } else {
        _implicitDeny = false;
        _result =
            ((ExprAclLine)_lastDiffs.get(0)).getAction().equals(LineAction.PERMIT)
                ? SymbolicResult.ACCEPT
                : SymbolicResult.REJECT;
      }
    }

    SingleRouterReport(SingleRouterReport other) {
      this(other._router, other._acl, other._lastDiffs, other._allDiffs);
    }

    void combineWith(SingleRouterReport other) {
      if (!_router.equals(other._router) || !_acl.equals(other._acl) || _result != other._result) {
        throw new IllegalArgumentException();
      }
      _lastDiffs.addAll(other._lastDiffs);
      _allDiffs.addAll(other._allDiffs);
      _implicitDeny = _implicitDeny || other._implicitDeny;
    }

    String getRouterName() {
      return _router;
    }

    IpAccessList getAcl() {
      return _acl;
    }

    SymbolicResult permits() {
      return _result;
    }

    boolean hasImplicitDeny() {
      return _implicitDeny;
    }
  }

  AclDiffReport(
      Set<ConjunctHeaderSpace> regions,
      Set<ConjunctHeaderSpace> subtractedRegions,
      String r1,
      IpAccessList acl1,
      List<AclLine> last1,
      Set<AclLine> all1,
      String r2,
      IpAccessList acl2,
      List<AclLine> last2,
      Set<AclLine> all2) {
    _regions = new HashSet<>(regions);
    _subtractedRegions = new HashSet<>(subtractedRegions);
    _report1 = new SingleRouterReport(r1, acl1, last1, all1);
    _report2 = new SingleRouterReport(r2, acl2, last2, all2);
  }

  private BitSet getAclBitVector(SingleRouterReport report) {
    BitSet bitVector = new BitSet(report._acl.getLines().size());
    Set<AclLine> lineDiffs = report._allDiffs;
    int i = 0;
    for (AclLine line : report._acl.getLines()) {
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
    System.out.println(_report1.getRouterName());
    aclToConfig.printRelevantLines(
        _report1.getRouterName(),
        _report1.getAcl(),
        _regions,
        _report1._lastDiffs,
        _report1._implicitDeny,
        printMore);
    System.out.println();
    System.out.println(_report2.getRouterName());
    aclToConfig.printRelevantLines(
        _report2.getRouterName(),
        _report2.getAcl(),
        _regions,
        _report2._lastDiffs,
        _report2._implicitDeny,
        printMore);
    System.out.println();
  }


  public LineDifference toLineDifference(
      IBatfish batfish, boolean printMore, boolean differential, BDD diff, BDDPacket packet) {
    // AclToConfigLines aclToConfig = new AclToConfigLines(batfish, differential);
    String rel1 = _report1._implicitDeny ? "DEFAULT REJECT" : _report1._lastDiffs.stream().map(AclLine::getName).collect(
        Collectors.joining(", "));
//        aclToConfig.getRelevantLines(
//            _report1.getRouterName(),
//            _report1.getAcl(),
//            _regions,
//            _report1._lastDiffs,
//            _report1._implicitDeny,
//            printMore);
    String rel2 = _report2._implicitDeny ? "DEFAULT REJECT" : _report2._lastDiffs.stream().map(AclLine::getName).collect(
        Collectors.joining(", "));
//        aclToConfig.getRelevantLines(
//            _report2.getRouterName(),
//            _report2.getAcl(),
//            _regions,
//            _report2._lastDiffs,
//            _report2._implicitDeny,
//            printMore);

    String matches1 = Arrays.stream(rel1.split("\n")).filter(line -> line.startsWith("*")).reduce(String::concat).orElse("");
    String matches2 = Arrays.stream(rel2.split("\n")).filter(line -> line.startsWith("*")).reduce(String::concat).orElse("");


    SortedSet<String> difference =
        _regions.stream()
            .map(ConjunctHeaderSpace::toString)
            .collect(Collectors.toCollection(TreeSet::new));
    SortedSet<String> diffSub =
        _subtractedRegions.stream()
            .map(ConjunctHeaderSpace::toString)
            .collect(Collectors.toCollection(TreeSet::new));
//
//    if (_headerPresent == null) {
//      _headerPresent = new AclHeaderPresent(Arrays.asList(_report1.getAcl(), _report2.getAcl()),
//          packet);
//    }
//    List<IncludedExcludedHeaderSpaces> includedExcludedHeaderSpaces =
//        _headerPresent.getIncludedExcludedHeaderSpaces(diff, null);
//    difference = includedExcludedHeaderSpaces.stream()
//        .map(IncludedExcludedHeaderSpaces::getIncludedPrefixString)
//        .collect(Collectors.toCollection(TreeSet::new));
//    diffSub = includedExcludedHeaderSpaces.stream()
//        .map(IncludedExcludedHeaderSpaces::getExcludedPrefixString)
//        .collect(Collectors.toCollection(TreeSet::new));

    return new LineDifference(
        _report1.getRouterName(), _report2.getRouterName(),
        _report1.getAcl().getName(), _report2.getAcl().getName(),
        rel1, rel2,
        matches1, matches2,
        difference, diffSub,
        _report1.permits(), _report2.permits());
  }
}
