// Copyright (C) 2010 Zeno Gantner, Steffen Rendle
// Copyright (C) 2011 Zeno Gantner, Chris Newell
// Copyright (C) 2014 Zeno Gantner, Chris Newell, Fabian Christoffel
//
//This file is part of MyMediaLite.
//
//MyMediaLite is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//MyMediaLite is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with MyMediaLite.  If not, see <http://www.gnu.org/licenses/>.

package org.mymedialite.eval;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.mymedialite.data.IEntityMapping;
import org.mymedialite.data.IPosOnlyFeedback;
import org.mymedialite.datatype.IBooleanMatrix;
import org.mymedialite.eval.measures.IMeasure;
import org.mymedialite.eval.measures.IMeasure.*;

import org.mymedialite.eval.measures.IMeasureDump;
import org.mymedialite.IRecommender;
import org.mymedialite.itemrec.Extensions;
import org.mymedialite.itemrec.Random;
import org.mymedialite.util.Utils;

/**
 * Evaluation class for item recommendation.
 *
 * @version 2.03
 */
public class ItemsParallel {

	// this is a static class, but Java does not allow us to declare that ;-)
	private ItemsParallel() {
	}

	static public List<IMeasure> getIMeasures(int numberOfRecommendableItems) {
		List<IMeasure> measures = new ArrayList<IMeasure>();


		return measures;
	}

	static public List<IMeasure> getIMeasures() {
		List<IMeasure> measures = new ArrayList<IMeasure>();


		measures.add(new AucAdapter());


		measures.add(new RecallAdapter(5));
		measures.add(new RecallAdapter(10));
		measures.add(new RecallAdapter(20));
		measures.add(new RecallAdapter(30));
		measures.add(new RecallAdapter(40));
		measures.add(new RecallAdapter(50));
		measures.add(new RecallAdapter(100));
		measures.add(new RecallAdapter(150));
		measures.add(new RecallAdapter(200));
		measures.add(new RecallAdapter(250));
		measures.add(new RecallAdapter(300));
		measures.add(new RecallAdapter(350));
		measures.add(new RecallAdapter(400));
		measures.add(new RecallAdapter(450));
		measures.add(new RecallAdapter(500));



		return measures;
	}

	static public List<IMeasure> getIMeasures(IBooleanMatrix attributesMatrix) {
		List<IMeasure> measures = new ArrayList<IMeasure>();

		return measures;
	}

	static public List<IMeasure> getIMeasures(IPosOnlyFeedback training) {
		// create item degrees map / popularity
		Map<Integer, Integer> itemPopularity = new HashMap<Integer, Integer>();
		IBooleanMatrix itemM = training.itemMatrix();
		for (int i = 0; i < itemM.numberOfRows(); i++) {
			itemPopularity.put(i, itemM.numEntriesByRow(i));
		}

		int numberOfUsers = itemM.numberOfColumns();

		List<IMeasure> measures = new ArrayList<IMeasure>();

		return measures;
	}

	static public List<IMeasure> getDistanceBasedCombinedDiversityIMeasures(
			IPosOnlyFeedback train, IPosOnlyFeedback test,
			IBooleanMatrix itemAttributes, Collection<Integer> testUsers,
			Collection<Integer> candidateItems) {
		List<IMeasure> measures = new ArrayList<IMeasure>();
		return measures;
	}

	static public List<IMeasure> getCombinedDiversityIMeasures(
			IPosOnlyFeedback train, IPosOnlyFeedback test,
			Collection<Integer> testUsers, Collection<Integer> candidateItems) {
		List<IMeasure> measures = new ArrayList<IMeasure>();

		return measures;
	}

	/**
	 * Evaluation for rankings of items in parallel.
	 *
	 * User-item combinations that appear in both sets are ignored for the test
	 * set, and thus in the evaluation, except when the boolean argument
	 * repeated_events is set.
	 *
	 * The evaluation measures are listed in the ItemPredictionMeasures
	 * property. Additionally, 'num_users' and 'num_items' report the number of
	 * users that were used to compute the results and the number of items that
	 * were taken into account.
	 *
	 * Literature: C. Manning, P. Raghavan, H. Sch&uuml;tze: Introduction to
	 * Information Retrieval, Cambridge University Press, 2008
	 *
	 * @param recommender
	 *            item recommender
	 * @param test
	 *            test cases
	 * @param training
	 *            training data
	 * @param test_users
	 *            a collection of integers with all relevant users
	 * @param candidate_items
	 *            a collection of integers with all relevant items
	 * @param candidate_item_mode
	 *            the mode used to determine the candidate items. The default is
	 *            CandidateItems.OVERLAP
	 * @param repeated_events
	 *            allow repeated events in the evaluation (i.e. items accessed
	 *            by a user before may be in the recommended list). The default
	 *            is false.
	 * @return a dictionary containing the evaluation results
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static ItemRecommendationEvaluationResults evaluate(
			IRecommender recommender, IPosOnlyFeedback test,
			IPosOnlyFeedback training, Collection<Integer> test_users,
			Collection<Integer> candidate_items, IBooleanMatrix itemAttributes,
			CandidateItems candidate_item_mode, Boolean repeated_events,
            IEntityMapping userMapping, File dumpLocation,int max_test_users,Map<Integer,List<Integer>> getCandidateItemPerUser) throws InterruptedException, ExecutionException {
		return evaluate(recommender, test, training, test_users, candidate_items,
                itemAttributes, candidate_item_mode, repeated_events, null,
                userMapping, dumpLocation,max_test_users, getCandidateItemPerUser);
	}

	/**
	 * Evaluation for rankings of items in parallel.
	 *
	 * User-item combinations that appear in both sets are ignored for the test
	 * set, and thus in the evaluation, except when the boolean argument
	 * repeated_events is set.
	 *
	 * The evaluation measures are listed in the ItemPredictionMeasures
	 * property. Additionally, 'num_users' and 'num_items' report the number of
	 * users that were used to compute the results and the number of items that
	 * were taken into account.
	 *
	 * Literature: C. Manning, P. Raghavan, H. Sch&uuml;tze: Introduction to
	 * Information Retrieval, Cambridge University Press, 2008
	 *
	 * @param recommender
	 *            item recommender
	 * @param test
	 *            test cases
	 * @param training
	 *            training data
	 * @param test_users
	 *            a collection of integers with all relevant users
	 * @param candidate_items
	 *            a collection of integers with all relevant items
	 * @param candidate_item_mode
	 *            the mode used to determine the candidate items. The default is
	 *            CandidateItems.OVERLAP
	 * @param repeated_events
	 *            allow repeated events in the evaluation (i.e. items accessed
	 *            by a user before may be in the recommended list). The default
	 *            is false.
	 * @return a dictionary containing the evaluation results
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static ItemRecommendationEvaluationResults evaluate(
			IRecommender recommender, IPosOnlyFeedback test,
			IPosOnlyFeedback training, Collection<Integer> test_users,
			Collection<Integer> candidate_items, IBooleanMatrix itemAttributes,
			CandidateItems candidate_item_mode, Boolean repeated_events,
            List<IMeasure> measures, IEntityMapping userMapping, File dumpLocation,int max_test_users,
            Map<Integer,List<Integer>> getCandidateItemPerUser) throws InterruptedException, ExecutionException {

		if (candidate_item_mode == null)
			candidate_item_mode = CandidateItems.OVERLAP;
		if (repeated_events == null)
			repeated_events = false;

		if (candidate_item_mode.equals(CandidateItems.TRAINING)) {
			candidate_items = training.allItems();
		} else if (candidate_item_mode.equals(CandidateItems.TEST)) {
			candidate_items = test.allItems();
		} else if (candidate_item_mode.equals(CandidateItems.OVERLAP)) {
			candidate_items = Utils.intersect(test.allItems(),
					training.allItems());
		} else if (candidate_item_mode.equals(CandidateItems.UNION)) {
			candidate_items = Utils.union(test.allItems(), training.allItems());
	    }  else if(candidate_item_mode.equals(CandidateItems.EXPLICIT)) {
	    	if (candidate_items == null)
	    		throw new IllegalArgumentException("candidate_items == null!");
	    }

		if (test_users == null)
			test_users = test.allUsers();

		int num_users = 0;
		ItemRecommendationEvaluationResults result = new ItemRecommendationEvaluationResults();

		IBooleanMatrix training_user_matrix = training.userMatrix();
		IBooleanMatrix test_user_matrix = test.userMatrix();

		// compile the complete measures list if no measures injected
		if (measures == null) {
			measures = getIMeasures();
		}

        if (userMapping != null && dumpLocation != null){
            measures = wrapMeasuresIntoDumpMeasures(userMapping, measures, dumpLocation);
        }

		ExecutorService executor = Executors.newFixedThreadPool(Runtime
				.getRuntime().availableProcessors());
		// ExecutorService executor = Executors.newFixedThreadPool(1);
		ArrayList<Future<ItemRecommendationEvaluationResults>> futures = new ArrayList<Future<ItemRecommendationEvaluationResults>>();

		for (Integer user_id : test_users) {
			// Items viewed by the user in the test set that were also present
			// in the training set.

			if(getCandidateItemPerUser!=null)
				candidate_items = getCandidateItemPerUser.get(user_id);

			HashSet<Integer> correct_items = new HashSet<Integer>(
					Utils.intersect(test_user_matrix.get(user_id),
							candidate_items));

			// The number of items that will be used for this user.
			HashSet<Integer> candidate_items_in_train = new HashSet<Integer>(
					Utils.intersect(training_user_matrix.get(user_id),
							candidate_items));

			int num_eval_items = candidate_items.size()
					- (repeated_events ? 0 : candidate_items_in_train.size());

			// Skip all users that have 0 or #relevant_items test items.
			if (correct_items.size() == 0)
				continue;
			if (num_eval_items - correct_items.size() == 0)
				continue;

			EvaluationTask task = new EvaluationTask(recommender,
					candidate_items, repeated_events, training_user_matrix,
					user_id, correct_items, measures);

			futures.add(executor.submit(task));

			if(num_users > max_test_users) // added to avoid a lot of users in evaluation
				break;

			num_users++;
		}

		executor.shutdown();
		int completedFutures = 0;
		for (Future<ItemRecommendationEvaluationResults> future : futures) {
			//try {
				ItemRecommendationEvaluationResults futureResult = future.get();

				for (IMeasure m : measures) {
					result.put(m.getName(), result.get(m.getName())
							+ futureResult.get(m.getName()));
				}

				completedFutures++;

				// or: printStatus(completedFutures);
				printIntermediateResult(completedFutures, futures.size(),
						result, measures);
		}

		if (num_users > 1000)
			System.out.println();

		for (IMeasure measure : measures) {
			String mId = measure.getName();
			result.put(mId, measure.normalize(result.get(mId), num_users));
		}

		result.put("num_users", (double) num_users);
		result.put("num_items", (double) candidate_items.size());

        if (userMapping != null && dumpLocation != null){
            dumpMeasures(measures);
        }

		return result;
	}

    private static List<IMeasure> wrapMeasuresIntoDumpMeasures(
            IEntityMapping userMapping,List<IMeasure> measures, File dumpLocation){
        List<IMeasure> list = new ArrayList<IMeasure>();
        for(IMeasure m : measures){
            list.add(new IMeasureDump.PerUserMeasureDump(userMapping, m, dumpLocation));
        }
        return list;
    }

    private static void dumpMeasures(List<IMeasure> measures){
        for(IMeasure m : measures){
            ((IMeasureDump) m).dump();
        }
    }

	private static void printIntermediateResult(int completedFutures,
			int numOfFutures, ItemRecommendationEvaluationResults result,
			List<IMeasure> measures) {
		if (completedFutures % 10 == 0) {
			StringBuilder sb = new StringBuilder();
			if (completedFutures % Integer.MAX_VALUE == 0) {
				DecimalFormat f = new DecimalFormat("##0.0000");
				sb.append("| ");
				for (IMeasure measure : measures) {
					if (measure.intermediateCalculationAllowed()) {
						String mId = measure.getName();
						double v = measure.normalize(result.get(mId),
								(double) completedFutures);
						sb.append(mId);
						sb.append(": ");
						sb.append(f.format(v));
						sb.append(" | ");
					}
				}
				sb.append("\n");
			}
			sb.append(completedFutures);
			sb.append(" of ");
			sb.append(numOfFutures);
			sb.append(" calculation tasks completed.");
			System.out.println(sb.toString());
		}
	}

	public static class EvaluationTask implements
			Callable<ItemRecommendationEvaluationResults> {

		private final IRecommender recommender;
		private final Collection<Integer> candidate_items;
		private final Boolean repeated_events;

		private final IBooleanMatrix training_user_matrix;
		private final Integer user_id;

		private final HashSet<Integer> correct_items;

		private final List<IMeasure> measures;

		private EvaluationTask(IRecommender recommender,
				Collection<Integer> candidate_items, Boolean repeated_events,
				IBooleanMatrix training_user_matrix, Integer user_id,
				HashSet<Integer> correct_items, List<IMeasure> measures) {
			this.recommender = recommender;
			this.candidate_items = candidate_items;
			this.repeated_events = repeated_events;
			this.training_user_matrix = training_user_matrix;
			this.user_id = user_id;
			this.correct_items = correct_items;
			this.measures = measures;
		}

		private final ItemRecommendationEvaluationResults result = new ItemRecommendationEvaluationResults();

		@Override
		public ItemRecommendationEvaluationResults call() throws Exception {

			List<Integer> randomItems = Extensions.predictItems(new Random(),
					user_id, candidate_items);
			List<Integer> prediction_list = Extensions.predictItems(
					recommender, user_id, randomItems);

			if (prediction_list.size() != candidate_items.size())
				throw new RuntimeException("Not all items have been ranked.");

			Collection<Integer> ignore_items = repeated_events ? new ArrayList<Integer>()
					: training_user_matrix.get(user_id);

			if (user_id == 1512) {
				System.out.println("Consumed items by user "+ user_id+" :" +training_user_matrix.get(user_id));
				System.out.println("correct items by user "+ user_id+" :" +correct_items);
				System.out.println("recommended items to user "+ user_id+" :" +prediction_list);
			}

			for (IMeasure m : measures) {

				double resultValue = m.compute(user_id, prediction_list,
						correct_items, ignore_items);

				/*if(m.getName().equals("recall@20")) {
					System.out.println("# correct items:"+correct_items.size()+", user: "+ user_id +" , recall@20: "+ resultValue);
				}*/

				result.put(m.getName(), resultValue);
			}

			return result;
		}

	}

	/**
	 * Format item prediction results.
	 *
	 * @param result
	 *            the result dictionary
	 * @return a string containing the results
	 */
	public static String formatResults(Map<String, Double> result,
			List<String> measureIdentifiers) {
		StringBuilder sb = new StringBuilder();
		for (String measureIdentifier : measureIdentifiers) {
			sb.append(measureIdentifier);
			sb.append(" ");
			sb.append(result.get(measureIdentifier));
		}
		return sb.toString();
	}

	/**
	 * Display item prediction results.
	 *
	 * @param result
	 *            the result dictionary
	 */
	static public void displayResults(HashMap<String, Double> result,
			List<String> measureIdentifiers) {
		for (String measureIdentifier : measureIdentifiers) {
			System.out.println(measureIdentifier + "\t"
					+ result.get(measureIdentifier));
		}
		System.out.println("num_users  " + result.get("num_users"));
		System.out.println("num_items  " + result.get("num_items"));
		System.out.println("num_lists  " + result.get("num_lists"));
	}

}