package org.mymedialite.eval.measures.diversity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.mymedialite.eval.measures.IMeasure.TopNMeasure;

import com.google.common.collect.Sets;

/**
 * Personalization in top N as defined in: Zhou, T., Kuscsik, Z., Liu, J.-G.,
 * Medo, M., Wakeling, J. R., & Zhang, Y.-C. (2010). Solving the apparent
 * diversity-accuracy dilemma of recommender systems. Proceedings of the
 * National Academy of Sciences of the United States of America, 107(10),
 * 4511–5. doi:10.1073/pnas.1000488107
 * 
 * Measures diversity in recommendation lists over all users.
 * 
 */
public class Personalization extends TopNMeasure {

	private final Queue<Set<Integer>> topRecommendations = new ConcurrentLinkedQueue<Set<Integer>>();

	public Personalization(int topN) {
		super(topN);
	}

	@Override
	public double compute(List<Integer> recommendations,
			Set<Integer> correctItems, Collection<Integer> ignoreItems) {
		Set<Integer> top = new HashSet<Integer>(getTopNRecommendations(
				recommendations, ignoreItems));
		topRecommendations.add(top);
		return Double.NaN;
	}

	@Override
	public double normalize(double result, double factor) {
		double sum = 0.0;
		int pairs = 0;
		List<Set<Integer>> trl = new ArrayList<Set<Integer>>(topRecommendations);
		for (int i = 0; i < trl.size(); i++) {
			for (int j = i + 1; j < trl.size(); j++) {
				Set<Integer> topI = trl.get(i);
				Set<Integer> topJ = trl.get(j);
				// size of topI or topJ can be < topN due to ignoreItems or
				// items that occur in topN of recommendation list
				// multiple times. If this is the case we add (topN - topX) / 2
				// to qij.
				double qij = (topN - topI.size()) / 2.0;
				qij += (topN - topJ.size()) / 2.0;
				qij += Sets.intersection(topI, topJ).size();
				double hij = 1.0 - (qij / (double) topN);
				sum += hij;
				pairs++;
			}
		}
		return sum / (double) pairs;
	}

	@Override
	public String getName() {
		return "Personalization@" + topN + " (Zhou et al. (2010))";
	}

}
