package org.mymedialite.itemrec;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.latlab.graph.AbstractNode;
import org.latlab.learner.CliqueTreePropagationGroupRec;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueNode;
import org.latlab.reasoner.CliqueTree;
import org.latlab.reasoner.CliqueTreePropagationRec;
import org.latlab.util.Function;
import org.latlab.util.Variable;
import org.mymedialite.data.IPosOnlyFeedback;
import org.mymedialite.datatype.EntityMappingVariable;
import org.mymedialite.datatype.Matrix;

/**
 *
 * @author fkhawar
 *
 */

public class parallelUserFactorCompute {

	public parallelUserFactorCompute(){

	}

	protected HashMap<LinkedHashSet<Variable>,Map<AbstractNode,Function>> _defaultMessages = null;
	protected HashMap<LinkedHashSet<Variable>, HashMap<AbstractNode, Double>> _defaultAlphas = null;
	protected HashMap<LinkedHashSet<Variable>, HashMap<AbstractNode, Double>> _defaultLogAlphas = null;
	protected HashMap<LinkedHashSet<Variable>,LinkedList<Function>> _defaultfunctions = null;
	protected HashMap<LinkedHashSet<Variable>,LinkedHashSet<CliqueNode>> _defualtQualifiedNeiMsgs = null;
	protected HashMap<LinkedHashSet<Variable>,Function> _defaultmsgsProd = null;

	protected static HashMap<Variable,Double> _defaultposteriors = new HashMap<Variable,Double>();

	private static ForkJoinPool threadPool = null;
	public ArrayList<Double> normalization;

	public void parallelUserFactorCompute1(EntityMappingVariable EntityMapping, Object[] _variables
			,IPosOnlyFeedback feedback,  boolean timeUserPreference,
			boolean timeInGroupPreference, IPosOnlyFeedback _timeRestrictedtrainData,
			Matrix<Double>  userFactors,
			boolean recomputeUserFactors,List<Integer> test_users,int userHistorySize,
			HashMap<Integer,List<Map.Entry<Integer, Double>>> timeData,LTM _model,
			HashMap<Variable, HashSet<Integer>> variableUserGroupMap,HashSet<String> topLevel){


		// create a group of ctp based on the parallelism of the machine, see parallel em to do this via chickering....
		double NumberOfProcessorsPower2 = Math.floor(Math.log10(Runtime.getRuntime().availableProcessors())/Math.log10(2));

		CliqueTreePropagationGroupRec ctps = CliqueTreePropagationGroupRec.constructFromModel(_model,
					16);
		//CliqueTreePropagationGroupRec ctps = CliqueTreePropagationGroupRec.constructFromModel(_model,
		//		getForkJoinPool().getParallelism()+1);

		/*
		 * Below code chunk is for implementing the restricted propagation over the tree. Specifically computing defualt messages. It is a littel inefficient simnce the same computation is done for all ctps
		 */
		_defaultMessages = new HashMap<LinkedHashSet<Variable>,Map<AbstractNode,Function>>();
		_defaultAlphas = new HashMap<LinkedHashSet<Variable>, HashMap<AbstractNode, Double>>();
		_defaultLogAlphas = new HashMap<LinkedHashSet<Variable>, HashMap<AbstractNode, Double>>();
		_defaultfunctions = new HashMap<LinkedHashSet<Variable>,LinkedList<Function>>();
		_defualtQualifiedNeiMsgs = new HashMap<LinkedHashSet<Variable>,LinkedHashSet<CliqueNode>>();
		_defaultmsgsProd = new HashMap<LinkedHashSet<Variable>,Function>();


		BlockingQueue<CliqueTreePropagationRec> queue = new ArrayBlockingQueue<CliqueTreePropagationRec>(ctps.capacity);
		CliqueTreePropagationRec ctp1 = ctps.take();

		// get and save the default messages, these messages dont contain the reference to the actual messages
		CliqueTree defaultCtp = ctp1.makeDefaultMessageMap(_defaultMessages,_defaultAlphas, _defaultLogAlphas, _defaultfunctions, _defualtQualifiedNeiMsgs,
		 _defaultmsgsProd);
		ctps.put(ctp1);

		for(int i = 0 ; i < ctps.capacity ; i++) {
			CliqueTreePropagationRec ctp = ctps.take();
			// copy the default messages to ctp
			ctp.setDefaultMessages(_defaultMessages,_defaultAlphas, _defaultLogAlphas,_defaultfunctions, _defualtQualifiedNeiMsgs,
			 _defaultmsgsProd,defaultCtp);
			queue.add(ctp);

		}

		for(int i = 0 ; i < ctps.capacity ; i++) {

			CliqueTreePropagationRec ctp = null;
			try {
				 ctp = queue.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			ctps.put(ctp);

		}

		// get the posterior of the latent variables at the selected level with no evidence set
		CliqueTreePropagationRec ctp = ctps.take();
		for(int i = 0 ; i < _variables.length ; i++){
			Function posterior = ctp.computeBelief((Variable)_variables[i]);

			Double pZequal1 = posterior.getCells()[1]; // get P(Z=1|u,m)
			_defaultposteriors.put((Variable)_variables[i], pZequal1);
		}
		ctps.put(ctp);


		parallelUserFactorComputation.Context context =
			new parallelUserFactorComputation.Context(EntityMapping,  _variables
					, feedback, timeUserPreference,
					timeInGroupPreference, _timeRestrictedtrainData,
					userFactors,
					 recomputeUserFactors,test_users,userHistorySize,
					timeData,_model,
					ctps,variableUserGroupMap,topLevel);



		parallelUserFactorComputation computation =
			new parallelUserFactorComputation(context, 0,feedback.maxUserID()+1 );


		getForkJoinPool().invoke(computation);

		normalization = computation.getNormalization();


	}

	public ArrayList<Double> getNorm() {
		return normalization;
	}


	protected static ForkJoinPool getForkJoinPool() {
		if (threadPool == null)
			threadPool = new ForkJoinPool();

		return threadPool;
	}

	@SuppressWarnings("serial")
	public static class parallelUserFactorComputation extends RecursiveAction {

		public static class Context {
			public final int splitThreshold;
			public final EntityMappingVariable EntityMapping;
			public final Object[] _variables;
			public final IPosOnlyFeedback feedback;
			public Matrix<Double> userFactors;
			public final boolean timeUserPreference;
			public final boolean timeInGroupPreference;
			public final boolean recomputeUserFactors;
			public final List<Integer> test_users;
			public final int userHistorySize;
			public final CliqueTreePropagationGroupRec ctps;
			public final Set<Variable>  ManifestVars;
			public final HashSet<String> topLevel; // Names of the top level latent variables

			public Context(EntityMappingVariable EntityMapping, Object[] _variables
					,IPosOnlyFeedback feedback, boolean timeUserPreference,
					boolean timeInGroupPreference, IPosOnlyFeedback _timeRestrictedtrainData,
					Matrix<Double>  userFactors,
					boolean recomputeUserFactors,List<Integer> test_users,int userHistorySize,
					HashMap<Integer,List<Map.Entry<Integer, Double>>> timeData,LTM _model,
					CliqueTreePropagationGroupRec ctps,HashMap<Variable, HashSet<Integer>> variableUserGroupMap,
					HashSet<String> topLevel) {

				this.EntityMapping = EntityMapping;
				this._variables =  _variables;
				this.feedback = feedback;
				this.timeUserPreference = timeUserPreference;
				this.userFactors = userFactors;
				this.recomputeUserFactors = recomputeUserFactors;
				this.timeInGroupPreference = timeInGroupPreference;
				this.test_users = test_users;
				this.userHistorySize= userHistorySize;
				this.ctps = ctps;
				this.ManifestVars= _model.getManifestVars();
				this.topLevel = topLevel;


				double NumberOfProcessorsPower2 = Math.floor(Math.log10(Runtime.getRuntime().availableProcessors())/Math.log10(2));

				splitThreshold =(int) Math.ceil((feedback.maxUserID()+1) / (double) Math.pow(2, NumberOfProcessorsPower2));
				System.out.println(splitThreshold+","+ (feedback.maxUserID()+1) );
			}
		}
		private final Context context;
		private final int start;
		private final int length1;
		public ArrayList<Double> normalization;

		public ArrayList<Double> getNormalization() {
			return normalization;
		}

		public parallelUserFactorComputation(Context context, int start, int length) {
			this.context = context;
			this.start = start;
			this.length1 = length;
		}

		@Override
		protected void compute() {

			normalization = new ArrayList<Double>(context._variables.length);

			if (length1 <= context.splitThreshold) {
				System.out.println("start:"+start+","+"length"+length1);
				computeDirectly();
				System.out.println("Core for user "+start+" till user "+ (start+length1) +" completed");
				return;
			}

			int split = length1 / 2;
			parallelUserFactorComputation c1 = new parallelUserFactorComputation(context, start, split);
			parallelUserFactorComputation c2 =
					new parallelUserFactorComputation(context, start + split, length1 - split);


			invokeAll(c1, c2);

			for (int i = 0 ; i < context._variables.length ; i++) {
				normalization.add(i, c1.normalization.get(i)+c2.normalization.get(i));
			}




		}

		/**
		 * Get the recent user (userHistorySize) ratings from timeData for a particular user
		 * @param the user for who the recent rating need to be gotten
		 * @return
		 */
		public static IntCollection getRecentUserRatings(int user, int userHistorySize,HashMap<Integer, List<Map.Entry<Integer, Double>>> timeData ){
			IntCollection recentRatings = new IntOpenHashSet();
			List<Map.Entry<Integer, Double>> sortedItems=timeData.get(user);

			for (int i = 0 ; i < userHistorySize ; i++){

	    		if (i >= sortedItems.size()){ // b/c we increase length we need this condition to avoid index out of bounds
	    			break;
	    		}

	    		recentRatings.add(sortedItems.get(i).getKey());
	    	}

			return recentRatings;

		}

		private void computeDirectly() {

			CliqueTreePropagationRec posteriorCtp = context.ctps.take();



			Set<CliqueNode> lastPropagationRangeCliques = null;

			// initialize the normalization
			for(int i = 0 ; i < context._variables.length ; i++){
				normalization.add(i,0.0);
			}

			// Go over the dataSet user wise and get P(Z | u, m) for each Z for each u
			for (int u = start ; u < start+length1  ; u++){

				posteriorCtp.clearEvidence();


				// See if only recent transactions have to be used for user latent vector
				IntCollection row=null;


				if(context.timeUserPreference == false){
					row = context.feedback.userMatrix().get(u); // get all the items(evidence items) of user

				}

				Set<Variable> evidenceVar = context.EntityMapping.toOriginalIDSet(row); // get the corresponding evidence variables

				// set evidence of the user data case
				int countEvidenceSet = posteriorCtp.setPositiveOnlyEvidence(evidenceVar, context.ManifestVars);

				HashSet<Variable> lastPropvars = new HashSet<Variable>();

				//set the _focusedSubtree in the clique tree
				if(countEvidenceSet != 0) {

					if (lastPropagationRangeCliques != null)
						posteriorCtp.resetMessages(lastPropagationRangeCliques);

					lastPropagationRangeCliques =  posteriorCtp.findAndSetPropagationRange(context.topLevel);
					for (CliqueNode clique : lastPropagationRangeCliques) {
						lastPropvars.addAll(clique.getVariables());
					}
					posteriorCtp.propagate();
				}



				// Calculate P(Z = 1 |u,m)

				//ArrayList<Double> uList = new ArrayList<Double>();
				for(int i = 0 ; i < context._variables.length ; i++){

					Double pZequal1 = null;
					Variable latent = (Variable)context._variables[i];
					if(countEvidenceSet != 0 && lastPropvars.contains(latent)) {
						Function posterior = posteriorCtp.computeBelief(latent);

						pZequal1 = posterior.getCells()[1]; // get P(Z=1|u,m)

					}
					else {
						pZequal1 = _defaultposteriors.get(latent);

					}


					normalization.set(i,(normalization.get(i)+pZequal1));

					context.userFactors.set(u, i, pZequal1);
				}

			}

			context.ctps.put(posteriorCtp);
		}
	}

}
