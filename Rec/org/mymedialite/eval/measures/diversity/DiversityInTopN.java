package org.mymedialite.eval.measures.diversity;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mymedialite.eval.measures.IMeasure.TopNMeasure;

import com.google.common.base.Preconditions;

/**
 * Diversity in top N as defined in: Adomavicius, G.; YoungOk Kwon,
 * "Improving Aggregate Recommendation Diversity Using Ranking-Based Techniques,"
 * Knowledge and Data Engineering, IEEE Transactions on , vol.24, no.5,
 * pp.896,911, May 2012.
 * 
 * Measures diversity in recommendation lists over all users.
 * 
 */
public class DiversityInTopN extends TopNMeasure {

	protected final Set<Integer> recommendedRecommendableItems = Collections
			.synchronizedSet(new HashSet<Integer>());

	public DiversityInTopN(int topN) {
		super(topN);
	}

	@Override
	public double compute(List<Integer> recommendations,
			Set<Integer> correctItems, Collection<Integer> ignoreItems) {

		Preconditions.checkArgument(recommendations.size() >= topN,
				"Size of recommendations list (" + recommendations.size()
						+ ") is smaler or equal to topN (" + topN + ").");

		this.recommendedRecommendableItems.addAll(getTopNRecommendations(
				recommendations, ignoreItems));

		return recommendedRecommendableItems.size();
	}

	// TODO breach in class hierarchy, not nice ;-(
	@Override
	public double normalize(double result, double factor) {
		// return the current size of the set, input parameters not needed
		return recommendedRecommendableItems.size();
	}

	@Override
	public String getName() {
		return "DiversityInTopN@" + topN + " (Adomavicius & Kwon (2012))";
	}

}
