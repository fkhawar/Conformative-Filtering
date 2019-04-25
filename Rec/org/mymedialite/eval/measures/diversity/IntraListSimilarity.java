package org.mymedialite.eval.measures.diversity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.mymedialite.eval.measures.IMeasure.TopNMeasure;

import com.google.common.base.Preconditions;

/**
 * Intra-List Similarity (ILS) according to: Ziegler, C.N., S.M. McNee, J.A.
 * Konstan, G. Lausen. 2005. Improving Recommendation Lists Through Topic
 * Diversification, Proc. of the 14th Intl. World Wide Web Conf. 22-32.
 * 
 */
public class IntraListSimilarity extends TopNMeasure {

	private final IItemSimilarity sim;

	/**
	 * @param topN
	 *            Since ILS does not consider the ranking order of a
	 *            recommendation list, ILS values are only meaningful relative
	 *            to a threshold value that is smaller than the number of items
	 *            to rank. Hence if always all items are contained in the
	 *            recommendation list and topN is equal o the size of the
	 *            recommendation list all recommendation lists produce the same
	 *            ILS value.
	 */
	public IntraListSimilarity(
			IItemSimilarity similarityCalculationImplementation, int topN) {
		super(topN);
		this.sim = similarityCalculationImplementation;
	}

	/**
	 * Calculates the Intra-List Similarity based on the injected similarity
	 * function implementation.
	 * 
	 * @param recommendations
	 *            the ordered list of recommended item ids, best first
	 * 
	 * @param correctItems
	 *            not required for this measure
	 * 
	 * @param ignoreItems
	 *            a collection of item IDs which should be ignored for the
	 *            evaluation
	 * 
	 * @return the ILS value or throws an exception if
	 *         {@code recommendations.size() < topN}
	 */
	@Override
	public double compute(List<Integer> recommendations,
			Set<Integer> correctItems, Collection<Integer> ignoreItems) {
		Preconditions.checkArgument(recommendations.size() >= topN,
				"Size of recommendations list (" + recommendations.size()
						+ ") is smaler or equal to topN (" + topN + ").");
		double r = 0.0;
		List<Integer> topList = getTopNRecommendations(recommendations,
				ignoreItems);
		for (int i = 0; i < topList.size(); i++) {
			for (int j = 0; j < topList.size(); j++) {
				if (i != j) {
					int itemIdA = topList.get(i);
					int itemIdB = topList.get(j);
					r += sim.getSimilarity(itemIdA, itemIdB);
				}
			}
		}
		return r / 2.0;
	}

	@Override
	public String getName() {
		return "ILS@" + topN + "-" + sim.getName() + " (Ziegler et al. (2005))";
	}
}
