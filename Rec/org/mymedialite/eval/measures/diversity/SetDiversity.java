package org.mymedialite.eval.measures.diversity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.mymedialite.eval.measures.IMeasure.TopNMeasure;

import com.google.common.base.Preconditions;

/**
 * Diversity(c1, ...cn) measure of items in a set as defined in (equation (2) in
 * paper): B. Smyth and P. McClave, Similarity vs. diversity, in Proceedings of
 * the 4th International Conference on Case-Based Reasoning, Vancouver, pp.
 * 347–361, 2001.
 * 
 */
public class SetDiversity extends TopNMeasure {

	private final IItemSimilarity sim;

	/**
	 * 
	 * @param similarityCalculationImplementation
	 *            {@link IItemSimilarity} implementation that returns similarity
	 *            measures in the range [0,1]. Furthermore the similarity
	 *            implementation fulfills {@link 1 ==
	 *            IItemSimilarity.getSimilarity(a,a)} and {@link
	 *            IItemSimilarity.getSimilarity(a,b) ==
	 *            IItemSimilarity.getSimilarity(b,a)}.
	 * 
	 * @param topN
	 *            Since Diversity does not consider the ranking order of a
	 *            recommendation list, Diversity values are only meaningful
	 *            relative to a threshold value that is smaller than the number
	 *            of items to rank. Hence if always all items are contained in
	 *            the recommendation list and topN is equal o the size of the
	 *            recommendation list all recommendation lists produce the same
	 *            Diversity value.
	 */
	public SetDiversity(IItemSimilarity similarityCalculationImplementation,
			int topN) {
		super(topN);
		this.sim = similarityCalculationImplementation;
	}

	/**
	 * Calculates the Diversity according to Smyth and McClave (2001) based on
	 * the injected similarity function implementation. If the returned value is
	 * 1 all items in the past recommendation list have a similarity of 0.
	 * 
	 * @param recommendations
	 *            the ordered list of recommended item ids, best first
	 * @param correctItems
	 *            not required for this measure
	 * @param ignoreItems
	 *            a collection of item IDs which should be ignored for the
	 *            evaluation
	 * 
	 * @return the Diversity value in the range [0,1] or throws an exception if
	 *         {@code recommendations.size() < topN}
	 */
	public double compute(List<Integer> recommendations,
			Set<Integer> correctItems, Collection<Integer> ignoreItems) {
		Preconditions.checkArgument(recommendations.size() >= topN,
				"Size of recommendations list (" + recommendations.size()
						+ ") is smaler or equal to topN (" + topN + ").");

		double r = 0.0;
		List<Integer> topList = getTopNRecommendations(recommendations,
				ignoreItems);

		for (int i = 0; i < topList.size(); i++) {
			for (int j = i; j < topList.size(); j++) {
				int itemIdA = topList.get(i);
				int itemIdB = topList.get(j);
				r += (1.0 - sim.getSimilarity(itemIdA, itemIdB));
			}
		}
		return r / (topN / 2.0 * (topN - 1));
	}

	@Override
	public String getName() {
		return "Diversity@" + topN + "-" + sim.getName()
				+ " (Smyth & McClave (2001))";
	}
}
