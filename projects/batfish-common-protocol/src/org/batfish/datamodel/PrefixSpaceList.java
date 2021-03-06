package org.batfish.datamodel;

import java.util.ArrayList;
import java.util.List;

public class PrefixSpaceList {

   public static PrefixSpaceList fromRouteFilter(RouteFilterList rf) {
      PrefixSpaceList list = new PrefixSpaceList();
      List<PrefixSpaceLine> lines = list._lines;
      LineAction currentAction = null;
      PrefixSpaceLine currentLine = null;
      PrefixSpace currentPrefixSpace = new PrefixSpace();
      for (RouteFilterLine rfLine : rf.getLines()) {
         LineAction rflAction = rfLine.getAction();
         if (currentAction != rflAction) {
            currentAction = rflAction;
            currentPrefixSpace = new PrefixSpace();
            currentLine = new PrefixSpaceLine(currentPrefixSpace,
                  currentAction);
            lines.add(currentLine);
         }
         PrefixRange rflRange = new PrefixRange(rfLine.getPrefix(),
               rfLine.getLengthRange());
         currentPrefixSpace.addPrefixRange(rflRange);
      }
      return list;
   }

   private final List<PrefixSpaceLine> _lines;

   public PrefixSpaceList() {
      _lines = new ArrayList<>();
   }

   public LineAction getAction(Prefix prefix) {
      for (PrefixSpaceLine line : _lines) {
         PrefixSpace space = line.getPrefixSpace();
         if (space.containsPrefix(prefix)) {
            return line.getAction();
         }
      }
      return LineAction.REJECT;
   }

   public List<PrefixSpaceLine> getLines() {
      return _lines;
   }

}
