package org.batfish.datamodel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.batfish.common.util.ComparableStructure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaDescription;

@JsonSchemaDescription("An AsPathAccessList is used to filter e/iBGP routes according to their AS-path attribute.")
public final class AsPathAccessList extends ComparableStructure<String>
      implements Serializable {

   private static final String LINES_VAR = "lines";

   private static final long serialVersionUID = 1L;

   private transient Set<AsPath> _deniedCache;

   private final List<AsPathAccessListLine> _lines;

   private transient Set<AsPath> _permittedCache;

   public AsPathAccessList(String name) {
      super(name);
      _lines = new ArrayList<>();
   }

   @JsonCreator
   public AsPathAccessList(@JsonProperty(NAME_VAR) String name,
         @JsonProperty(LINES_VAR) List<AsPathAccessListLine> lines) {
      super(name);
      _lines = lines;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      AsPathAccessList other = (AsPathAccessList) obj;
      return other._lines.equals(_lines);
   }

   @JsonProperty(LINES_VAR)
   @JsonPropertyDescription("The list of lines against which a route's AS-path will be checked in order.")
   public List<AsPathAccessListLine> getLines() {
      return _lines;
   }

   private boolean newPermits(AsPath asPath) {
      boolean accept = false;
      for (AsPathAccessListLine line : _lines) {
         String regex = line.getRegex();
         Pattern p = Pattern.compile(regex);
         String asPathString = asPath.getAsPathString();
         Matcher matcher = p.matcher(asPathString);
         boolean match = matcher.find();
         if (match) {
            accept = line.getAction() == LineAction.ACCEPT;
            break;
         }
      }
      if (accept) {
         _permittedCache.add(asPath);
      }
      else {
         _deniedCache.add(asPath);
      }
      return accept;
   }

   public boolean permits(AsPath asPath) {
      if (_deniedCache.contains(asPath)) {
         return false;
      }
      else if (_permittedCache.contains(asPath)) {
         return true;
      }
      return newPermits(asPath);
   }

   private void readObject(ObjectInputStream in)
         throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      _deniedCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
      _permittedCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
   }

}
