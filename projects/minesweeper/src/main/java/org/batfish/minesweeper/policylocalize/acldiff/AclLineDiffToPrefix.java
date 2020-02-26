package org.batfish.minesweeper.policylocalize.acldiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.minesweeper.policylocalize.acldiff.representation.AclToDescribedHeaderSpaces;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;

public class AclLineDiffToPrefix {

  private IpAccessList _acl1;
  private IpAccessList _acl2;
  private IpAccessListLine _line1;
  private IpAccessListLine _line2;
  private Set<IpAccessListLine> _diffLines1;
  private Set<IpAccessListLine> _diffLines2;

  private boolean _differencesCalculated;
  private Map<ConjunctHeaderSpace, List<ConjunctHeaderSpace>> _differences;
  private StringBuilder _output;

  AclLineDiffToPrefix(IpAccessList acl1, IpAccessList acl2, IpAccessListLine line1,
      IpAccessListLine line2) {
    _acl1 = acl1;
    _acl2 = acl2;
    _line1 = line1;
    _line2 = line2;
    _diffLines1 = new HashSet<>();
    _diffLines2 = new HashSet<>();
    _differences = new HashMap<>();
    _differencesCalculated = false;
    _output = new StringBuilder();
  }

  /**
   * Given two ACL lines in corresponding ACLs, prints out the intersection of the lines minus the
   * prefixes in the previous lines
   */
  private void calculateDifference() {
    List<ConjunctHeaderSpace> spaces1 = AclToDescribedHeaderSpaces.createPrefixSpaces(_line1);
    List<ConjunctHeaderSpace> spaces2 = AclToDescribedHeaderSpaces.createPrefixSpaces(_line2);

    _output.append("DIFFERENCES").append("\n");
    List<ConjunctHeaderSpace> resultSpaces = new ArrayList<>();

    for (ConjunctHeaderSpace ps1 : spaces1) {
      for (ConjunctHeaderSpace ps2 : spaces2) {
        Optional<ConjunctHeaderSpace> optional = ps1.intersection(ps2);
        optional.ifPresent(resultSpaces::add);
      }
    }
    for (ConjunctHeaderSpace resultSpace : resultSpaces) {
      boolean doOutput = true;
      List<ConjunctHeaderSpace> diffs = new ArrayList<>();
      for (IpAccessListLine line : _acl1.getLines()) {
        if (line.equals(_line1)) {
          _diffLines1.add(line);
          break;
        }
        // Before reaching matching line, check that lines are relevant and affect region
        List<ConjunctHeaderSpace> lineSpaces = AclToDescribedHeaderSpaces.createPrefixSpaces(line);
        for (ConjunctHeaderSpace lineSpace : lineSpaces) {
          // Prevent adding irrelevant regions to output
          if (lineSpace.contains(resultSpace)) {
            doOutput = false;
            break;
          }
          Optional<ConjunctHeaderSpace> optional = lineSpace.intersection(resultSpace);
          if (optional.isPresent()) {
            _diffLines1.add(line);
            ConjunctHeaderSpace intersection = optional.get();
            boolean skip = false;
            for (int i = 0; i < diffs.size(); i++) {
              if (diffs.get(i).contains(intersection)) {
                skip = true;
                break;
              } else if (intersection.contains(diffs.get(i))) {
                diffs.set(i, intersection);
                skip = true;
                break;
              }
            }
            if (!skip) {
              diffs.add(intersection);
            }
          }
        }
      }
      for (IpAccessListLine line : _acl2.getLines()) {
        if (line.equals(_line2)) {
          _diffLines2.add(line);
          break;
        }
        // Before reaching matching line, check that lines are relevant and affect region
        List<ConjunctHeaderSpace> lineSpaces = AclToDescribedHeaderSpaces.createPrefixSpaces(line);
        for (ConjunctHeaderSpace lineSpace : lineSpaces) {
          // Prevent adding irrelevant regions to output
          if (lineSpace.contains(resultSpace)) {
            doOutput = false;
            break;
          }
          Optional<ConjunctHeaderSpace> optional = lineSpace.intersection(resultSpace);
          if (optional.isPresent()) {
            _diffLines2.add(line);
            ConjunctHeaderSpace intersection = optional.get();
            boolean skip = false;
            for (int i = 0; i < diffs.size(); i++) {
              if (diffs.get(i).contains(intersection)) {
                skip = true;
                break;
              } else if (intersection.contains(diffs.get(i))) {
                diffs.set(i, intersection);
                skip = true;
                break;
              }
            }
            if (!skip) {
              diffs.add(intersection);
            }
          }
        }
      }

      if (doOutput) {
        _differences.put(resultSpace, new ArrayList<>());
        for (ConjunctHeaderSpace sp : diffs) {
          _differences.get(resultSpace).add(sp);
        }

        _output.append(resultSpace).append("\n");
        for (ConjunctHeaderSpace sp : diffs) {
          _output.append("\t- ").append(sp).append("\n");
        }
      }
    }
    _differencesCalculated = true;
  }

  void printDifferenceInPrefix() {
    if (!_differencesCalculated) {
      calculateDifference();
    }
    System.out.print(_output);
  }

  private Map<ConjunctHeaderSpace, List<ConjunctHeaderSpace>> getDifferences() {
    if (!_differencesCalculated) {
      calculateDifference();
    }
    return _differences;
  }

  public Set<IpAccessListLine> getAcl1LineDifferences() {
    if (!_differencesCalculated) {
      calculateDifference();
    }
    return _diffLines1;
  }

  public Set<IpAccessListLine> getAcl2LineDifferences() {
    if (!_differencesCalculated) {
      calculateDifference();
    }
    return _diffLines2;
  }

  AclDiffReport getAclDiffReport(String r1, String r2) {
    if (!_differencesCalculated) {
      calculateDifference();
    }
    Set<ConjunctHeaderSpace> subtractedRegions = new HashSet<>();
    for (List<ConjunctHeaderSpace> regionList : getDifferences().values()) {
      subtractedRegions.addAll(regionList);
    }
    return new AclDiffReport(
        _differences.keySet(),
        subtractedRegions,
        r1,
        _acl1, Collections.singletonList(_line1),
        _diffLines1,
        r2,
        _acl2, Collections.singletonList(_line2),
        _diffLines2);
  }
}
