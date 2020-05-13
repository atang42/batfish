package org.batfish.minesweeper.policylocalize.acldiff;

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
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.batfish.common.plugin.IBatfish;
import org.batfish.config.Settings;
import org.batfish.datamodel.AclLine;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.DefinedStructureInfo;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.answers.ConvertConfigurationAnswerElement;
import org.batfish.datamodel.answers.ParseVendorConfigurationAnswerElement;
import org.batfish.identifiers.SnapshotId;
import org.batfish.main.Batfish;
import org.batfish.minesweeper.policylocalize.acldiff.representation.AclToDescribedHeaderSpaces;
import org.batfish.minesweeper.policylocalize.acldiff.representation.ConjunctHeaderSpace;
import org.batfish.specifier.SpecifierContext;
import org.batfish.storage.FileBasedStorageDirectoryProvider;

public class AclToConfigLines {

  private Batfish _batfish;
  private ConvertConfigurationAnswerElement _ccae;
  private ParseVendorConfigurationAnswerElement _pvcae;
  private Settings _settings;
  private ConvertConfigurationAnswerElement _ccaeRef;
  private ParseVendorConfigurationAnswerElement _pvcaeRef;
  private SpecifierContext _referenceContext;

  private static String linesep = System.lineSeparator();

  AclToConfigLines(IBatfish batfish, boolean differential) {
    _batfish = (Batfish) batfish;
    _ccae = _batfish.loadConvertConfigurationAnswerElementOrReparse(_batfish.getSnapshot());
    _pvcae = _batfish.loadParseVendorConfigurationAnswerElement(_batfish.getSnapshot());
    _settings = _batfish.getSettings();

    if (differential) {
      _ccaeRef = _batfish.loadConvertConfigurationAnswerElementOrReparse(_batfish.getReferenceSnapshot());
      _pvcaeRef = _batfish.loadParseVendorConfigurationAnswerElement(_batfish.getReferenceSnapshot());
      _referenceContext = _batfish.specifierContext(_batfish.getReferenceSnapshot());
    }
  }

  private SortedSet<Integer> getAclLineNums(String router, IpAccessList acl,
      ParseVendorConfigurationAnswerElement pvcae, ConvertConfigurationAnswerElement ccae) {
    Collection<String> filenames = pvcae.getFileMap().get(router);
    for (String name : filenames) {
      for (String structType : ccae.getDefinedStructures().get(name).keySet()) {
        if (structType.contains("access-list") || structType.contains("filter")) {
          DefinedStructureInfo info = ccae.getDefinedStructures()
              .get(name)
              .get(structType)
              .getOrDefault(acl.getName(), null);
          if (info != null) {
            return info.getDefinitionLines();
          }
        }
      }
    }
    return new TreeSet<>();
  }

  private SortedMap<Integer, String> getAclLineText(String router, IpAccessList acl) {
    FileBasedStorageDirectoryProvider provider =
        new FileBasedStorageDirectoryProvider(_settings.getStorageBase());
    Path path;
    String name;
    SortedSet<Integer> lineNums;
    if (router.endsWith("-current")) {
      path = provider.getSnapshotInputObjectsDir(
          _settings.getContainer(),
          _settings.getTestrig());
      router = router.substring(0, router.length() - "-current".length());
      name = new ArrayList<>(_pvcae.getFileMap().get(router)).get(0);
      lineNums = getAclLineNums(router, acl, _pvcae, _ccae);
    } else if (router.endsWith("-reference")) {
      path = provider.getSnapshotInputObjectsDir(
          _settings.getContainer(),
          _settings.getDeltaTestrig());
      router = router.substring(0, router.length() - "-reference".length());
      name = new ArrayList<>(_pvcaeRef.getFileMap().get(router)).get(0);
      lineNums = getAclLineNums(router, acl, _pvcaeRef, _ccaeRef);
    } else {
      path = provider.getSnapshotInputObjectsDir(
          _settings.getContainer(),
          _settings.getTestrig());
      name = new ArrayList<>(_pvcae.getFileMap().get(router)).get(0);
      lineNums = getAclLineNums(router, acl, _pvcae, _ccae);
    }
    Path filename = Paths.get(name);
    Path fullname = path.resolve(filename);

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
    Path path = _settings.getFlattenDestination();
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

  String getRelevantLines(String router, IpAccessList acl, Collection<ConjunctHeaderSpace> regions,
      @Nullable List<AclLine> lastLines, boolean hasImplicitDeny, boolean printMore) {

    if (router.equals("~NONE~")) {
      return "";
    }

    SortedMap<Integer, String> lines = getAclLineText(router, acl);
    ConfigurationFormat format;
    boolean isReference;
    if (router.endsWith("-current")) {
      router = router.substring(0, router.length() - "-current".length());
      format = _batfish.loadConfigurations(_batfish.getSnapshot())
          .get(router)
          .getConfigurationFormat();
      isReference = false;
    } else if (router.endsWith("-reference")) {
      router = router.substring(0, router.length() - "-reference".length());
      format = _referenceContext.getConfigs().get(router).getConfigurationFormat();
      isReference = true;
    } else {
      format = _batfish.loadConfigurations(_batfish.getSnapshot())
          .get(router)
          .getConfigurationFormat();
      isReference = false;
    }
    String ret = "";
    if (format.getVendorString().equals("cisco")) {
      ret = (getRelevantLinesCisco(lines, acl, regions, lastLines, hasImplicitDeny, printMore));
    } else if (format.getVendorString().equals("juniper")) {
      ret = (getRelevantLinesJuniper(lines.firstKey(),
          lines.lastKey(),
          router,
          acl,
          regions,
          lastLines,
          hasImplicitDeny,
          printMore,
          isReference));
    } else {
      System.err.println("Does not support the format: " + format);
    }
    return ret;
  }

  void printRelevantLines(String router, IpAccessList acl, Collection<ConjunctHeaderSpace> regions,
      @Nullable List<AclLine> lastLines, boolean hasImplicitDeny, boolean printMore) {

    SortedMap<Integer, String> lines = getAclLineText(router, acl);

    ConfigurationFormat format;
    if (router.endsWith("-current")) {
      router = router.substring(0, router.length() - "-current".length());
      format = _batfish.loadConfigurations(_batfish.getSnapshot())
          .get(router)
          .getConfigurationFormat();
    } else if (router.endsWith("-reference")) {
      router = router.substring(0, router.length() - "-reference".length());
      format = _referenceContext.getConfigs().get(router).getConfigurationFormat();
    } else {
      format = _batfish.loadConfigurations(_batfish.getSnapshot())
          .get(router)
          .getConfigurationFormat();
    }
    switch (format.getVendorString()) {
    case "juniper":
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
      printRelevantLinesJuniper(lines.firstKey(),
          lines.lastKey(),
          router,
          acl,
          regions,
          lastLines,
          hasImplicitDeny,
          printMore);
      break;
    case "cisco":
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
      break;
    default:
      System.err.println("May not support the format: " + format);
      printRelevantLinesCisco(lines, acl, regions, lastLines, hasImplicitDeny, printMore);
      break;
    }
  }

  private String getRelevantLinesCisco(SortedMap<Integer, String> lines, IpAccessList acl,
      Collection<ConjunctHeaderSpace> regions, @Nullable List<AclLine> lastLines,
      boolean hasImplicitDeny, boolean printMore) {

    StringBuilder ret = new StringBuilder();
    List<AclLine> relevantAclLines = new ArrayList<>();
    int prevPrint = 0;
    for (AclLine aclLine : acl.getLines()) {
      List<ConjunctHeaderSpace> lineRegions = AclToDescribedHeaderSpaces.createPrefixSpaces(aclLine);
      for (ConjunctHeaderSpace lineReg : lineRegions) {
        for (ConjunctHeaderSpace region : regions) {
          if (lineReg.intersection(region).isPresent()) {
            relevantAclLines.add(aclLine);
          }
        }
      }
    }
    Set<String> relevantLineTexts = relevantAclLines.stream()
        .map(AclLine::getName)
        .filter(Objects::nonNull)
        .map(String::trim)
        .collect(Collectors.toSet());
    if (lastLines == null) {
      lastLines = new ArrayList<>();
    }
    Set<String> lastLineTexts = lastLines.stream()
        .map(AclLine::getName)
        .filter(Objects::nonNull)
        .map(String::trim)
        .collect(Collectors.toSet());
    int lastLinesReached = 0;
    for (Entry<Integer, String> ent : lines.entrySet()) {
      int lineNum = ent.getKey();
      String text = ent.getValue();

      boolean done = false;
      if (text.contains("ip access-list extended")) {
        prevPrint = lineNum;
        ret.append(String.format("%s\n", lineNum, text));
      } else {
        for (String lastLine : lastLineTexts) {
          if (text.contains(lastLine)) {
            if (prevPrint != 0 && prevPrint < lineNum - 1) {
              ret.append("      ...\n");
            }
            prevPrint = lineNum;
            ret.append(String.format("*%s\n", text));
            done = true;
            lastLinesReached++;
          }
        }
      }
      if (!done) {
        for (String relLine : relevantLineTexts) {
          if (text.contains(relLine)) {
            if (prevPrint != 0 && prevPrint < lineNum - 1) {
              ret.append("      ...\n");
            }
            prevPrint = lineNum;
            ret.append(String.format("%s\n", text));
          }
        }
      }
      if (!printMore && lastLinesReached == lastLines.size()) {
        break;
      }
    }
    if (hasImplicitDeny) {
      ret.append("*Implicit deny\n");
    }
    return ret.toString();
  }

  private void printRelevantLinesCisco(SortedMap<Integer, String> lines, IpAccessList acl,
      Collection<ConjunctHeaderSpace> regions, @Nullable List<AclLine> lastLines,
      boolean hasImplicitDeny, boolean printMore) {

    System.out.print(getRelevantLinesCisco(
        lines,
        acl,
        regions,
        lastLines,
        hasImplicitDeny,
        printMore));
  }

  private String getRelevantLinesJuniper(int firstLine, int lastLine, String router,
      IpAccessList acl, Collection<ConjunctHeaderSpace> regions,
      @Nullable List<AclLine> lastAclLines, boolean hasImplicitDeny, boolean printMore, boolean reference) {
    StringBuilder ret = new StringBuilder();
    int prevPrint = 0;
    List<AclLine> relevantAclLines = new ArrayList<>();
    for (AclLine aclLine : acl.getLines()) {
      List<ConjunctHeaderSpace> lineRegions = AclToDescribedHeaderSpaces.createPrefixSpaces(aclLine);
      for (ConjunctHeaderSpace lineReg : lineRegions) {
        for (ConjunctHeaderSpace region : regions) {
          if (lineReg.intersection(region).isPresent()) {
            relevantAclLines.add(aclLine);
          }
        }
      }
    }
    Set<String> relevantLineTexts = relevantAclLines.stream()
        .map(AclLine::getName)
        .collect(Collectors.toSet());
    if (lastAclLines == null) {
      lastAclLines = new ArrayList<>();
    }
    Set<String> lastLineTexts = lastAclLines.stream()
        .map(AclLine::getName)
        .collect(Collectors.toSet());

    FileBasedStorageDirectoryProvider provider =
        new FileBasedStorageDirectoryProvider(_settings.getStorageBase());

    SnapshotId snapshotId = reference
        ? _batfish.getReferenceSnapshot().getSnapshot()
        : _batfish.getSnapshot().getSnapshot();
    Path path = provider.getSnapshotInputObjectsDir(_settings.getContainer(), snapshotId);
    String name = new ArrayList<>(_pvcae.getFileMap().get(router)).get(0);
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
              ret.append("      ...").append(linesep);
            }
            prevPrint = currLineNum;
            boolean found = false;
            for (String lastAclLine : lastLineTexts) {
              if (currLine.contains(lastAclLine)) {
                found = true;
                ret.append(String.format("*%s", currLine)).append(linesep);
                lastLinesReached++;
                break;
              }
            }
            if (!found) {
              ret.append(String.format("%s", currLine)).append(linesep);
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
        ret.append("*Implicit deny").append(linesep);
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return ret.toString();
  }

  private void printRelevantLinesJuniper(int firstLine, int lastLine, String router,
      IpAccessList acl, Collection<ConjunctHeaderSpace> regions,
      @Nullable List<AclLine> lastAclLines, boolean hasImplicitDeny, boolean printMore) {

    System.out.print(getRelevantLinesJuniper(
        firstLine,
        lastLine,
        router,
        acl,
        regions,
        lastAclLines,
        hasImplicitDeny,
        printMore, false));
  }
}
