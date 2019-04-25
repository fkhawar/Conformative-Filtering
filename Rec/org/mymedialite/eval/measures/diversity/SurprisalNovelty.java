package org.mymedialite.eval.measures.diversity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.mymedialite.eval.measures.IMeasure.TopNMeasure;

/**
 * Surprisal/Novelty in top N as defined in: Zhou, T., Kuscsik, Z., Liu, J.-G.,
 * Medo, M., Wakeling, J. R., & Zhang, Y.-C. (2010). Solving the apparent
 * diversity-accuracy dilemma of recommender systems. Proceedings of the
 * National Academy of Sciences of the United States of America, 107(10),
 * 4511–5. doi:10.1073/pnas.1000488107
 * 
 */
public class SurprisalNovelty extends TopNMeasure {

	private final Map<Integer, Double> itemSelfInformations;

	public SurprisalNovelty(Map<Integer, Integer> itemDegrees, int numUsers,
			int topN) {
		super(topN);
		this.itemSelfInformations = calculateItemSelfInformations(itemDegrees,
				numUsers);
	}

	private Map<Integer, Double> calculateItemSelfInformations(
			Map<Integer, Integer> itemDegrees, int numUsers) {
		Map<Integer, Double> r = new ConcurrentHashMap<Integer, Double>();
		for (Map.Entry<Integer, Integer> e : itemDegrees.entrySet()) {
			double p = (double) numUsers / (double) e.getValue();
			double isi = Math.log(p) / Math.log(2.0);
			r.put(e.getKey(), isi);
		}
		return r;
	}

	@Override
	public double compute(List<Integer> recommendations,
			Set<Integer> correctItems, Collection<Integer> ignoreItems) {
		double sum = 0.0;

		List<Integer> topList = getTopNRecommendations(recommendations,
				ignoreItems);
		for (Integer r : topList) {
			sum += itemSelfInformations.get(r);
		}

		return (double) sum / (double) topList.size();
	}

	@Override
	public String getName() {
		return "Surprisal@" + topN + " (Zhou et al. (2010))";
	}

}
