package org.mymedialite.eval.measures.diversity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.ints.IntList;

import org.apache.mahout.common.distance.CosineDistanceMeasure;
import org.mymedialite.datatype.IBooleanMatrix;

public class CosineAttributeTypeItemSimilarity extends
		JaccardAttributeTypeItemSimilarity {

	private int vectorLength = -1;
	
	public CosineAttributeTypeItemSimilarity(IBooleanMatrix attributeTypes) {
		super(attributeTypes);
	}

	@Override
	public double getSimilarity(int itemIdA, int itemIdB) {
		checkPrecondition(itemIdA, itemIdB);

		double[] aA = getVector(itemIdA);
		double[] bA = getVector(itemIdB);

		// we get the distance but want to return the similarity.
		// according to
		// http://en.wikipedia.org/wiki/Cosine_similarity:
		// distance = 1 - similarity,
		double distance = CosineDistanceMeasure.distance(aA, bA);

		return 1.0 - distance;
	}

	private double[] getVector(int itemId) {
		int l = getVectorLength();
		double[] v = new double[l];
		IntList aL = attributeTypes.getEntriesByRow(itemId);
		for (int i : aL) {
			v[i] = 1.0;
		}
		return v;
	}

	private int getVectorLength() {
		// We do this only to increase efficiency.
		// SparseBooleanMatrix.numberOfColumns()
		// has high complexity.
		if (vectorLength == -1) {
			this.vectorLength = attributeTypes.numberOfColumns();
		}
		return this.vectorLength;
	}

	@Override
	public String getName() {
		return "Cosine";
	}

}
