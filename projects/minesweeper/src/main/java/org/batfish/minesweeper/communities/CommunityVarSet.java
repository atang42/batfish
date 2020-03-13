package org.batfish.minesweeper.communities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import javax.annotation.Nonnull;
import org.batfish.minesweeper.CommunityVar;

public class CommunityVarSet {
  @Nonnull private final Set<CommunityVar> _vars;
  private SortedMap<CommunityVar, List<CommunityVar>> _deps;

  public CommunityVarSet(@Nonnull Set<CommunityVar> vars) {
    _vars = new TreeSet<>(vars);
  }

  @Nonnull public Set<CommunityVar> getVars() {
    return _vars;
  }

  @Nonnull public SortedMap<CommunityVar, List<CommunityVar>> getDependencies() {
    if (_deps != null) {
      return _deps;
    }
    _deps = new TreeMap<>();
    // Map community regex matches to Java regex
    Map<CommunityVar, java.util.regex.Pattern> regexes = new HashMap<>();
    for (CommunityVar c : _vars) {
      if (c.getType() == CommunityVar.Type.REGEX) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(c.getRegex());
        regexes.put(c, p);
      }
    }

    for (CommunityVar c1 : _vars) {
      // map exact match to corresponding regexes
      if (c1.getType() == CommunityVar.Type.REGEX) {

        List<CommunityVar> list = new ArrayList<>();
        _deps.put(c1, list);
        java.util.regex.Pattern p = regexes.get(c1);

        for (CommunityVar c2 : _vars) {
          if (c2.getType() == CommunityVar.Type.EXACT) {
            Matcher m = p.matcher(c2.getRegex());
            if (m.find()) {
              list.add(c2);
            }
          }
          if (c2.getType() == CommunityVar.Type.OTHER && c1.getRegex().equals(c2.getRegex())) {
            list.add(c2);
          }
        }
      }
    }
    return _deps;
  }
}
