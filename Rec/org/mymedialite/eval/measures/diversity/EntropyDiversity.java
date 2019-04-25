package org.mymedialite.eval.measures.diversity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mymedialite.eval.measures.IMeasure.TopNMeasure;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * Entropy-Diversity in top N as defined in: Adomavicius, G.; YoungOk Kwon,
 * "Improving Aggregate Recommendation Diversity Using Ranking-Based Techniques,"
 * Knowledge and Data Engineering, IEEE Transactions on , vol.24, no.5,
 * pp.896,911, May 2012.
 * 
 * Measures diversity in recommendation lists over all users.
 * 
 */
public class EntropyDiversity extends TopNMeasure {

	protected final AtomicLongMap<Integer> rec = AtomicLongMap.create();

	public EntropyDiversity(int topN) {
		super(topN);
	}

	@Override
	public double compute(List<Integer> recommendations,
			Set<Integer> correctItems, Collection<Integer> ignoreItems) {
		
		for (Integer r : getTopNRecommendations(recommendations, ignoreItems)) {
			rec.incrementAndGet(r);
		}
		
		return Double.NaN;
	}

	@Override
	public double normalize(double result, double factor) {
		// The entropy is a sum of terms of the form p log(p). When p=0 you
		// instead use the limiting value (as p approaches 0 from above), which
		// is 0.
		// from:
		// http://stats.stackexchange.com/questions/57069/alternative-to-shannons-entropy-when-probabilty-equal-to-zero
		double total = getTotal();
		double sum = 0.0;
		for (Map.Entry<Integer, Long> e : rec.asMap().entrySet()) {
			double recI = e.getValue();
			double t1 = recI / total;
			double t2 = Math.log(t1);
			sum += t1 * t2;
		}
		return sum * -1.0;
	}

	@Override
	public String getName() {
		return "EntropyDiversity@" + topN + " (Adomavicius & Kwon (2012))";
	}

	protected double getTotal() {
		// we can't calculate total = factor * topN because of ignoreItems
		return rec.sum();
	}

}
