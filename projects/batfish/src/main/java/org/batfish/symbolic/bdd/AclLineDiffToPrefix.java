package org.batfish.symbolic.bdd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;

public class AclLineDiffToPrefix {

  IpAccessList _acl1;
  IpAccessList _acl2;
  IpAccessListLine _line1;
  IpAccessListLine _line2;
  Set<IpAccessListLine> _diffLines1;
  Set<IpAccessListLine> _diffLines2;

  boolean _differencesCalculated;
  Map<PacketPrefixRegion, List<PacketPrefixRegion>> _differences;

  public AclLineDiffToPrefix(IpAccessList acl1, IpAccessList acl2,
      IpAccessListLine line1, IpAccessListLine line2) {
    _acl1 = acl1;
    _acl2 = acl2;
    _line1 = line1;
    _line2 = line2;
    _diffLines1 = new HashSet<>();
    _diffLines2 = new HashSet<>();
    _differences = new HashMap<>();
    _differencesCalculated = false;
  }

  /**
   * Given two ACL lines in corresponding ACLs, prints out the intersection of the lines minus the
   * prefixes in the previous lines
   */
  public void getDifferenceInPrefixes() {
    List<PacketPrefixRegion> spaces1 = PacketPrefixRegion.createPrefixSpace(_line1);
    List<PacketPrefixRegion> spaces2 = PacketPrefixRegion.createPrefixSpace(_line2);

    System.out.println("DIFFERENCES");
    List<PacketPrefixRegion> resultSpaces = new ArrayList<>();

    for (PacketPrefixRegion ps1 : spaces1) {
      for (PacketPrefixRegion ps2 : spaces2) {
        Optional<PacketPrefixRegion> optional = ps1.intersection(ps2);
        if (optional.isPresent()) {
          resultSpaces.add(optional.get());
        }
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
            if (_acl1.getLines().contains(line)) {
              _diffLines1.add(line);
            }
            PacketPrefixRegion intersection = optional.get();
            boolean skip = false;
            for (int i = 0; i < diffs.size(); i++) {
              if (intersection.contains(diffs.get(i))) {
                diffs.set(i, intersection);
                break;
              } else if (diffs.get(i).contains(intersection)) {
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
            if (_acl2.getLines().contains(line)) {
              _diffLines2.add(line);
            }
            PacketPrefixRegion intersection = optional.get();
            boolean skip = false;
            for (int i = 0; i < diffs.size(); i++) {
              if (intersection.contains(diffs.get(i))) {
                diffs.set(i, intersection);
                break;
              } else if (diffs.get(i).contains(intersection)) {
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

        System.out.println(resultSpace);
        _differencesCalculated = true;
      }
    }
  }

  public Map<PacketPrefixRegion, List<PacketPrefixRegion>> getDifferences() {
    if (!_differencesCalculated) {
      getDifferenceInPrefixes();
    }
    return _differences;
  }

  public Set<IpAccessListLine> getAcl1LineDifferences() {
    if (!_differencesCalculated) {
      getDifferenceInPrefixes();
    }
    return _diffLines1;
  }

  public Set<IpAccessListLine> getAcl2LineDifferences() {
    if (!_differencesCalculated) {
      getDifferenceInPrefixes();
    }
    return _diffLines2;
  }

  public AclDiffReport getAclDiffReport(String r1, String r2) {
    if (!_differencesCalculated) {
      getDifferenceInPrefixes();
    }
    AclDiffReport report = new AclDiffReport(_differences.keySet(),
        r1, _acl1, Arrays.asList(_line1), _diffLines1,
        r2, _acl2, Arrays.asList(_line2), _diffLines2);
    return report;
  }

}
