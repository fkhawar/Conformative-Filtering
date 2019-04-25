package org.mymedialite.eval.measures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * An evaluation measure expressing the quality of the recommender system.
 * 
 */
public interface IMeasure {

	/**
	 * Compute the measure for the passed user (e.g. AUC) or store passed
	 * arguments / intermediate results for later evaluation when
	 * {@link #normalize} is called. <br>
	 * WARNING: The implementation of this method needs to be thread safe.
	 * Multiple threads will call {@link #compute} in parallel, hence make sure
	 * that your implementation keeps a consistent state.
	 * 
	 * 
	 * @param userId
	 *            the id of the user corresponding to the passed item
	 *            collections
	 * @param recommendations
	 *            the ordered list of items recommended to the user (best first)
	 * @param correctItems
	 *            the unordered set of items with feedback from the passed user
	 *            in the test data.
	 * @param ignoreItems
	 *            the items from the recommendation list that should be ignored
	 *            in the measure calculation for this user (e.g. items that a
	 *            user already knows, i.e. if an interaction between the user
	 *            and the item already exists in the training set)
	 * @return the measure result if its calculation is possible or NaN if valid
	 *         measure value can only be provided after multiple calls to
	 *         {@link #compute} and will be obtained by calling
	 *         {@link #normalize}.
	 */
	double compute(Integer userId, List<Integer> recommendations,
			Set<Integer> correctItems, Collection<Integer> ignoreItems);

	/**
	 * Get the UNIQUE name of the measure.
	 * 
	 * @return this measures name
	 */
	String getName();

	/**
	 * Indicates if this measure allows calls to {@link #normalize} before all
	 * calls to {@link #compute} were made in order to obtain intermediate
	 * results of the measure.
	 * 
	 * @return true if this measure is allowed to produce a final result by
	 *         calling {@link #normalize} before all recommendation lists were
	 *         passed; else false.
	 */
	boolean intermediateCalculationAllowed();

	/**
	 * Perform any kind of data transformation to obtain the final result of
	 * this measure based on the recommendation lists passed through
	 * {@link #compute} to this measure so far. Feel free to use the passed
	 * parameters, but we mainly pass them in order to maintain backwards
	 * compatibility with previously implemented accuracy measures. <br>
	 * WARNING: If {@link #intermediateCalculationAllowed} returns true the
	 * implementation of this method needs to be thread safe.
	 * 
	 * @param result
	 *            the sum of the returned values of calls to {@link #compute} of
	 *            this measure so far
	 * @param factor
	 *            the number of calls to {@link #compute} of this measure so far
	 *            (e.g. number of users)
	 * @return the final value of the measure
	 */
	double normalize(double result, double factor);

	/*
	 * ##### END IMeasure interface ######
	 * 
	 * ##### START adapter classes ######
	 * 
	 * Adapters forward calculations to static method calls for previously
	 * implemented accuracy measures.
	 */

	public abstract class NoUserIdMeasure implements IMeasure {

		@Override
		public double compute(Integer userId, List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return compute(recommendations, correctItems, ignoreItems);
		}

		@Override
		public boolean intermediateCalculationAllowed() {
			return true;
		}

		public abstract double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems);

	}

	public abstract class PerUserMeasure extends NoUserIdMeasure {

		@Override
		public double normalize(double result, double factor) {
			return result / factor;
		}

	}

	public class AucAdapter extends PerUserMeasure {

		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return AUC.compute(recommendations, correctItems, ignoreItems);
		}

		@Override
		public String getName() {
			return "AUC";
		}
	}

	public class MapAdapter extends PerUserMeasure {

		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return PrecisionAndRecall.AP(recommendations, correctItems,
					ignoreItems);
		}

		@Override
		public String getName() {
			return "MAP";
		}
	}

	public class NdcgAdapter extends PerUserMeasure {

		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return NDCG.compute(recommendations, correctItems, ignoreItems);
		}

		@Override
		public String getName() {
			return "NDCG";
		}
	}

	public class MrrAdapter extends PerUserMeasure {

		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return ReciprocalRank.compute(recommendations, correctItems,
					ignoreItems);
		}

		@Override
		public String getName() {
			return "MRR";
		}
	}

	public abstract class TopNMeasure extends PerUserMeasure {

		protected final int topN;

		public TopNMeasure(int topN) {
			if (topN < 1) {
				throw new IllegalArgumentException(
						"Top N value must be greater 0. Passed value: " + topN);
			}
			this.topN = topN;
		}

		protected List<Integer> getTopNRecommendations(
				List<Integer> recommendations, Collection<Integer> ignoreItems) {
			List<Integer> topRecommendations = new ArrayList<Integer>();
			for (Integer r : recommendations) {
				if (!ignoreItems.contains(r)) {
					topRecommendations.add(r);
				}
				if (topRecommendations.size() == topN) {
					return topRecommendations;
				}
			}
			throw new IllegalStateException(
					"Recommendation list contains less than " + topN
							+ " valid recommendations. Size recommendations: "
							+ recommendations.size()
							+ ", size topRecommendations so far: "
							+ topRecommendations.size()
							+ ", size ignoreItems: " + ignoreItems.size());
		}
	}

	public class PrecAdapter extends TopNMeasure {

		public PrecAdapter(int topN) {
			super(topN);
		}

		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return PrecisionAndRecall.precisionAt(recommendations,
					correctItems, ignoreItems, topN);
		}

		@Override
		public String getName() {
			return "prec@" + topN;
		}
	}
	
	public class HitsAdapter extends TopNMeasure {

		public HitsAdapter(int topN) {
			super(topN);
		}

		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return PrecisionAndRecall.hitsAt(recommendations,
					correctItems, ignoreItems, topN);
		}

		@Override
		public String getName() {
			return "hits@" + topN;
		}
	}
	
	public class MapNAdapter extends TopNMeasure {
		
		public MapNAdapter(int topN) {
			super(topN);
		}

		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return PrecisionAndRecall.APn(recommendations, correctItems,
					ignoreItems, topN);
		}

		@Override
		public String getName() {
			return "MAP"+ topN;
		}
	}
	
	public class NdcgNAdapter extends TopNMeasure {

		
		public NdcgNAdapter(int topN) {
			super(topN);
		}
		
		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return NDCG.computeN(recommendations, correctItems, ignoreItems,topN);
		}

		@Override
		public String getName() {
			return "NDCG@"+ topN;
		}
	}

	public class RecallAdapter extends TopNMeasure {

		public RecallAdapter(int topN) {
			super(topN);
		}

		@Override
		public double compute(List<Integer> recommendations,
				Set<Integer> correctItems, Collection<Integer> ignoreItems) {
			return PrecisionAndRecall.recallAt(recommendations, correctItems,
					ignoreItems, topN);
		}

		@Override
		public String getName() {
			return "recall@" + topN;
		}
	}

}