package org.mymedialite.eval.measures.diversity;

import java.util.Map;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;

/**
 * Gini-Diversity in top N as defined in: Adomavicius, G.; YoungOk Kwon,
 * "Improving Aggregate Recommendation Diversity Using Ranking-Based Techniques,"
 * Knowledge and Data Engineering, IEEE Transactions on , vol.24, no.5,
 * pp.896,911, May 2012.
 * 
 * Measures diversity in recommendation lists over all users.
 * 
 */
public class GiniDiversity extends EntropyDiversity {

	private final int numberOfRecommendableItems;

	public GiniDiversity(int numberOfRecommendableItems, int topN) {
		super(topN);
		this.numberOfRecommendableItems = numberOfRecommendableItems;
	}

	@Override
	public double normalize(double result, double factor) {
		
		/*
		 * FIXME Occasionally we got here the following exception with 
		 * recommenders Perfect and Random:
		 * 
		 * 
		 * 2014-09-16T23:37:06 java.lang.IllegalArgumentException: Comparison
		 * method violates its general contract! Exception in thread "main"
		 * java.lang.RuntimeException: Exception during evaluation. at
		 * ch.uzh.ifi.ddis.mymedialite.main.ItemRecommenderEvaluate.main(ItemRecommenderEvaluate.java:300)
		 * Caused by: java.lang.IllegalArgumentException: Comparison method
		 * violates its general contract! at
		 * java.util.TimSort.mergeHi(TimSort.java:868) at
		 * java.util.TimSort.mergeAt(TimSort.java:485) at
		 * java.util.TimSort.mergeCollapse(TimSort.java:410) at
		 * java.util.TimSort.sort(TimSort.java:214) at
		 * java.util.Arrays.sort(Arrays.java:727) at
		 * com.google.common.collect.ImmutableSortedMap
		 * .sortEntries(ImmutableSortedMap.java:294) at
		 * com.google.common.collect
		 * .ImmutableSortedMap.fromEntries(ImmutableSortedMap.java:285) at
		 * com.google
		 * .common.collect.ImmutableSortedMap.copyOfInternal(ImmutableSortedMap
		 * .java:275) at
		 * com.google.common.collect.ImmutableSortedMap.copyOf(ImmutableSortedMap
		 * .java:223) at
		 * org.mymedialite.eval.measures.diversity.GiniDiversity.normalize
		 * (GiniDiversity.java:35) at
		 * org.mymedialite.eval.ItemsParallel.printIntermediateResult
		 * (ItemsParallel.java:365) at
		 * org.mymedialite.eval.ItemsParallel.evaluate(ItemsParallel.java:331)
		 * at
		 * ch.uzh.ifi.ddis.mymedialite.main.ItemRecommenderEvaluate.main(ItemRecommenderEvaluate.java:291)
		 * 
		 * At the moment I don't know how to fix that.
		 * For easier debugging I print the arguments passed to the comparator
		 * in case of an exception.
		 * 
		 * 
		 */	
		Map<Integer, Long> sortedRec;
		try {
			// I think the iteration has to be performed on a sorted counts map
			// see also GiniIndex.java in rival
			// (https://github.com/recommenders/rival/)
			//
			// sorting map by value with google guava according to
			// http://stackoverflow.com/a/3420912/2584278:
			Ordering<Integer> valueComparator = Ordering.natural()
					.onResultOf(
							Functions.forMap(rec.asMap()))
					.compound(Ordering.<Integer>natural());
			sortedRec = ImmutableSortedMap.copyOf(rec.asMap(),
					valueComparator);
		} catch (Exception e) {
			System.out.println("The item counts map:\n------\n" + rec + "\n-------");
			throw new RuntimeException(e);
		}

		//		System.out.println("Counts map:\n" + rec + "\n" + "Counts map ordered by value:\n" + sortedRec);

		double total = getTotal();
		double sum = 0.0;
		int iCount = numberOfRecommendableItems - sortedRec.size() + 1;
		for (Map.Entry<Integer, Long> e : sortedRec.entrySet()) {
			double t1 = (double) (numberOfRecommendableItems + 1 - iCount)
					/ (double) (numberOfRecommendableItems + 1);

			double recI = e.getValue();

			double t2 = recI / total;

			sum += t1 * t2;
			
			iCount++;
		}
		return 2.0 * sum;
	}

	@Override
	public String getName() {
		return "GiniDiversity@" + topN + " (Adomavicius & Kwon (2012))";
	}

}
