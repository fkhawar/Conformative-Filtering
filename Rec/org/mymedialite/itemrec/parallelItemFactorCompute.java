package org.mymedialite.itemrec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.latlab.util.Variable;
import org.mymedialite.data.IPosOnlyFeedback;
import org.mymedialite.datatype.EntityMappingVariable;
import org.mymedialite.datatype.Matrix;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
/**
 *
 * @author fkhawar
 *
 */
public class parallelItemFactorCompute {

	private static ForkJoinPool threadPool = null;

	public parallelItemFactorCompute() {

	}
	public void parallelItemFactorCompute1(Collection<Integer> candidate_items,Matrix<Double> itemFactors,
			IPosOnlyFeedback feedback,EntityMappingVariable EntityMapping,Object[] _variables,
			boolean timeInGroupPreference,IPosOnlyFeedback _timeRestrictedtrainData,
			Matrix<Double>  userFactors, ArrayList<Double> normalization,List<Integer> relevantUsers){


		HashMap<Integer,Boolean> candidateUsers = new HashMap<Integer,Boolean>();
		for (int user: feedback.allUsers()) {
			// if we have a list of candidate users and that list does not contain this user then we just skip it
			if(relevantUsers!=null && !relevantUsers.contains(user)){
				candidateUsers.put(user, false);
			}
			else
				candidateUsers.put(user, true);

		}

		parallelItemFactorComputation.Context context =
				new parallelItemFactorComputation.Context(candidate_items,EntityMapping, _variables, feedback, itemFactors, timeInGroupPreference,
						_timeRestrictedtrainData, userFactors, normalization,candidateUsers);


		parallelItemFactorComputation computation =
			new parallelItemFactorComputation(context, 0,feedback.maxItemID()+1 );


		getForkJoinPool().invoke(computation);


	}

	protected static ForkJoinPool getForkJoinPool() {
		if (threadPool == null)
			threadPool = new ForkJoinPool();

		return threadPool;
	}

	@SuppressWarnings("serial")
	public static class parallelItemFactorComputation extends RecursiveAction {

		public static class Context {

			/**
			 * Threshold at after which we would split into 2, i.e maximum variable size for one thread
			 * to iterate over
			 */
			public final int splitThreshold;
			public Collection<Integer> candidate_items;
			public EntityMappingVariable EntityMapping;
			public Object[] _variables;
			public IPosOnlyFeedback feedback;
			public Matrix<Double> itemFactors;
			public boolean timeInGroupPreference;
			public IPosOnlyFeedback _timeRestrictedtrainData;
			public Matrix<Double>  userFactors;
			public  ArrayList<Double> normalization;
			public HashMap<Integer,Boolean> candidateUsers;

			public Context(Collection<Integer> candidate_items,EntityMappingVariable EntityMapping, Object[] _variables
					,IPosOnlyFeedback feedback, Matrix<Double> itemFactors, boolean timeInGroupPreference,
					IPosOnlyFeedback _timeRestrictedtrainData, Matrix<Double>  userFactors,  ArrayList<Double> normalization, HashMap<Integer,Boolean> candidateUsers) {

				this.candidate_items = candidate_items;
				this.EntityMapping =EntityMapping;
				this._variables =  _variables;
				this.feedback = feedback;
				this.itemFactors = itemFactors;
				this.timeInGroupPreference = timeInGroupPreference;
				this._timeRestrictedtrainData = _timeRestrictedtrainData;
				this.userFactors = userFactors;
				this.normalization = normalization;
				this.candidateUsers = candidateUsers;
				double NumberOfProcessorsPower2 = Math.floor(Math.log10(Runtime.getRuntime().availableProcessors())/Math.log10(2));

				splitThreshold =(int) Math.ceil((feedback.maxItemID()+1) / Math.pow(2,NumberOfProcessorsPower2));

			}

		}

		private final Context context;
		private final int start;
		private final int length;



		public parallelItemFactorComputation(Context context, int start, int length) {
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
			parallelItemFactorComputation c1 = new parallelItemFactorComputation(context, start, split);
			parallelItemFactorComputation c2 =
					new parallelItemFactorComputation(context, start + split, length - split);
			invokeAll(c1, c2);



		}

		private void computeDirectly() {
			for(int item = start ; item < start+length ; item++){

				// if we have a list of candidate items and that list does not contain this item then we just skip it
				if(context.candidate_items !=null && !context.candidate_items.contains(item)){
					continue;
				}

				//ArrayList<Double> iList = new ArrayList<Double>();

				// if the model is leaened on test set items than all item in trainset will not be in the model, so we skip them
				if(context.EntityMapping.internal_to_original.get(item)== null){
					continue;
				}

				// get users who consumed this item
				IntCollection consumingUsers;

				if(context.timeInGroupPreference == false)
					consumingUsers = context.feedback.itemMatrix().get(item);

				else {
					consumingUsers = context._timeRestrictedtrainData.itemMatrix().get(item);
				}

				for (int i = 0 ; i < context._variables.length ; i++){

					double weightedCount = 0; // sum_j(n(g|u_j,D)*P(Z=1|u_j,m))

					for(int user : consumingUsers)	{// for all users who consumed this item

						Double pZ1givenU = context.userFactors.get(user,i);

						weightedCount = weightedCount + pZ1givenU;

					}


					context.itemFactors.set(item, i, weightedCount/context.normalization.get(i));
				}
			}
		}



	}



}
