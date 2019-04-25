package FastHLTA;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.latlab.learner.CliqueTreePropagationGroup;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.Utils;
import org.latlab.util.Variable;
import org.latlab.util.DataSet.DataCase;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * 
 * @author fkhawar
 *
 */
public class ParallelForestHardAssignment {
	
	private static ForkJoinPool threadPool = null;
	
	/**
	 * stores the global index of the island varibles in ascending order of their local index 
	 */
	protected  HashMap<Integer, IntList> LatentVariableToIslandGlobalIndex;
	
	/**
	 * map from the index of latent variable in LatentVars to the value. The value is a map with key as the array that contains the unique states for this latent variable and value as the hard-assigned value of the latent variable for this datacase
	 */
	protected  HashMap<Integer,HashMap<IntList,Integer>> LatentVariableToDatasetMap;
	
	public ParallelForestHardAssignment() {
		
	}
	
	private static DataSet HardAssignedData=null;
	
	public void ParallelHardAssignmentCompute(DataSet dataSet, Map<Variable, LTM> _hierarchies) {
		
		Variable[] LatentVars = _hierarchies.keySet().toArray(new Variable[0]); // These will be the variable of the new DataSet
		
		//HashMap<Integer,HashMap<IntList,Integer>> LatentVariableToDatasetMap = new HashMap<Integer,HashMap<IntList,Integer>>(); // map from the index of latent variable in LatentVars to the value. The value is a map with key as the array that contains the unique states for this latent variable an dvalue as the hardassigned value of the latent varible for this datacse
		
		
		HashMap<Variable,CliqueTreePropagationGroup> rootToTreeCtpsMap = new HashMap<Variable,CliqueTreePropagationGroup>();
		
		// create a ctp for each island X parallelism
		for (Variable latent : LatentVars) {
			
			// for each latent variable get its corresponding tree and make a group based on parallelism
			CliqueTreePropagationGroup ctps = 
					CliqueTreePropagationGroup.constructFromModel(_hierarchies.get(latent),
							Runtime.getRuntime().availableProcessors());
			
			rootToTreeCtpsMap.put(latent, ctps); // add this ctps group under the root of the tree
		}
		
		Map<Variable, Integer> varIdx = dataSet.createVariableToIndexMap();// create a map from the manifest variables in this dataset to their index in the dataset
		
		// create an index map that stores the order of each manifest variable within an island
		Map<Variable, Integer> localVarIdx = new HashMap<Variable, Integer>();
		for (Variable latent : _hierarchies.keySet()) {
			
			int localIndex = 0;
			
			LTM island = _hierarchies.get(latent);
			
			Set<Variable> islandVariables = island.getManifestVars();
			
			for (Variable inslanVar : islandVariables) {
				
				/*if(localIndex >=5)
					try {
						throw new Exception("local index invalid: "+ localIndex );
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}*/
				
				localVarIdx.put(inslanVar, localIndex);
				localIndex = localIndex + 1;
			}
			
		}
		
		ArrayList<DataCase> data = dataSet.getData(); // the original data
		
		makeLatenVariableToProjectionMap(varIdx, 
				localVarIdx,  _hierarchies ,
				 rootToTreeCtpsMap, 
				 LatentVars);
		
		ParallelHardAssignmentComputation.Context context =
				new ParallelHardAssignmentComputation.Context(LatentVars, data,  LatentVariableToDatasetMap, LatentVariableToIslandGlobalIndex );
		
		ParallelHardAssignmentComputation computation =
				new ParallelHardAssignmentComputation(context, 0,data.size());

		computation.hardAssign = new DataSet(LatentVars); // initialize the final hardAssign dataset
		
		getForkJoinPool().invoke(computation);
		
		HardAssignedData = computation.getData(); // receive the final harAssigned data
		
	}

	/**
	 * to get the final  hard-assigned data
	 * @return the hard-assigned data for the curent top level latent variables
	 */
	public DataSet getData() {
		return HardAssignedData;
	}
	
	protected static ForkJoinPool getForkJoinPool() {
		if (threadPool == null)
			threadPool = new ForkJoinPool();

		return threadPool;
	}
	
	@SuppressWarnings("serial")
	public static class ParallelHardAssignmentComputation extends RecursiveAction {
		
		public static class Context {
			
			/**
			 * Threshold at after which we would split into 2, i.e maximum variable size for one thread
			 * to iterate over
			 */
			public final int splitThreshold;
			public final Variable[] LatentVars;
			public final ArrayList<DataCase> data;
			public final HashMap<Integer,HashMap<IntList,Integer>> LatentVariableToDatasetMap;
			public final HashMap<Integer, IntList> LatentVariableToIslandGlobalIndex ;
			
			
			
			public Context(Variable[] LatentVars, 
					ArrayList<DataCase> data,  HashMap<Integer, HashMap<IntList,Integer>> LatentVariableToDatasetMap,
					HashMap<Integer, IntList> LatentVariableToIslandGlobalIndex) {
				
				
				
				this.LatentVars = LatentVars;
				this.data = data;
				this.LatentVariableToDatasetMap = LatentVariableToDatasetMap;
				this.LatentVariableToIslandGlobalIndex = LatentVariableToIslandGlobalIndex;
				 
				double NumberOfProcessorsPower2 = Math.floor(Math.log10(Runtime.getRuntime().availableProcessors())/Math.log10(2));
				
				splitThreshold =(int) Math.ceil((double)(data.size()) / Math.pow(2,NumberOfProcessorsPower2));
				
				System.out.println("Split Threshold: "+ splitThreshold +" , "+"Data Size: "+data.size());
				
			}
		
		}
		
		private final Context context;
		private final int start;
		private final int length;
		public DataSet hardAssign;

		public DataSet getData() {
			return hardAssign;
		}
		
		public ParallelHardAssignmentComputation(Context context, int start, int length) {
			this.context = context;
			this.start = start;
			this.length = length;

		}

		
		@Override
		protected void compute() {
			if (length <= context.splitThreshold) {
				computeDirectly();
				return;
			}

			int split = length / 2;
			ParallelHardAssignmentComputation c1 = new ParallelHardAssignmentComputation(context, start, split);
			ParallelHardAssignmentComputation c2 =
					new ParallelHardAssignmentComputation(context, start + split, length - split);
			c1.hardAssign = new DataSet(context.LatentVars);
			c2.hardAssign = new DataSet(context.LatentVars);
			invokeAll(c1, c2);
			
			for (DataCase d : c1.hardAssign.getData()) {
				hardAssign.addDataCase(d.getStates(),d.getWeight());
			}
			
			
			for (DataCase d : c2.hardAssign.getData()) {
				hardAssign.addDataCase(d.getStates(),d.getWeight());
			}
			
			
		}
		
		private void computeDirectly() {
			
			// array to store the new data
			int[][] newData = new int[length][context.LatentVars.length];
			
			
			
			// update for every data case
			for (int j = start; j < start+length; j++) {
				
				DataCase dataCase = context.data.get(j);

				int[] states = dataCase.getStates();

				for (int k = 0; k < context.LatentVars.length; k++) {
					

					// get the global states index
					IntList globalStateIndex = context.LatentVariableToIslandGlobalIndex.get(k);
					
					// get the projected sates
					IntList projectedStates = new IntArrayList();
					for (int i = 0 ; i < globalStateIndex.size() ; i++) {
						projectedStates.add(i, states[globalStateIndex.get(i)]);
					}
					
					newData[j-start][k] = context.LatentVariableToDatasetMap.get(k).get(projectedStates);
					
				} // end of all latent vars
				
				hardAssign.addDataCase(newData[j-start], context.data.get(j).getWeight()); // add the new hardassigned datacase
				
			}
			
	
		} // end compute

		
	}
	
	private void makeLatenVariableToProjectionMap(Map<Variable, Integer> varIdx, 
			Map<Variable, Integer> localVarIdx, Map<Variable, LTM> _hierarchies ,
			HashMap<Variable,CliqueTreePropagationGroup> rootToTreeCtpsMap, 
			Variable[] LatentVars) {
		// Map from the latent variable to the indices of the datacase states that would give its projected states in the order of localVarIdx. Then just get these indcies and pick the corresponding states from states[] and set them as evidence with the order form 
		// only works for small number of island variables e.g. 5; in that case the datacases for each island will be 2^5
		
		LatentVariableToIslandGlobalIndex = new HashMap<Integer, IntList>(); // stores the global index of the island varibles in ascending order of their local index 
		
		LatentVariableToDatasetMap = new HashMap<Integer,HashMap<IntList,Integer>>(); // map from the index of latent variable in LatentVars to the value. The value is a map with key as the array that contains the unique states for this latent variable an dvalue as the hardassigned value of the latent varible for this datacse
		
		
		// var, {54 24 1}  ordered global states ; so that we can get the projected states
		// int , {proj states, hardassign}
		for (int k = 0; k <LatentVars.length; k++) {
		
			HashMap<IntList,Integer> states2HardAssignValueMap = new HashMap<IntList,Integer>();
			
			LatentVariableToDatasetMap.put(k, states2HardAssignValueMap);
			
			Variable latent = LatentVars[k];
			
			LTM island = _hierarchies.get(latent); // get the island
			
			Set<Variable> islandVars =
					island.getManifestVars();// get island variable
			
			int nislandVars = islandVars.size();
		
			// project states
			Iterator<Variable> iter = islandVars.iterator();
			
			HashMap<Variable,Integer> islandVariableIndexMap = new HashMap<Variable,Integer>(); // sotres the local index of each island variable. This map wil be sorted later
			
			IntList orderedglobalIndex = new IntArrayList(); // stores the ordered global index, ehic are ordered in ascending order w.r.t the local index
			
			for (int i = 0; i < nislandVars; i++) {
				
				Variable islandVariable =  iter.next();
				
				int localIndex = (int)localVarIdx.get(islandVariable);
				
				islandVariableIndexMap.put(islandVariable, localIndex);

			}
			
			// sort the island variables as per their index 
			List<Map.Entry<Variable,Integer>> sortedIslandVariablesList = Utils.sortByAscendingOrderInt(islandVariableIndexMap);
			
			Variable[] sortedIslandVariables = new Variable[nislandVars];
			
			// get the global indices sorted w.r.t local indecies
			for (int i = 0 ; i < sortedIslandVariablesList.size() ; i++) {
				
				Variable islandVariable = sortedIslandVariablesList.get(i).getKey();
				
				sortedIslandVariables[i] = islandVariable;
				
				int globalIndex = (int)varIdx.get(islandVariable);
				
				orderedglobalIndex.add(i, globalIndex);
				
			}
			
			LatentVariableToIslandGlobalIndex.put(k,orderedglobalIndex);
			
			// go over all 2^nislandVars states and do their hardassignment
			Stack<Integer> stack = new Stack<Integer>();
			
			
			
			for (int i = 0; i < Math.pow(2, nislandVars); i++) {
				
				int[] localStates = new int[nislandVars]; 
				IntList localStatesList = new IntArrayList(nislandVars); // same as above , but needed for puttin in map
				
				
				
				for (int l = 0 ; l< nislandVars ; l++) {
					if(i == 0)// conversion to binaray doesnt work for 0 so do it here
						localStates[l] = 0;
					localStatesList.add(l, 0); // always initialize the list
				}

			
				int num = i;
				// convert to binary
				while (num != 0)
			    {
			      int d = num % 2;
			      stack.push(d);
			      num /= 2;
			    } 
				
				// populate the states with binaray equvilent of 0
				int index = nislandVars-stack.size(); // starting position of filling the states
				while (!(stack.isEmpty() ))
			    {
					
					localStates[index] = stack.pop();
					localStatesList.set(index, localStates[index]);
					index++;
			    }
				
				
				// do propagation
				CliqueTreePropagation ctp = rootToTreeCtpsMap.get(latent).take();// get the ctpgroup for the island under this latent variable
				
				// set evidence and propagate
				ctp.setEvidence(sortedIslandVariables, localStates);
				ctp.propagate();

				// compute P(Y|d)
				Function post = ctp.computeBelief(latent);
				
				// hardAssign the datacase
				double cell = 0;
				int assign = 0;

				for (int l = 0; l < post.getDomainSize(); l++) {
					if (post.getCells()[l] > cell) {
						cell = post.getCells()[l];
						assign = l;
					}
				}

				Integer assignment = new Integer(assign);
				LatentVariableToDatasetMap.get(k).put(localStatesList, assignment);
				//System.out.println(localStatesList+"--"+assignment);
				rootToTreeCtpsMap.get(latent).put(ctp);
				
			}
			
			
			
		}
		
	}
	
}
