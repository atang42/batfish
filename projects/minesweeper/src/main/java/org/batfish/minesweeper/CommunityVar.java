package org.batfish.minesweeper;

import com.google.common.base.MoreObjects;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.datamodel.bgp.community.Community;
import org.batfish.datamodel.routing_policy.expr.LiteralCommunity;

/**
 * Representation of a community variable for the symbolic encoding. Configuration languages allow
 * users match community values using either <b>exact matches</b> or <b>regular expression</b>
 * matches. For example, a regular expression match such as .*:65001 will match any community string
 * that ends with 65001.
 *
 * <p>To encode community semantics, the model introduces a single new boolean variable for every
 * exact match, and two new boolean variables for every regex match. The first variable says whether
 * there is a community value that matches the regex, but is not specified in the configuration
 * (e.g., came from a neighbor). The second variable says if the regex match is successful, which is
 * based on both the communities in the configuration as well as other communities possibly sent by
 * neighbors.
 *
 * @author Ryan Beckett
 */
@ParametersAreNonnullByDefault
public final class CommunityVar implements Comparable<CommunityVar> {

  private static final Comparator<CommunityVar> COMPARATOR =
      Comparator.comparing(CommunityVar::getType)
          .thenComparing(CommunityVar::getRegex)
          .thenComparing(
              CommunityVar::getLiteralValue, Comparator.nullsLast(Comparator.naturalOrder()));

  public enum Type {
    EXACT,
    REGEX,
    CONJUNCT,
    OTHER
  }

  @Nonnull private final Type _type;
  @Nonnull private final String _regex;
  @Nullable private final Community _literalValue;
  @Nullable private final Set<CommunityVar> _conjuncts;

  private CommunityVar(Type type, String regex, @Nullable Community literalValue, @Nullable Set<CommunityVar> conjuncts) {
    _type = type;
    _regex = regex;
    _literalValue = literalValue;
    _conjuncts = conjuncts;
  }

  /** Create a community var of type {@link Type#REGEX} */
  public static CommunityVar from(String regex) {
    return new CommunityVar(Type.REGEX, regex, null, null );
  }

  /**
   * Create a community var of type {@link Type#EXACT} based on a literal {@link Community} value
   */
  public static CommunityVar from(Community literalCommunity) {
    return new CommunityVar(Type.REGEX, "^" + literalCommunity.matchString() + "$", null, null);
  }

  /** Create a community var of type {@link Type#OTHER} based on a REGEX community var. */
  public static CommunityVar other(String regex) {
    return new CommunityVar(Type.REGEX, regex, null, null);
  }

  private static Collection<CommunityVar> flattenCommunityVars(Collection<CommunityVar> conjuncts) {
    Set<CommunityVar> result = new HashSet<>();
    for (CommunityVar var : conjuncts) {
      if (var.getType() == Type.CONJUNCT) {
        assert var.getConjuncts() != null;
        result.addAll(flattenCommunityVars(var.getConjuncts()));
      } else {
        result.add(var);
      }
    }
    return result;
  }

  /** Create a community var of type {@link Type#CONJUNCT} based on other community var */
  public static CommunityVar from(Collection<CommunityVar> conjuncts) {
    StringBuilder builder = new StringBuilder();
    boolean notFirst = false;
    conjuncts = flattenCommunityVars(conjuncts);
    for (CommunityVar comm : conjuncts) {
      if (notFirst) {
        builder.append(" AND ");
      } else {
        notFirst = true;
      }
      builder.append(comm.getRegex());
    }
    return new CommunityVar(Type.CONJUNCT, builder.toString(), null, new TreeSet<>(conjuncts));
  }

  @Nonnull
  public Type getType() {
    return _type;
  }

  @Nonnull
  public String getRegex() {
    return _regex;
  }

  @Nullable
  public Community getLiteralValue() {
    return _literalValue;
  }

  @Nullable
  public Set<CommunityVar> getConjuncts() {
    return _conjuncts;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("type", _type)
        .add("regex", _regex)
        .add("literalValue", _literalValue)
        .toString();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CommunityVar)) {
      return false;
    }
    CommunityVar that = (CommunityVar) o;
    return _type == that._type
        && _regex.equals(that._regex)
        && Objects.equals(_literalValue, that._literalValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_type.ordinal(), _regex, _literalValue);
  }

  @Override
  public int compareTo(CommunityVar that) {
    return COMPARATOR.compare(this, that);
  }
}
