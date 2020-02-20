package org.batfish.minesweeper.policylocalize.resultrepr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.SubRange;
import org.batfish.minesweeper.bdd.BDDRoute;

public class PrefixRangeDAG {

  private static PrefixRange ALL_PREFIXES = new PrefixRange(Prefix.ZERO, new SubRange(0, 32));

  private PrefixRangeNode _start;
  private Set<AbstractPrefixRangeNode> _ends;
  private int _intersections;

  private PrefixRangeDAG() {
    _start = new PrefixRangeNode(ALL_PREFIXES);
    _ends = new HashSet<>();
    _intersections = 0;
  }

  public PrefixRangeNode getStart() {
    return _start;
  }

  public Set<AbstractPrefixRangeNode> getEnds() {
    return _ends;
  }

  public List<IncludedExcludedPrefixRanges> getRangesMatchingBDD(BDD bdd, BDDRoute record) {
    return _start.getRangesMatchingBDD(bdd, record).getFlattenedRanges();
  }

  public int getIntersectionCount() {
    return _intersections;
  }

  /*
  Add prefix range node to subgraph starting at parent.
  Nodes must be added in order by prefix length of the prefix ranges
  */
  private static void addNode(PrefixRangeNode parent, PrefixRangeNode newNode) {
    List<PrefixRangeNode> children = parent.getChildren();
    List<PrefixRangeNode> containingNodes = new ArrayList<>();
    List<PrefixRangeNode> intersectingNodes = new ArrayList<>();

    PrefixRange prefixRange = newNode.getPrefixRange();
    for (PrefixRangeNode node : children) {
      if (node.containsPrefixRange(prefixRange)) {
        containingNodes.add(node);
      } else if (node.getIntersection(prefixRange).isPresent()) {
        intersectingNodes.add(node);
      }
    }

    if (containingNodes.isEmpty()) {
      parent.addChild(newNode);
      newNode.addParent(parent);
    } else {
      for (PrefixRangeNode containingNode : containingNodes) {
        addNode(containingNode, newNode);
      }
    }

    for (PrefixRangeNode intersecting : intersectingNodes) {
      intersecting.addIntersects(newNode);
      newNode.addIntersects(intersecting);
    }
  }

  public static PrefixRangeDAG build(Collection<PrefixRange> ranges) {
    PrefixRangeDAG graph = new PrefixRangeDAG();
    List<PrefixRange> prefixRangeList = new ArrayList<>(ranges);
    Map<PrefixRange, PrefixRangeNode> nodeMap = new HashMap<>();
    nodeMap.put(ALL_PREFIXES, graph._start);

    // Add all prefix ranges as nodes with parent, children, intersect relationships
    // Sorting ensures that if A contains B then A comes before B
    prefixRangeList.sort(
        Comparator.comparing((PrefixRange pr) -> pr.getPrefix().getPrefixLength())
            .thenComparing((PrefixRange pr) -> pr.getLengthRange().getStart())
            .thenComparing((PrefixRange pr) -> -pr.getLengthRange().getEnd()));
    for (PrefixRange range : prefixRangeList) {
      if (!range.equals(ALL_PREFIXES)) {
        PrefixRangeNode newNode = new PrefixRangeNode(range);
        nodeMap.put(range, newNode);
        addNode(graph.getStart(), newNode);
      }
    }

    // Mark ends and create generated nodes based on remainders
    int intersections = 0;
    for (PrefixRangeNode node : nodeMap.values()) {
      intersections += node.getIntersects().size();
      if (node.getChildren().isEmpty() && node.getIntersects().isEmpty()) {
        graph._ends.add(node);
      } else {
        List<PrefixRange> excluded =
            Stream.concat(
                    node.getChildren().stream().map(PrefixRangeNode::getPrefixRange),
                    node.getIntersects().stream().map(PrefixRangeNode::getPrefixRange))
                .collect(Collectors.toList());
        RemainderNode remainderNode = new RemainderNode(node, excluded);
        node.setRemainder(remainderNode);
        graph.getEnds().add(remainderNode);
      }
    }
    // Avoid double counting intersections
    intersections /= 2;
    graph._intersections = intersections;

    // TODO: Create intersection nodes based on intersections

    return graph;
  }
}
