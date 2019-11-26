package org.batfish.representation.juniper;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Represents all {@link PsFrom} statements in a single {@link PsTerm} */
public final class PsFroms implements Serializable {

  private boolean _atLeastOneFrom = false;

  private final Set<PsFromAsPath> _fromAsPaths;
  private PsFromColor _fromColor;
  private final Set<PsFromCommunity> _fromCommunities;
  private PsFromFamily _fromFamily;
  private PsFromInstance _fromInstance;
  private final Set<PsFromInterface> _fromInterfaces;
  private PsFromLocalPreference _fromLocalPreference;
  private PsFromMetric _fromMetric;
  private final Set<PsFromPolicyStatement> _fromPolicyStatements;
  private final Set<PsFromPolicyStatementConjunction> _fromPolicyStatementConjunctions;
  private final Set<PsFromPrefixList> _fromPrefixLists;
  private final Set<PsFromPrefixListFilterLonger> _fromPrefixListFilterLongers;
  private final Set<PsFromPrefixListFilterOrLonger> _fromPrefixListFilterOrLongers;
  private final Set<PsFromProtocol> _fromProtocols;
  private final Set<PsFromRouteFilter> _fromRouteFilters;
  private final Set<PsFromTag> _fromTags;
  private PsFromUnsupported _fromUnsupported;
  private String _text;

  PsFroms() {
    _fromAsPaths = new LinkedHashSet<>();
    _fromCommunities = new LinkedHashSet<>();
    _fromInterfaces = new LinkedHashSet<>();
    _fromPolicyStatements = new LinkedHashSet<>();
    _fromPolicyStatementConjunctions = new LinkedHashSet<>();
    _fromPrefixLists = new LinkedHashSet<>();
    _fromPrefixListFilterLongers = new LinkedHashSet<>();
    _fromPrefixListFilterOrLongers = new LinkedHashSet<>();
    _fromProtocols = new LinkedHashSet<>();
    _fromRouteFilters = new LinkedHashSet<>();
    _fromTags = new LinkedHashSet<>();
    _text = "";
  }

  public void addFromAsPath(@Nonnull PsFromAsPath fromAsPath) {
    _atLeastOneFrom = true;
    _fromAsPaths.add(fromAsPath);
    _text += fromAsPath.getText() + System.lineSeparator();
  }

  public void addFromCommunity(@Nonnull PsFromCommunity fromCommunity) {
    _atLeastOneFrom = true;
    _fromCommunities.add(fromCommunity);
    _text += fromCommunity.getText() + System.lineSeparator();
  }

  public void addFromInterface(@Nonnull PsFromInterface fromInterface) {
    _atLeastOneFrom = true;
    _fromInterfaces.add(fromInterface);
    _text += fromInterface.getText() + System.lineSeparator();
  }

  public void addFromPolicyStatement(@Nonnull PsFromPolicyStatement fromPolicyStatement) {
    _atLeastOneFrom = true;
    _fromPolicyStatements.add(fromPolicyStatement);
    _text += fromPolicyStatement.getText() + System.lineSeparator();
  }

  public void addFromPolicyStatementConjunction(
      @Nonnull PsFromPolicyStatementConjunction fromPolicyStatementConjunction) {
    _atLeastOneFrom = true;
    _fromPolicyStatementConjunctions.add(fromPolicyStatementConjunction);
    _text += fromPolicyStatementConjunction.getText() + System.lineSeparator();
  }

  public void addFromPrefixList(@Nonnull PsFromPrefixList fromPrefixList) {
    _atLeastOneFrom = true;
    _fromPrefixLists.add(fromPrefixList);
    _text += fromPrefixList.getText() + System.lineSeparator();
  }

  public void addFromPrefixListFilterLonger(
      @Nonnull PsFromPrefixListFilterLonger fromPrefixListFilterLonger) {
    _atLeastOneFrom = true;
    _fromPrefixListFilterLongers.add(fromPrefixListFilterLonger);
    _text += fromPrefixListFilterLonger.getText() + System.lineSeparator();
  }

  public void addFromPrefixListFilterOrLonger(
      @Nonnull PsFromPrefixListFilterOrLonger fromPrefixListFilterOrLonger) {
    _atLeastOneFrom = true;
    _fromPrefixListFilterOrLongers.add(fromPrefixListFilterOrLonger);
    _text += fromPrefixListFilterOrLonger.getText() + System.lineSeparator();
  }

  public void addFromProtocol(@Nonnull PsFromProtocol fromProtocol) {
    _atLeastOneFrom = true;
    _fromProtocols.add(fromProtocol);
    _text += fromProtocol.getText() + System.lineSeparator();
  }

  public void addFromRouteFilter(@Nonnull PsFromRouteFilter fromRouteFilter) {
    _atLeastOneFrom = true;
    _fromRouteFilters.add(fromRouteFilter);
    _text += fromRouteFilter.getText() + System.lineSeparator();
  }

  public void addFromTag(@Nonnull PsFromTag fromTag) {
    _atLeastOneFrom = true;
    _fromTags.add(fromTag);
    _text += fromTag.getText() + System.lineSeparator();
  }

  @Nonnull
  Set<PsFromAsPath> getFromAsPaths() {
    return _fromAsPaths;
  }

  @Nullable
  PsFromColor getFromColor() {
    return _fromColor;
  }

  @Nonnull
  Set<PsFromCommunity> getFromCommunities() {
    return _fromCommunities;
  }

  @Nullable
  PsFromFamily getFromFamily() {
    return _fromFamily;
  }

  PsFromInstance getFromInstance() {
    return _fromInstance;
  }

  @Nonnull
  Set<PsFromInterface> getFromInterfaces() {
    return _fromInterfaces;
  }

  @Nullable
  PsFromLocalPreference getFromLocalPreference() {
    return _fromLocalPreference;
  }

  @Nullable
  PsFromMetric getFromMetric() {
    return _fromMetric;
  }

  @Nonnull
  Set<PsFromPolicyStatement> getFromPolicyStatements() {
    return _fromPolicyStatements;
  }

  @Nonnull
  Set<PsFromPolicyStatementConjunction> getFromPolicyStatementConjunctions() {
    return _fromPolicyStatementConjunctions;
  }

  @Nonnull
  Set<PsFromPrefixList> getFromPrefixLists() {
    return _fromPrefixLists;
  }

  @Nonnull
  Set<PsFromPrefixListFilterLonger> getFromPrefixListFilterLongers() {
    return _fromPrefixListFilterLongers;
  }

  @Nonnull
  Set<PsFromPrefixListFilterOrLonger> getFromPrefixListFilterOrLongers() {
    return _fromPrefixListFilterOrLongers;
  }

  @Nonnull
  Set<PsFromProtocol> getFromProtocols() {
    return _fromProtocols;
  }

  @Nonnull
  Set<PsFromRouteFilter> getFromRouteFilters() {
    return _fromRouteFilters;
  }

  @Nonnull
  Set<PsFromTag> getFromTags() {
    return _fromTags;
  }

  @Nullable
  PsFromUnsupported getFromUnsupported() {
    return _fromUnsupported;
  }

  boolean hasAtLeastOneFrom() {
    return _atLeastOneFrom;
  }

  public void setFromColor(@Nonnull PsFromColor fromColor) {
    _atLeastOneFrom = true;
    _fromColor = fromColor;
    _text += fromColor.getText() + System.lineSeparator();
  }

  public void setFromFamily(@Nonnull PsFromFamily fromFamily) {
    _atLeastOneFrom = true;
    _fromFamily = fromFamily;
    _text += fromFamily.getText() + System.lineSeparator();
  }

  public void setFromInstance(@Nonnull PsFromInstance fromInstance) {
    _atLeastOneFrom = true;
    _fromInstance = fromInstance;
    _text += fromInstance.getText() + System.lineSeparator();
  }

  public void setFromLocalPreference(@Nonnull PsFromLocalPreference fromLocalPreference) {
    _atLeastOneFrom = true;
    _fromLocalPreference = fromLocalPreference;
    _text += fromLocalPreference.getText() + System.lineSeparator();
  }

  public void setFromMetric(@Nonnull PsFromMetric fromMetric) {
    _atLeastOneFrom = true;
    _fromMetric = fromMetric;
    _text += fromMetric.getText() + System.lineSeparator();
  }

  public void setFromUnsupported(@Nonnull PsFromUnsupported fromUnsupported) {
    _atLeastOneFrom = true;
    _fromUnsupported = fromUnsupported;
    _text += fromUnsupported.getText() + System.lineSeparator();
  }

  public String getText() {
    return _text;
  }
}
