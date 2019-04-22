package org.batfish.symbolic.bdd;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.batfish.config.Settings;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.DefinedStructureInfo;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.answers.ConvertConfigurationAnswerElement;
import org.batfish.datamodel.answers.ParseVendorConfigurationAnswerElement;
import org.batfish.main.Batfish;

public class AclToConfigLines {

  private Batfish _batfish;
  private ConvertConfigurationAnswerElement _ccae;
  private ParseVendorConfigurationAnswerElement _pvcae;
  private Settings _settings;

  public AclToConfigLines(Batfish batfish) {
    _batfish = batfish;
    _ccae = _batfish.loadConvertConfigurationAnswerElementOrReparse();
    _pvcae = _batfish.loadParseVendorConfigurationAnswerElement();
    _settings = _batfish.getSettings();
  }

  private SortedSet<Integer> getAclLineNums(String router, IpAccessList acl) {
    Collection<String> filenames = _pvcae.getFileMap().get(router);
    for (String name : filenames) {
      for (String structType : _ccae.getDefinedStructures().get(name).keySet()) {
        if (structType.contains("access-list") || structType.contains("filter")) {
          DefinedStructureInfo info =
              _ccae
                  .getDefinedStructures()
                  .get(name)
                  .get(structType)
                  .getOrDefault(acl.getName(), null);
          if (info != null) return info.getDefinitionLines();
        }
      }
    }
    return new TreeSet<>();
  }

  public SortedMap<Integer, String> getAclLineText(String router, IpAccessList acl) {
    SortedSet<Integer> lineNums = getAclLineNums(router, acl);
    Path path = _settings.getActiveTestrigSettings().getInputPath();
    String name = new ArrayList<String>(_pvcae.getFileMap().get(router)).get(0);
    Path fullname = Paths.get(path.toString(), name);

    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(fullname.toFile()));
    } catch (FileNotFoundException e) {
      System.err.println("Cannot find file " + fullname);
      return new TreeMap<>();
    }
    String currLine;
    int currLineNum = 1;

    TreeMap<Integer, String> lines = new TreeMap<>();
    try {
      currLine = reader.readLine();
      while (currLine != null) {
        if (lineNums.contains(currLineNum)) {
          lines.put(currLineNum, currLine);
        }
        currLineNum++;
        currLine = reader.readLine();
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return lines;
  }

  private String getConfigText(String router) {
    Path path = _settings.getActiveTestrigSettings().getInputPath();
    String name = new ArrayList<String>(_pvcae.getFileMap().get(router)).get(0);
    Path fullname = Paths.get(path.toString(), name);

    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(fullname.toFile()));
    } catch (FileNotFoundException e) {
      System.err.println("Cannot find file " + fullname);
      return "";
    }
    StringBuilder builder = new StringBuilder();
    String currLine;

    try {
      currLine = reader.readLine();
      while (currLine != null) {
        builder.append(currLine).append("\n");
        currLine = reader.readLine();
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return builder.toString();
  }

  public void printRelevantLines(
      String router,
      IpAccessList acl,
      Collection<PacketPrefixRegion> regions,
      @Nullable List<IpAccessListLine> lastLines,
      boolean hasImplicitDeny,
      boolean printMore) {
    SortedMap<Integer, String> lines = getAclLineText(router, acl);
    ConfigurationFormat format = _batfish.loadConfigurations().get(router).getConfigurationFormat();
    if (format.getVendorString().equals("cisco")) {
      /*    CISCO PARSE TEST
            Settings settings = new Settings();
            CiscoCombinedParser parser = new CiscoCombinedParser(getConfigText(router), settings, format);
            CiscoControlPlaneExtractor extractor =
                new CiscoControlPlaneExtractor(getConfigText(router), parser, format, new Warnings());
            ParserRuleContext tree =
                Batfish.parse(
                    parser, new BatfishLogger(BatfishLogger.LEVELSTR_FATAL, false), settings);
            extractor.processParseTree(tree);
            CiscoConfiguration vendorConfiguration =
                (CiscoConfiguration) extractor.getVendorConfiguration();
      */
      printRelevantLinesCisco(lines, acl, regions, lastLines, hasImplicitDeny, printMore);
    } else if (format.getVendorString().equals("juniper")) {
      /*    JUNIPER PARSE TEST
            Settings settings = new Settings();

            Flattener flattener = Batfish.flatten(
                getConfigText(router),
                new BatfishLogger(BatfishLogger.LEVELSTR_FATAL, false),
                settings,
                new Warnings(),
                format,
                "HEADER");

            String flattenedText = flattener.getFlattenedConfigurationText();
            FlattenerLineMap linemap = flattener.getOriginalLineMap();

            FlatJuniperCombinedParser parser = new FlatJuniperCombinedParser(flattenedText, settings, linemap);
            FlatJuniperControlPlaneExtractor extractor =
                new FlatJuniperControlPlaneExtractor(flattenedText, parser, new Warnings());
            ParserRuleContext tree =
                Batfish.parse(
                    parser, new BatfishLogger(BatfishLogger.LEVELSTR_FATAL, false), settings);
            extractor.processParseTree(tree);
            VendorConfiguration vendorConfiguration = extractor.getVendorConfiguration();
      */
      printRelevantLinesJuniper(
          lines.firstKey(), lines.lastKey(), router, acl, regions, lastLines, hasImplicitDeny, printMore);
    } else {
      System.err.println("Does not support the format: " + format);
    }
  }

  public void printRelevantLinesCisco(
      SortedMap<Integer, String> lines,
      IpAccessList acl,
      Collection<PacketPrefixRegion> regions,
      @Nullable List<IpAccessListLine> lastLines,
      boolean hasImplicitDeny,
      boolean printMore) {

    List<IpAccessListLine> relevantAclLines = new ArrayList<>();
    int prevPrint = 0;
    for (IpAccessListLine aclLine : acl.getLines()) {
      List<PacketPrefixRegion> lineRegions = PacketPrefixRegion.createPrefixSpace(aclLine);
      for (PacketPrefixRegion lineReg : lineRegions) {
        for (PacketPrefixRegion region : regions) {
          if (lineReg.intersection(region).isPresent()) {
            relevantAclLines.add(aclLine);
          }
        }
      }
    }
    Set<String> relevantLineTexts =
        relevantAclLines
            .stream()
            .map(IpAccessListLine::getName)
            .map(String::trim)
            .collect(Collectors.toSet());
    if (lastLines == null) {
      lastLines = new ArrayList<>();
    }
    Set<String> lastLineTexts =
        lastLines
            .stream()
            .map(IpAccessListLine::getName)
            .map(String::trim)
            .collect(Collectors.toSet());
    int lastLinesReached = 0;
    for (Entry<Integer, String> ent : lines.entrySet()) {
      int lineNum = ent.getKey();
      String text = ent.getValue();

      boolean done = false;
      if (text.contains("ip access-list extended")) {
        prevPrint = lineNum;
        System.out.format("%-6d %s\n", lineNum, text);
      } else {
        for (String lastLine : lastLineTexts) {
          if (text.contains(lastLine)) {
            if (prevPrint != 0 && prevPrint < lineNum - 1) {
              System.out.println("      ...");
            }
            prevPrint = lineNum;
            System.out.format("*%-5d %s\n", lineNum, text);
            done = true;
            lastLinesReached++;
          }
        }
      }
      if (!done) {
        for (String relLine : relevantLineTexts) {
          if (text.contains(relLine)) {
            if (prevPrint != 0 && prevPrint < lineNum - 1) {
              System.out.println("      ...");
            }
            prevPrint = lineNum;
            System.out.format("%-6d %s\n", lineNum, text);
          }
        }
      }
      if (!printMore && lastLinesReached == lastLines.size()) {
        break;
      }
    }
    if (hasImplicitDeny) {
      System.out.println("*Implicit deny");
    }
  }

  public void printRelevantLinesJuniper(
      int firstLine,
      int lastLine,
      String router,
      IpAccessList acl,
      Collection<PacketPrefixRegion> regions,
      @Nullable List<IpAccessListLine> lastAclLines,
      boolean hasImplicitDeny,
      boolean printMore) {
    int prevPrint = 0;
    List<IpAccessListLine> relevantAclLines = new ArrayList<>();
    for (IpAccessListLine aclLine : acl.getLines()) {
      List<PacketPrefixRegion> lineRegions = PacketPrefixRegion.createPrefixSpace(aclLine);
      for (PacketPrefixRegion lineReg : lineRegions) {
        for (PacketPrefixRegion region : regions) {
          if (lineReg.intersection(region).isPresent()) {
            relevantAclLines.add(aclLine);
          }
        }
      }
    }
    Set<String> relevantLineTexts =
        relevantAclLines.stream().map(IpAccessListLine::getName).collect(Collectors.toSet());
    if (lastAclLines == null) {
      lastAclLines = new ArrayList<>();
    }
    Set<String> lastLineTexts =
        lastAclLines.stream().map(IpAccessListLine::getName).collect(Collectors.toSet());

    Path path = _settings.getActiveTestrigSettings().getInputPath();
    String name = new ArrayList<String>(_pvcae.getFileMap().get(router)).get(0);
    Path fullname = Paths.get(path.toString(), name);

    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(fullname.toFile()));
    } catch (FileNotFoundException e) {
      System.err.println("Cannot find file " + fullname);
    }
    String currLine;
    int currLineNum = 1;
    boolean doPrint = false;
    int currDepth = 0;
    int lastLinesReached = 0;
    try {
      currLine = reader.readLine();
      while (currLine != null && currLineNum <= lastLine) {
        if (currLineNum >= firstLine && currLineNum <= lastLine) {
          if (!doPrint && currLine.contains("term")) {
            for (String termName : relevantLineTexts) {
              if (currLine.contains(termName)) {
                currDepth = 0;
                doPrint = true;
                break;
              }
            }
          }
          if (doPrint || currLine.contains("filter")) {
            if (prevPrint != 0 && prevPrint < currLineNum - 1) {
              System.out.println("      ...");
            }
            prevPrint = currLineNum;
            boolean found = false;
            for (String lastAclLine : lastLineTexts) {
              if (currLine.contains(lastAclLine)) {
                found = true;
                System.out.format("*%-5d %s\n", currLineNum, currLine);
                lastLinesReached++;
                break;
              }
            }
            if (!found) {
              System.out.format("%-6d %s\n", currLineNum, currLine);
            }
            if (currLine.contains("{")) {
              currDepth++;
            }
            if (currLine.contains("}")) {
              currDepth--;
            }
            if (currDepth <= 0) {
              doPrint = false;
              if (!printMore && lastLinesReached == lastAclLines.size()) {
                break;
              }
            }
          }
        }
        currLineNum++;
        currLine = reader.readLine();
      }
      if (hasImplicitDeny) {
        System.out.println("*Implicit deny");
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
