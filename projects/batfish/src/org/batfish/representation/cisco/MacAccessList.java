package org.batfish.representation.cisco;

import java.util.ArrayList;
import java.util.List;

import org.batfish.common.util.ComparableStructure;

public class MacAccessList extends ComparableStructure<String> {

   private static final long serialVersionUID = 1L;

   private final int _definitionLine;

   private List<MacAccessListLine> _lines;

   public MacAccessList(String name, int definitionLine) {
      super(name);
      _definitionLine = definitionLine;
      _lines = new ArrayList<>();
   }

   public int getDefinitionLine() {
      return _definitionLine;
   }

   public List<MacAccessListLine> getLines() {
      return _lines;
   }

}
