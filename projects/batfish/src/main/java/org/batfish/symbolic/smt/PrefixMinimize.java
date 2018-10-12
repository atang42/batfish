package org.batfish.symbolic.smt;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.symbolic.smt.PrefixMinimize.PrefixReprOperator.OpType;

public class PrefixMinimize {

  public abstract static class PrefixRepr {
    public abstract int getTermCount();
    public abstract boolean containsPrefix(Prefix pfx);
    public abstract Set<Prefix> getPrefixes();
  }

  public static class PrefixReprEmpty extends PrefixRepr {
    @Override public int getTermCount() {
      return 0;
    }

    @Override public boolean containsPrefix(Prefix pfx) {
      return false;
    }

    @Override public Set<Prefix> getPrefixes() {
      return new TreeSet<Prefix>() {
      };
    }

    @Override public String toString() {
      return "Empty";
    }
  }

  public static class PrefixReprTerm extends PrefixRepr {
    private Prefix prefix;

    public PrefixReprTerm(Prefix pfx) {
      super();
      prefix = pfx;
    }

    public Prefix getPrefix() {
      return prefix;
    }

    @Override public int getTermCount() {
      return 1;
    }

    @Override public boolean containsPrefix(Prefix pfx) {
      return pfx.equals(prefix);
    }

    @Override public Set<Prefix> getPrefixes() {
      TreeSet<Prefix> result = new TreeSet<>();
      result.add(prefix);
      return result;
    }

    @Override public String toString() {
      return prefix.toString();
    }
  }

  public static class PrefixReprOperator extends PrefixRepr {

    public enum OpType {UNION, SUBTRACT}

    private PrefixRepr left;
    private PrefixRepr right;
    private OpType operator;
    private int termCount;
    private Set<Prefix> prefixSet;

    public PrefixReprOperator(PrefixRepr l, PrefixRepr r, OpType op) {
      left = l;
      right = r;
      operator = op;
      termCount = l.getTermCount() + r.getTermCount();
      prefixSet = new TreeSet<>(left.getPrefixes());
      prefixSet.addAll(right.getPrefixes());
    }

    public PrefixRepr getLeft() {
      return left;
    }

    public PrefixRepr getRight() {
      return right;
    }

    public OpType getOperator() {
      return operator;
    }

    @Override public int getTermCount() {
      return termCount;
    }

    @Override public boolean containsPrefix(Prefix pfx) {
      return left.containsPrefix(pfx) || right.containsPrefix(pfx);
    }

    @Override public Set<Prefix> getPrefixes() {
      return prefixSet;
    }

    @Override public String toString() {
      String opString = operator == OpType.UNION ? " + " : " - ";
      return "(" + left + opString + right + ")";
    }
  }

  private static PrefixRepr unionPrefixes(PrefixRepr left, PrefixRepr right) {
    if (left instanceof PrefixReprEmpty)
      return right;
    if (right instanceof PrefixReprEmpty)
      return left;
    return new PrefixReprOperator(left, right, OpType.UNION);
  }

  private static PrefixRepr subtractPrefixes(PrefixRepr left, PrefixRepr right) {
    assert(!(left instanceof PrefixReprEmpty));
    if (right instanceof PrefixReprEmpty)
      return left;
    return new PrefixReprOperator(left, right, OpType.SUBTRACT);
  }

  private Prefix getNeighborPfx(Prefix pfx) {
    Long bits = pfx.getStartIp().asLong();
    Long flippedBit = 1L << (32 - pfx.getPrefixLength());
    return new Prefix(new Ip(bits ^ flippedBit), pfx.getPrefixLength());
  }

  private PrefixRepr getBestRepr(PrefixRepr a, PrefixRepr b) {
    if(a.getTermCount() <= b.getTermCount()) {
      return a;
    }
    return b;
  }

  private PrefixRepr mergeNeighbors(PrefixRepr left, PrefixRepr right, Prefix parent) {
    PrefixRepr leftSubtracted;
    PrefixRepr rightSubtracted;

    if (left instanceof PrefixReprOperator) {
      PrefixReprOperator leftOp = (PrefixReprOperator)left;
      assert(leftOp.getLeft() instanceof PrefixReprTerm);
      assert(parent.containsPrefix(((PrefixReprTerm) leftOp.getLeft()).getPrefix()));
      leftSubtracted = leftOp.getRight();
    } else {
      leftSubtracted = new PrefixReprEmpty();
    }
    if (right instanceof PrefixReprOperator) {
      PrefixReprOperator rightOp = (PrefixReprOperator)right;
      assert(rightOp.getLeft() instanceof PrefixReprTerm);
      assert(parent.containsPrefix(((PrefixReprTerm) rightOp.getLeft()).getPrefix()));
      rightSubtracted = rightOp.getRight();
    } else {
      rightSubtracted = new PrefixReprEmpty();
    }

    PrefixRepr subtractedUnion = unionPrefixes(leftSubtracted, rightSubtracted);
    return subtractPrefixes(new PrefixReprTerm(parent), subtractedUnion);
  }

  public void minimizePrefixWithNesting(Set<Prefix> prefixes, Set<Prefix> complement) {
    Map<Prefix, PrefixRepr> posBest = new HashMap<>();
    Map<Prefix, PrefixRepr> posIncl = new HashMap<>();
    Map<Prefix, PrefixRepr> negBest = new HashMap<>();
    Map<Prefix, PrefixRepr> negIncl = new HashMap<>();

    // Used to sort prefixes from longest to shortest
    Comparator<Prefix> pfxLongToShort = (prefix1, prefix2) -> {
      int ret = Integer.compare(prefix1.getPrefixLength(), prefix2.getPrefixLength());
      if (ret != 0) {
        return -ret;
      }
      return prefix1.getStartIp().compareTo(prefix2.getStartIp());
    };

    PriorityQueue<Prefix> prefixQueue = new PriorityQueue<>(prefixes.size(), pfxLongToShort);
    Set<Prefix> visited = new HashSet<>();

    for (Prefix pfx : prefixes) {
      posBest.put(pfx, new PrefixReprTerm(pfx));
      posIncl.put(pfx, new PrefixReprTerm(pfx));
      negBest.put(pfx, new PrefixReprEmpty());
      negIncl.put(pfx, subtractPrefixes(new PrefixReprTerm(pfx), new PrefixReprTerm(pfx)));

      if (pfx.getPrefixLength() > 0) {
        prefixQueue.add(pfx);
      }
    }

    for (Prefix pfx : complement) {
      posBest.put(pfx, new PrefixReprEmpty());
      posIncl.put(pfx, subtractPrefixes(new PrefixReprTerm(pfx), new PrefixReprTerm(pfx)));
      negBest.put(pfx, new PrefixReprTerm(pfx));
      negIncl.put(pfx, new PrefixReprTerm(pfx));
    }

    while (!prefixQueue.isEmpty()) {
      Prefix current = prefixQueue.poll();
      Prefix neighbor = getNeighborPfx(current);
      Prefix parent = new Prefix(current.getStartIp(), current.getPrefixLength() - 1);

      if (visited.contains(parent)) {
        continue;
      }

      PrefixRepr posIncl1 = subtractPrefixes(new PrefixReprTerm(parent), unionPrefixes(negBest.get(current), negBest.get(neighbor)));
      PrefixRepr posIncl2 = mergeNeighbors(posIncl.get(current), posIncl.get(neighbor), parent);
      posIncl.put(parent, getBestRepr(posIncl1, posIncl2));

      PrefixRepr negIncl1 = subtractPrefixes(new PrefixReprTerm(parent), unionPrefixes(posBest.get(current), posBest.get(neighbor)));
      PrefixRepr negIncl2 = mergeNeighbors(negIncl.get(current), negIncl.get(neighbor), parent);
      negIncl.put(parent, getBestRepr(negIncl1, negIncl2));

      posBest.put(parent, getBestRepr(unionPrefixes(posBest.get(current), posBest.get(neighbor)), posIncl.get(parent)));
      negBest.put(parent, getBestRepr(unionPrefixes(negBest.get(current), negBest.get(neighbor)), negIncl.get(parent)));

      if (parent.getPrefixLength() > 0) {
        prefixQueue.add(parent);
      }
      visited.add(parent);
    }
    Prefix root = Prefix.parse("0.0.0.0/0");
    System.out.println(posBest.get(root));
    System.out.println(posBest.get(root).getPrefixes());

  }

}
