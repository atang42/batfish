package org.batfish.minesweeper.policylocalize.resultrepr;

import java.util.*;

import net.sf.javabdd.BDD;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.PrefixRange;
import org.batfish.datamodel.SubRange;
import org.batfish.minesweeper.bdd.BDDRoute;

public class PrefixRangeDAG {

  private static PrefixRange ALL_PREFIXES = new PrefixRange(Prefix.ZERO, new SubRange(0, 32));

  private PrefixRangeNode _start;
  private int _intersections;
  Map<PrefixRange, PrefixRangeNode> _nodeMap = new HashMap<>();

  private PrefixRangeDAG() {
    _start = new PrefixRangeNode(ALL_PREFIXES, false);
    _intersections = 0;
  }

  public PrefixRangeNode getStart() {
    return _start;
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
  private static void addNode(PrefixRangeNode parent, PrefixRangeNode newNode, PrefixRangeDAG graph) {
    List<PrefixRangeNode> children = parent.getChildren();
    List<PrefixRangeNode> containingNodes = new ArrayList<>();
    List<PrefixRangeNode> containedNodes = new ArrayList<>();
    List<PrefixRangeNode> intersectingNodes = new ArrayList<>();
    if (parent == newNode) {
      // Don't insert node as child of self
      return;
    }
    PrefixRange prefixRange = newNode.getPrefixRange();
    for (PrefixRangeNode node : children) {
      if (node.containsPrefixRange(prefixRange)) {
        containingNodes.add(node);
      } else if (newNode.containsPrefixRange(node.getPrefixRange())) {
        containedNodes.add(node);
      }
      else if (node.getIntersection(prefixRange).isPresent()) {
        intersectingNodes.add(node);
      }
    }

    if (containingNodes.isEmpty()) {
      parent.addChild(newNode);
      newNode.addParent(parent);
    } else {
      for (PrefixRangeNode containingNode : containingNodes) {
        addNode(containingNode, newNode, graph);
      }
    }

    for (PrefixRangeNode contained : containedNodes) {
      contained.removeParent(parent);
      parent.removeChild(contained);
      newNode.addChild(contained);
      contained.addParent(contained);
    }

    for (PrefixRangeNode intersecting : intersectingNodes) {
      intersecting.addIntersects(newNode);
      newNode.addIntersects(intersecting);
      Optional<PrefixRange> intersection = newNode.getIntersection(intersecting.getPrefixRange());
      assert intersection.isPresent();
      if (!graph._nodeMap.containsKey(intersection.get())) {
        PrefixRangeNode intersectionNode = new PrefixRangeNode(intersection.get(), true);
        addNode(parent, intersectionNode, graph);
      }
    }
  }

  public static PrefixRangeDAG build(Collection<PrefixRange> ranges) {
    PrefixRangeDAG graph = new PrefixRangeDAG();
    List<PrefixRange> prefixRangeList = new ArrayList<>(ranges);
    graph._nodeMap.put(ALL_PREFIXES, graph._start);

    // Add all prefix ranges as nodes with parent, children, intersect relationships
    // Sorting ensures that if A contains B then A comes before B
    prefixRangeList.sort(
        Comparator.comparing((PrefixRange pr) -> pr.getPrefix().getPrefixLength())
            .thenComparing((PrefixRange pr) -> pr.getLengthRange().getStart())
            .thenComparing((PrefixRange pr) -> -pr.getLengthRange().getEnd()));
    for (PrefixRange range : prefixRangeList) {
      if (!range.equals(ALL_PREFIXES)) {
        PrefixRangeNode newNode = new PrefixRangeNode(range, false);
        if (graph._nodeMap.containsKey(range)) {
          continue;
        }
        graph._nodeMap.put(range, newNode);
        addNode(graph.getStart(), newNode, graph);
      }
    }

    // Mark ends and create generated nodes based on remainders
    int intersections = 0;
    for (PrefixRangeNode node : graph._nodeMap.values()) {
      intersections += node.getIntersects().size();
    }
    // Avoid double counting intersections
    intersections /= 2;
    graph._intersections = intersections;

    return graph;
  }
}
