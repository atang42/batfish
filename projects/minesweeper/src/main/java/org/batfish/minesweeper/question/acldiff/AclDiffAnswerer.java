package org.batfish.minesweeper.question.acldiff;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import org.batfish.common.Answerer;
import org.batfish.common.NetworkSnapshot;
import org.batfish.common.plugin.IBatfish;
import org.batfish.datamodel.answers.Schema;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.table.ColumnMetadata;
import org.batfish.datamodel.table.Row;
import org.batfish.datamodel.table.Row.TypedRowBuilder;
import org.batfish.datamodel.table.TableAnswerElement;
import org.batfish.datamodel.table.TableMetadata;
import org.batfish.minesweeper.policylocalize.acldiff.BddDiff;
import org.batfish.minesweeper.policylocalize.acldiff.LineDifference;
import org.batfish.representation.cisco_nxos.BgpVrfIpv4AddressFamilyConfiguration.Network;

public final class AclDiffAnswerer extends Answerer {

  private static final String COL_INTERFACE = "Interface";
  private static final String COL_ROUTE_INCLUDED_PREFIXES = "Included_Packets";
  private static final String COL_ROUTE_EXCLUDED_PREFIXES = "Excluded_Packets";
  private static final String COL_NODE1 = "Node1";
  private static final String COL_FILTER1 = "Filter1";
  private static final String COL_TEXT1 = "Text1";
  private static final String COL_ACTION1 = "Action1";
  private static final String COL_NODE2 = "Node2";
  private static final String COL_FILTER2 = "Filter2";
  private static final String COL_TEXT2 = "Text2";
  private static final String COL_ACTION2 = "Action2";

  private static final List<ColumnMetadata> COLUMN_METADATA =
      ImmutableList.of(
          new ColumnMetadata(COL_ROUTE_INCLUDED_PREFIXES, Schema.STRING, "Included Prefixes", true, false),
          new ColumnMetadata(COL_ROUTE_EXCLUDED_PREFIXES, Schema.STRING, "Excluded Prefixes", true, false),
          new ColumnMetadata(COL_NODE1, Schema.STRING, "Node", true, false),
          new ColumnMetadata(COL_FILTER1, Schema.STRING, "Filter name", true, false),
          new ColumnMetadata(COL_TEXT1, Schema.STRING, "Line text", true, false),
          new ColumnMetadata(
              COL_ACTION1,
              Schema.STRING,
              "Action performed by the line (e.g., PERMIT or DENY)",
              true,
              false),
          new ColumnMetadata(COL_NODE2, Schema.STRING, "Node", true, false),
          new ColumnMetadata(COL_FILTER2, Schema.STRING, "Filter name", true, false),
          new ColumnMetadata(COL_TEXT2, Schema.STRING, "Line text", true, false),
          new ColumnMetadata(
              COL_ACTION2,
              Schema.STRING,
              "Action performed by the line (e.g., PERMIT or DENY)",
              true,
              false));

  private static final Map<String, ColumnMetadata> METADATA_MAP =
      TableMetadata.toColumnMap(COLUMN_METADATA);

  private static Row lineDifferenceToRow(LineDifference ld) {

    StringBuilder included = new StringBuilder();
    for (String s : ld.getDifference()) {
      included.append(s).append("\n");
    }
    StringBuilder excluded = new StringBuilder();
    for (String s : ld.getDiffSub()) {
      excluded.append(s).append("\n\n");
    }

    TypedRowBuilder builder =
        Row.builder(METADATA_MAP)
            .put(COL_NODE1, ld.getRouter1())
            .put(COL_NODE2, ld.getRouter2())
            .put(COL_FILTER1, ld.getFilter1())
            .put(COL_FILTER2, ld.getFilter2())
            .put(COL_ROUTE_INCLUDED_PREFIXES, included)
            .put(COL_ROUTE_EXCLUDED_PREFIXES, excluded)
            .put(COL_TEXT1, ld.getSnippet1())
            .put(COL_TEXT2, ld.getSnippet2())
            .put(COL_ACTION1, ld.getAction1())
            .put(COL_ACTION2, ld.getAction2());
    return builder.build();
  }

  AclDiffAnswerer(Question question, IBatfish batfish) {
    super(question, batfish);
  }

  @Override
  public TableAnswerElement answer(NetworkSnapshot snapshot) {
    AclDiffQuestion question = (AclDiffQuestion) _question;
    NodesSpecifier regex = question.getNodeRegex();
    String aclRegex = question.getAclRegex();
    boolean printMore = question.getPrintMore();
    BddDiff diffChecker = new BddDiff(_batfish, regex, aclRegex, printMore);
    SortedSet<LineDifference> diffs =
        diffChecker.findDiffWithLines();
    TableAnswerElement result =
        new TableAnswerElement(new TableMetadata(COLUMN_METADATA));
    for (LineDifference diff : diffs) {
      result.addRow(lineDifferenceToRow(diff));
    }
    return result;
  }
}
