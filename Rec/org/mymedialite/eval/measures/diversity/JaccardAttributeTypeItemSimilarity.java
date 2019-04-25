package org.mymedialite.eval.measures.diversity;

import java.util.Set;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import org.mymedialite.datatype.IBooleanMatrix;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

public class JaccardAttributeTypeItemSimilarity implements IItemSimilarity {

	protected final IBooleanMatrix attributeTypes;

	public JaccardAttributeTypeItemSimilarity(IBooleanMatrix attributeTypes) {
		this.attributeTypes = attributeTypes;
	}

	@Override
	public double getSimilarity(int itemIdA, int itemIdB) {
		// we check first if both ids are known in the attribute table,
		// i.e. if at least one attribute is available for both
		// if this is not the case we can't calculate a similarity value
		// and throw an exception. his also prevents of division by 0.
		checkPrecondition(itemIdA, itemIdB);
		return calculateSimilarity(itemIdA, itemIdB);
	}

	protected void checkPrecondition(int itemIdA, int itemIdB) {
		Preconditions.checkArgument(!attributeTypes.getEntriesByRow(itemIdA)
				.isEmpty()
				&& !attributeTypes.getEntriesByRow(itemIdB).isEmpty(),
				"Both items need to have at least one attribute. itemIdA: "
						+ itemIdA + ", itemIdB: " + itemIdB);
	}

	private double calculateSimilarity(int itemIdA, int itemIdB) {
		IntSet aAttributes = new IntOpenHashSet(
				attributeTypes.getEntriesByRow(itemIdA));
		IntSet bAttributes = new IntOpenHashSet(
				attributeTypes.getEntriesByRow(itemIdB));
		return calculateIntersectionSize(aAttributes, bAttributes)
				/ calculateUnionSize(aAttributes, bAttributes);
	}

	private double calculateIntersectionSize(Set<Integer> aAttributes,
			Set<Integer> bAttributes) {
		return Sets.intersection(aAttributes, bAttributes).size();
	}

	private double calculateUnionSize(Set<Integer> aAttributes,
			Set<Integer> bAttributes) {
		return Sets.union(aAttributes, bAttributes).size();
	}

	@Override
	public String getName() {
		return "Jaccard";
	}

}
