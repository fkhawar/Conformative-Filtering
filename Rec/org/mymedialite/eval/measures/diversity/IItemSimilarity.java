package org.mymedialite.eval.measures.diversity;

public interface IItemSimilarity {
	
	double getSimilarity(int itemIdA, int itemIdB);
	
	String getName();

}
