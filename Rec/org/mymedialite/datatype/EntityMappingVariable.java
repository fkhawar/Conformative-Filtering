package org.mymedialite.datatype;

import org.latlab.util.Variable;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.mymedialite.data.IEntityMapping;

public class EntityMappingVariable {
	/**
	   * Contains the mapping from the original (external) IDs to the internal IDs.
	   */
	  public HashMap<Variable, Integer> original_to_internal = new HashMap<Variable, Integer>();

	  /**
	   * Contains the mapping from the internal IDs to the original (external) IDs.
	   */
	  public HashMap<Integer, Variable> internal_to_original = new HashMap<Integer, Variable>();

	  /**
	   * Get all the original (external) entity IDs
	   * @return all original (external) entity IDs
	   */
	  
	  public Collection<Variable> originalIDs() {
	    return original_to_internal.keySet();
	  }

	  /**
	   * Get all the internal entity IDs.
	   * @return all internal entity IDs
	   */
	 
	  public Collection<Integer> internalIDs() {
	    return internal_to_original.keySet();
	  }

	  /**
	   * Get original (external) ID of a given entity.
	   * @param internal_id the internal ID of the entity
	   * @return the original (external) ID of the entity
	   * @throws if the given internal ID is unknown
	   */
	  
	  public Variable toOriginalID(int internal_id) throws IllegalArgumentException {
	    Variable original_id = internal_to_original.get(internal_id);   
	    if (original_id != null) {
	      return original_id;
	    } else {
	      throw new IllegalArgumentException("Unknown internal ID: " + internal_id);
	    }
	  }
	  
	  public Variable toOriginalIDn(int internal_id) throws IllegalArgumentException {
		    Variable original_id = internal_to_original.get(internal_id);   
		    if (original_id != null) {
		      return original_id;
		    } else {
		      return null;
		    }
		  }
	  
	  /**
	   * Get internal ID of a given entity.
	   * If the given external ID is unknown, create a new internal ID for it and store the mapping.
	   * @param original_id the original (external) ID of the entity
	   * @return the internal ID of the entity
	   */
	  
	  public Integer toInternalID(Variable original_id) {
	    Integer internal_id = original_to_internal.get(original_id);
	    if (internal_id != null) {
	      return internal_id;
	    } else {
	      internal_id = original_to_internal.size();
	      original_to_internal.put(original_id, internal_id);
	      internal_to_original.put(internal_id, original_id);
	      return internal_id;
	    }
	  }

	  /**
	   * Get the original (external) IDs of a list of given entities.
	   * @param internal_id_list the list of internal IDs
	   * @return the list of original (external) IDs
	   */
	  
	  public List<Variable> toOriginalIDn(IntCollection internal_id_list) {
	    ArrayList<Variable> result = new ArrayList<Variable>(internal_id_list.size());
	    for (Integer id : internal_id_list) {
	    	Variable IId = toOriginalIDn(id);
	    	if(IId != null )
	    		result.add(IId);
	    }
	    return result;
	  }
	  
	  public Set<Variable> toOriginalIDSet(IntCollection internal_id_list) {
		    Set<Variable> result = new HashSet<Variable>(internal_id_list.size());
		    for (Integer id : internal_id_list) {
		    	Variable IId = toOriginalIDn(id);
		    	if(IId != null )
		    		result.add(IId);
		    }
		    return result;
		  }
	  
	  public List<Variable> toOriginalID(IntCollection internal_id_list) {
		    ArrayList<Variable> result = new ArrayList<Variable>(internal_id_list.size());
		    for (Integer id : internal_id_list) {
		      result.add(toOriginalID(id));
		    }
		    return result;
		  }

	  /**
	   *  Get the internal IDs of a list of given entities.
	   *  @param original_id_list the list of original (external) IDs
	   *  @return a list of internal IDs
	   */
	  
	  public IntList toInternalID(List<Variable> original_id_list) {
	    IntList result = new IntArrayList(original_id_list.size());
	    for (Variable id : original_id_list) {
	      result.add(toInternalID(id));
	    }
	    return result;
	  }
	  
	  /**
	   * Save this entity mapping.
	   * @param writer
	   * @throws IOException
	   */
	  public void saveMapping(PrintWriter writer) throws IOException {
	    writer.println(original_to_internal.size());
	    for(Entry<Variable, Integer> entry : original_to_internal.entrySet()) {
	      writer.println(entry.getKey() + " " + entry.getValue());
	      if(writer.checkError()) throw new IOException("Error writing model file");
	    }
	  }
	  
	  public void clear(){
		  original_to_internal.clear();
		  internal_to_original.clear();
	  }
	
}
