// Copyright (C) 2011 Zeno Gantner, Chris Newell
//
// This file is part of MyMediaLite.
//
// MyMediaLite is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// MyMediaLite is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with MyMediaLite.  If not, see <http://www.gnu.org/licenses/>.

package org.mymedialite.eval;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.math.DoubleMath;

/**
 * Item recommendation evaluation results. This class is basically a HashMap
 * with a custom-made toString() method.
 * 
 * @version 2.03
 */
@SuppressWarnings("serial")
public class ItemRecommendationEvaluationResults extends
		LinkedHashMap<String, Double> {

	private DecimalFormat decimalFormat = new DecimalFormat("0.00000");
	private DecimalFormat integerFormat = new DecimalFormat("0");

	private static final double defaultValue = 0.0d;

	@Override
	public Double get(Object k) {
		return containsKey(k) ? super.get(k) : defaultValue;
	}

	/**
	 * Format item prediction results.
	 * 
	 * @return a string containing the results
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (String k : this.keySet()) {
			sb.append(k);
			sb.append("=");
			sb.append(this.getPrettyPrintedValue(k));
			sb.append(" ");
		}
		return sb.toString();
	}
	
	public String getPrettyPrintedValue(Object k){
		Double v = this.get(k);
		if (DoubleMath.isMathematicalInteger(v)) {
			return integerFormat.format(v);
		} else {
			return decimalFormat.format(v);
		}
	}
}
