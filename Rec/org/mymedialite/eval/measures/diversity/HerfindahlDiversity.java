package org.mymedialite.eval.measures.diversity;

import java.util.Map;

/**
 * Herfindahl-Diversity in top N as defined in: Adomavicius, G.; YoungOk Kwon,
 * "Improving Aggregate Recommendation Diversity Using Ranking-Based Techniques,"
 * Knowledge and Data Engineering, IEEE Transactions on , vol.24, no.5,
 * pp.896,911, May 2012.
 * 
 * Measures diversity in recommendation lists over all users.
 * 
 */
public class HerfindahlDiversity extends EntropyDiversity {

	public HerfindahlDiversity(int topN) {
		super(topN);
	}

	@Override
	public double normalize(double result, double factor) {
		double total = getTotal();
		double sum = 0.0;
		for (Map.Entry<Integer, Long> e : rec.asMap().entrySet()) {
			double recI = e.getValue();
			sum += Math.pow(recI / total, 2.0);
		}
		return 1.0 - sum;
	}

	@Override
	public String getName() {
		return "HerfindahlDiversity@" + topN + " (Adomavicius & Kwon (2012))";
	}

}
