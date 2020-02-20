package org.batfish.minesweeper.policylocalize;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.StaticRoute;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.Row.TypedRowBuilder;
import org.batfish.datamodel.table.TableMetadata;

public class StaticRouteDiff {

  private static final String COL_ROUTE_PREFIX = "Prefix";
  private static final String COL_NODE1 = "Node1";
  private static final String COL_DEST1 = "Destination1";
  private static final String COL_TAG1 = "Tag1";
  private static final String COL_AD1 = "AdminDist1";
  private static final String COL_TEXT1 = "Text1";
  private static final String COL_LINES1 = "Lines1";
  private static final String COL_NODE2 = "Node2";
  private static final String COL_DEST2 = "Destination2";
  private static final String COL_TAG2 = "Tag2";
  private static final String COL_AD2 = "AdminDist2";
  private static final String COL_TEXT2 = "Text2";
  private static final String COL_LINES2 = "Lines2";

  public static final List<ColumnMetadata> COLUMN_METADATA =
      ImmutableList.of(
          new ColumnMetadata(COL_ROUTE_PREFIX, Schema.STRING, "Prefix", true, false),
          new ColumnMetadata(COL_NODE1, Schema.STRING, "Node", true, false),
          new ColumnMetadata(
              COL_DEST1, Schema.STRING, "Destination interface or address", false, false),
          new ColumnMetadata(COL_TAG1, Schema.INTEGER, "Route Tag", false, false),
          new ColumnMetadata(COL_AD1, Schema.INTEGER, "Preference", false, false),
          new ColumnMetadata(COL_TEXT1, Schema.STRING, "Line text", false, false),
          new ColumnMetadata(COL_LINES1, Schema.list(Schema.INTEGER), "Line Numbers", false, false),
          new ColumnMetadata(COL_NODE2, Schema.STRING, "Node", true, false),
          new ColumnMetadata(
              COL_DEST2, Schema.STRING, "Destination interface or address", false, false),
          new ColumnMetadata(COL_TAG2, Schema.INTEGER, "Route Tag", false, false),
          new ColumnMetadata(COL_AD2, Schema.INTEGER, "Preference", false, false),
          new ColumnMetadata(COL_TEXT2, Schema.STRING, "Line text", false, false),
          new ColumnMetadata(COL_LINES2, Schema.list(Schema.INTEGER), "Line Numbers", false, false));

  public static final Map<String, ColumnMetadata> METADATA_MAP =
      TableMetadata.toColumnMap(COLUMN_METADATA);


  @Nonnull private final List<PrefixRange> _ignoredPrefixRanges;

  public StaticRouteDiff(@Nonnull List<PrefixRange> ignoredPrefixes) {
    _ignoredPrefixRanges = ignoredPrefixes;
  }

  @Nonnull
  public List<Row> compareStaticRoutes(String node1, String node2,
      @Nonnull Configuration config1, @Nonnull Configuration config2) {
    List<Row> answer = new ArrayList<>();

    SortedSet<StaticRoute> staticRoutes1 = config1.getDefaultVrf().getStaticRoutes();
    SortedSet<StaticRoute> staticRoutes2 = config2.getDefaultVrf().getStaticRoutes();

    Map<Prefix, List<StaticRoute>> staticRoutesByNetwork1 =
        staticRoutes1.stream().collect(Collectors.groupingBy(StaticRoute::getNetwork));

    Map<Prefix, List<StaticRoute>> staticRoutesByNetwork2 =
        staticRoutes2.stream().collect(Collectors.groupingBy(StaticRoute::getNetwork));

    Set<Prefix> allNetworks = new TreeSet<>(staticRoutesByNetwork1.keySet());
    allNetworks.addAll(staticRoutesByNetwork2.keySet());

    for (Prefix prefix : allNetworks) {

      // Check ignored
      boolean isIgnored = false;
      for (PrefixRange prefixRange : _ignoredPrefixRanges) {
        if (prefixRange.includesPrefixRange(PrefixRange.fromPrefix(prefix))) {
          isIgnored = true;
          break;
        }
      }
      if (isIgnored) {
        continue;
      }

      List<StaticRoute> srList1;
      srList1 = staticRoutesByNetwork1.getOrDefault(prefix, new ArrayList<>());
      List<StaticRoute> srList2;
      srList2 = staticRoutesByNetwork2.getOrDefault(prefix, new ArrayList<>());

      srList1.sort(Comparator.comparingLong(StaticRoute::getAdministrativeCost));
      srList2.sort(Comparator.comparingLong(StaticRoute::getAdministrativeCost));
      if (!checkStaticRoutesEquality(srList1, srList2)) {
        for (int i = 0; i < Integer.max(srList1.size(), srList2.size()); i++) {
          StaticRoute sr1 = null;
          StaticRoute sr2 = null;
          if (i < srList1.size()) {
            sr1 = srList1.get(i);
          }
          if (i < srList2.size()) {
            sr2 = srList2.get(i);
          }
          answer.add(buildRowFromStaticRoutes(prefix, node1, node2, sr1, sr2));
        }
      }
    }
    return answer;
  }

  private boolean checkStaticRoutesEquality(
      @Nullable List<StaticRoute> sr1, @Nullable List<StaticRoute> sr2) {

    if (sr1 == null && sr2 == null) {
      return true;
    }
    if (sr1 == null || sr2 == null || sr1.size() != sr2.size()) {
      return false;
    }
    sr1.sort(Comparator.comparingLong(StaticRoute::getAdministrativeCost));
    sr2.sort(Comparator.comparingLong(StaticRoute::getAdministrativeCost));

    for (int i = 0; i < sr1.size(); i++) {
      if (!sr1.equals(sr2)) {
        return false;
      }
    }
    return true;
  }

  @Nonnull
  private Row buildRowFromStaticRoutes(
      @Nonnull Prefix prefix,
      @Nonnull String node1,
      @Nonnull String node2,
      @Nullable StaticRoute sr1,
      @Nullable StaticRoute sr2) {
    TypedRowBuilder builder = Row.builder(METADATA_MAP);
    builder.put(COL_ROUTE_PREFIX, prefix).put(COL_NODE1, node1).put(COL_NODE2, node2);

    if (sr1 != null) {
      String nextHop;
      if (sr1.getNextHopInterface().equals("dynamic")) {
        nextHop = sr1.getNextHopIp().toString();
      } else {
        nextHop = sr1.getNextHopInterface();
      }
      String lineNumberStr = "";
      if (sr1.getLineNumbers() != null) {
        lineNumberStr =
            sr1.getLineNumbers().stream().map(Object::toString).reduce((a, b) -> a + "\n" + b).get();
      }
      builder
          .put(COL_DEST1, nextHop)
          .put(COL_AD1, sr1.getAdministrativeCost())
          .put(COL_TAG1, sr1.getTag())
          .put(COL_TEXT1, sr1.getText())
          .put(COL_LINES1, sr1.getLineNumbers());
    }
    if (sr2 != null) {
      String nextHop;
      if (sr2.getNextHopInterface().equals("dynamic")) {
        nextHop = sr2.getNextHopIp().toString();
      } else {
        nextHop = sr2.getNextHopInterface();
      }
      String lineNumberStr = "";
      if (sr2.getLineNumbers() != null) {
        lineNumberStr =
            sr2.getLineNumbers().stream().map(Object::toString).reduce((a, b) -> a + "\n" + b).get();
      }
      builder
          .put(COL_DEST2, nextHop)
          .put(COL_AD2, sr2.getAdministrativeCost())
          .put(COL_TAG2, sr2.getTag())
          .put(COL_TEXT2, sr2.getText())
          .put(COL_LINES2, sr2.getLineNumbers());
    }
    return builder.build();
  }

}
