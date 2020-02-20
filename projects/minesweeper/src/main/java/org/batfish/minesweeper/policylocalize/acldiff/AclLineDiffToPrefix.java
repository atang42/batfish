package org.batfish.minesweeper.policylocalize.acldiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.batfish.common.bdd.PacketPrefixRegion;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;

public class AclLineDiffToPrefix {

  private IpAccessList _acl1;
  private IpAccessList _acl2;
  private IpAccessListLine _line1;
  private IpAccessListLine _line2;
  private Set<IpAccessListLine> _diffLines1;
  private Set<IpAccessListLine> _diffLines2;

  private boolean _differencesCalculated;
  private Map<PacketPrefixRegion, List<PacketPrefixRegion>> _differences;
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
    List<PacketPrefixRegion> spaces1 = PacketPrefixRegion.createPrefixSpace(_line1);
    List<PacketPrefixRegion> spaces2 = PacketPrefixRegion.createPrefixSpace(_line2);

    _output.append("DIFFERENCES").append("\n");
    List<PacketPrefixRegion> resultSpaces = new ArrayList<>();

    for (PacketPrefixRegion ps1 : spaces1) {
      for (PacketPrefixRegion ps2 : spaces2) {
        Optional<PacketPrefixRegion> optional = ps1.intersection(ps2);
        optional.ifPresent(resultSpaces::add);
      }
    }
    for (PacketPrefixRegion resultSpace : resultSpaces) {
      boolean doOutput = true;
      List<PacketPrefixRegion> diffs = new ArrayList<>();
      for (IpAccessListLine line : _acl1.getLines()) {
        if (line.equals(_line1)) {
          _diffLines1.add(line);
          break;
        }
        // Before reaching matching line, check that lines are relevant and affect region
        List<PacketPrefixRegion> lineSpaces = PacketPrefixRegion.createPrefixSpace(line);
        for (PacketPrefixRegion lineSpace : lineSpaces) {
          // Prevent adding irrelevant regions to output
          if (lineSpace.contains(resultSpace)) {
            doOutput = false;
            break;
          }
          Optional<PacketPrefixRegion> optional = lineSpace.intersection(resultSpace);
          if (optional.isPresent()) {
            _diffLines1.add(line);
            PacketPrefixRegion intersection = optional.get();
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
        List<PacketPrefixRegion> lineSpaces = PacketPrefixRegion.createPrefixSpace(line);
        for (PacketPrefixRegion lineSpace : lineSpaces) {
          // Prevent adding irrelevant regions to output
          if (lineSpace.contains(resultSpace)) {
            doOutput = false;
            break;
          }
          Optional<PacketPrefixRegion> optional = lineSpace.intersection(resultSpace);
          if (optional.isPresent()) {
            _diffLines2.add(line);
            PacketPrefixRegion intersection = optional.get();
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
        for (PacketPrefixRegion sp : diffs) {
          _differences.get(resultSpace).add(sp);
        }

        _output.append(resultSpace).append("\n");
        for (PacketPrefixRegion sp : diffs) {
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

  private Map<PacketPrefixRegion, List<PacketPrefixRegion>> getDifferences() {
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
    Set<PacketPrefixRegion> subtractedRegions = new HashSet<>();
    for (List<PacketPrefixRegion> regionList : getDifferences().values()) {
      subtractedRegions.addAll(regionList);
    }
    return new AclDiffReport(
        _differences.keySet(),
        subtractedRegions,
        r1,
        _acl1,
        Arrays.asList(_line1),
        _diffLines1,
        r2,
        _acl2,
        Arrays.asList(_line2),
        _diffLines2);
  }
}
