package org.mymedialite.eval.measures.diversity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class NormalizedDiversityInTopN extends DiversityInTopN {

	private final int numberOfRecommendableItems;

	public NormalizedDiversityInTopN(int numberOfRecommendableItems, int topN) {
		super(topN);
		this.numberOfRecommendableItems = numberOfRecommendableItems;
	}

	@Override
	public double compute(List<Integer> recommendations,
			Set<Integer> correctItems, Collection<Integer> ignoreItems) {
		return super.compute(recommendations, correctItems, ignoreItems)
				/ (double) numberOfRecommendableItems;
	}

	@Override
	public String getName() {
		return "NormalizedDiversityInTopN@" + topN
				+ " (adapted from Adomavicius & Kwon (2012))";
	}

	@Override
	public double normalize(double result, double factor) {
		// return the current size of the set normalized by the
		// numberOfRecommendableItems, input parameters not needed
		return recommendedRecommendableItems.size()
				/ (double) numberOfRecommendableItems;
	}

}
