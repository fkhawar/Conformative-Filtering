package org.mymedialite.itemrec;

import it.unimi.dsi.fastutil.ints.IntCollection;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.latlab.util.Utils;
import org.latlab.util.Variable;
import org.mymedialite.correlation.CorrelationMatrix;
import org.mymedialite.data.IEntityMapping;
import org.mymedialite.data.IPosOnlyFeedback;
import org.mymedialite.data.PosOnlyFeedback;
import org.mymedialite.datatype.EntityMappingVariable;
import org.mymedialite.datatype.Matrix;
import org.mymedialite.datatype.MatrixExtensions;
import org.mymedialite.datatype.SparseBooleanMatrix;
import org.mymedialite.io.Constants;
import org.mymedialite.io.IMatrixExtensions;
import org.mymedialite.io.Model;
import org.mymedialite.itemrec.ItemRecommender;

import org.latlab.io.ParseException;
import org.latlab.io.Parser;
import org.latlab.io.bif.BifParser;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;

/**
 * Implementation of Conformative Filtering. Please see the ECIR 2019 paper "Conformative Filtering for Implicit Feedback Data"
 * avaialbe here https://arxiv.org/pdf/1704.01889.pdf.
 *
 * Note: This code should be only invoked via ItemRecommendation.java
 * @author fkhawar
 *
 */
public class LTM_FR extends ItemRecommender {

	/**
	 * The learned latent tree model
	 */
	private LTM _model;

	/**
	 * the path where the model file is located
	 */
	public String modelPath;

	/**
	 * the number of latest events that we will keep for each user
	 */
	public int historySize;

	public int userHistorySize;

	/**
	 * The name of the training dataset with time information
	 *
	 * If this file is not null then we will use the last _historySize ratings for a user
	 * for determining the userLatent factors.
	 *
	 * Note: for now we are will use this restricted data to get the membership of the user
	 * the latent variable also . Later we might want to do two propagation, one for restricted dataset
	 * to get the user latent vector and the other on the full dataset to get user membership
	 * to a latent variable
	 */
	public String timeRestrictedTrainfileName;



	/**
	 * Time restricted Training DataSet. This will be created if _timeRestrictedTestfileName
	 * is not null.
	 */
	static IPosOnlyFeedback _timeRestrictedtrainData;


	/**
	 *  Item internal ID to external ID
	 */
	public IEntityMapping _item_mapping;

	/**
	 *  Item internal ID to external ID
	 */
	public IEntityMapping _user_mapping;

	/**
	 * Latent level at which the recommendation will be made
	 */
	public int latentLevel;

	/**
	 * the threshold of a user belonging to a latent variable
	 */
	public double PZ1threshold;


	/**
	 * The test users specified by us, implemented this to speed up so that we dont have to
	 * calculate the latent factors for all users
	 *
	 * From the users specified by us, these are the only users
	 */
	public List<Integer> test_users;

	/**
	 * These are the items that overlap in the test and the training set
	 * for the test_users
	 *
	 * NOTE: due to this setting we are effectively working with the
	 * mode --overlap-items, other modes might not have any effect. For other
	 * modes to take effect we will need to change "Collection<Integer> overlapItems" in
	 * ItemRecommendation.java.
	 */
	public Collection<Integer> candidate_items;

	/**
	 * tell whether we want to get our model fir evaluation done, basically it will allow repeated user-item pairs i.e. pairs in train can also be in test set
	 */
	public boolean compute_fit = false;

	/**
	 * whether time will be used in calculating the group preference
	 */
	public boolean timeInGroupPreference = false;

	/**
	 * whether time decay will be used i.e. time based weight to calculate group preference.
	 * If it is -1.0 then no time decay weight will be used in calculating group preference
	 * Otherwise the value tells what is the target recommendation time against which the weight
	 * will be calculated.
	 *
	 * The time is in unix epochs
	 */
	public float timeDecayGroupTargetTime = -1.0f;

	/**
	 * lambda =1/To , where T0 is the half life of the weight, Ding 2006
	 */
	public double lambda;

	/**
	 * the normalization of item factors
	 */
	public ArrayList<Double> _normalization;


	/**
	 * For each user store the sorted list of map entry where the key is item_id and value is time stamp
	 */
	private HashMap<Integer,List<Map.Entry<Integer, Double>>> timeData;

	/**
	 * Whether time will be used in calculating the user preference
	 */
	public boolean timeUserPreference = false;

	/**
	 * This tells whether we need to recompute the user latent factors before calculating the item factors.
	 * This can occur when timeUserPreference=true and timeInGroupPreference= false or timeUserPreference=false and timeInGroupPreference=true
	 */
	private boolean recomputeUserFactors = false; // T. This might occur if the timeInGroupPreference=false and timeUserPreference=true. in this case the user factors will be on reduced dataset whereas in group we need the full factors


	/**
	 * The number of latent variables at each level of the LTM
	 */
	private int numFactors;

	/**
	 * Ordered variable of the selected latent level
	 */
	private Object[] _variables;


	/**
	 * Variables from model at different levels of the latent tree
	 */
	private HashMap<Integer, HashSet<Variable>> _varDiffLevels;

	/**
	 * mapping from variable to internal item id
	 */
	private EntityMappingVariable EntityMapping;


	/**
	 * the group of users that belong to this latent variable/ or are clustered by this latent variable
	 */
	private HashMap<Variable, HashSet<Integer>> variableUserGroupMap;


	 /** Latent user factor matrix */
	  protected Matrix<Double> userFactors;  // [user index] [feature index]

	  /** Latent item factor matrix */
	  protected Matrix<Double> itemFactors;  // [item index] [feature index]


	/**
	 * Training the recommender, make sure all the public variables like _modelPath,_item_mapping and
	 * _latent_level are already set
	 */
	public void train(){
		initialization();
		userLatentVector();
		itemLatentVector();

	}

	public void setComputeFit(boolean cf){
		this.compute_fit = cf;
	}

	public void setItemMapping(IEntityMapping imap){
		this._item_mapping = imap;
	}


	public void setUserMapping(IEntityMapping umap){
		this._user_mapping = umap;
	}

	public void setTestUsers(List<Integer> tus){
		this.test_users = tus;
	}

	public void setCandidateItems(Collection<Integer> ci){
		this.candidate_items = ci;
	}


	private void initialization(){

		// Read model
		_model = new LTM();
		Parser parser;
		try {
			parser = new BifParser(new FileInputStream(modelPath),"UTF-8");
			parser.parse(_model);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} //  Parsing the LTM from the bif file provided in args[0]
		catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("model read");


		_varDiffLevels = new HashMap<Integer, HashSet<Variable>>();
        processVariables();
        System.out.println("variales processed");

        _variables = _varDiffLevels.get(latentLevel).toArray();

        numFactors = _variables.length;

        System.out.println("#Users: "+ (maxUserID + 1) + "#Items: "+ (maxItemID+1) + "numFactors:" + numFactors);
		userFactors = new Matrix<Double>(maxUserID + 1, numFactors, 0.0);
		itemFactors = new Matrix<Double>(maxItemID + 1, numFactors, 0.0);

        variableUserGroupMap = new HashMap<Variable, HashSet<Integer>>();

        _normalization = new ArrayList<Double>(_variables.length);
	}

	/**
	 * Getting a latent representation for user, also get the group of users for each latent variable
	 */
	private void userLatentVector(){

		EntityMapping = new EntityMappingVariable();
		projectEntityMappingVar(_model.getManifestVars(), _item_mapping, EntityMapping);

		System.out.println("computing recent ratings");

		// see if we have to use recent user transactions only
		if(timeRestrictedTrainfileName!=null){ // if file name is specified
			// Read the data
			try {
				if( timeUserPreference==true || timeInGroupPreference==true )
					recentRatings();
				/*else if(timeDecayGroupTargetTime != -1.0f){
					recentRatings();
					decayWeight();
				}*/
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}


		}

		long startUserFactor = System.currentTimeMillis();

		HashSet<Variable> topVariables =_varDiffLevels.get(2);//_varDiffLevels.get(_varDiffLevels.size()-1);
		HashSet<String> topVariablesNames = new HashSet<String>();

		for (Variable top : topVariables) {
			topVariablesNames.add(top.getName());
		}

		parallelUserFactorCompute pc = new parallelUserFactorCompute();
		pc.parallelUserFactorCompute1(EntityMapping,  _variables
				, feedback, timeUserPreference,
				timeInGroupPreference, _timeRestrictedtrainData,
				userFactors,
				 recomputeUserFactors,test_users,userHistorySize,
				timeData,_model,
				variableUserGroupMap,topVariablesNames);
		 _normalization = pc.getNorm();

		System.out.println("--- UserFactor Time: "
				+ (System.currentTimeMillis() - startUserFactor) + " ms ---");

	}

	/**
	 * Get the recent user (userHistorySize) ratings from timeData for a particular user
	 * @param the user for who the recent rating need to be gotten
	 * @return
	 */
	public static IntCollection getRecentUserRatings(int user, int userHistorySize,HashMap<Integer, List<Map.Entry<Integer, Double>>> timeData ){
		IntCollection recentRatings = new IntOpenHashSet();
		List<Map.Entry<Integer, Double>> sortedItems=timeData.get(user);

		for (int i = 0 ; i < userHistorySize ; i++){

    		if (i >= sortedItems.size()){ // b/c we increase length we need this condition to avoid index out of bounds
    			break;
    		}

    		recentRatings.add(sortedItems.get(i).getKey());
    	}

		return recentRatings;

	}


	/**
	 * Get the item latent vector for each item i.e n(g|Zi=1,D,m) where i= 1 ... Length(_variables)
	 */
	private void itemLatentVector(){

		parallelItemFactorCompute pc = new parallelItemFactorCompute();

		long startItemFactor = System.currentTimeMillis();

		pc.parallelItemFactorCompute1(candidate_items,itemFactors,
				feedback,EntityMapping, _variables,
				timeInGroupPreference,_timeRestrictedtrainData,
				userFactors,_normalization,test_users);

		System.out.println("--- Item Factor Time: "
				+ (System.currentTimeMillis() - startItemFactor) + " ms ---");


		System.out.println("Item latent factors calculated");
	}

	/**
	 * For all items not in train set of  user calculate sum_l[n(g|Z_k) * P(Z_k=1|u)]
	 * @param user Internal Id of a user
	 */
	public double predict(int user, int item){

		 if ((user < 0) || (user >= userFactors.dim1)) {
		      System.err.println("user is unknown: " + user);
		      return 0;
		 }
		 if ((item < 0) || (item >= itemFactors.dim1)) {
		      System.err.println("item is unknown: " + item);
		      return 0;
		 }

		 // perform row normalization i.e normalize for every user and item , equivalent to cosine
		 if (userFactors.dim2 != itemFactors.dim2)
			 throw new IllegalArgumentException("wrong row size: " + userFactors.dim2 + " vs. " + itemFactors.dim2);

			 return MatrixExtensions.rowScalarProduct(userFactors, user, itemFactors, item);

	}

	/**
	 * make a HashMap (_varDiffLevels) which had the names of variables at each level
	 */
	private void processVariables() // F: make a HashMap (_varDiffLevels) which had the names of variables at each level
	{

		Set<Variable> internalVar = _model.getLatVars(); // set of latent variables from out._model. _model is the same the class variable defined in the begining
		Set<Variable> leafVar     = _model.getLeafVars();// set of leaf variables(observed)


		HashSet<Variable> levelOne = new HashSet<Variable>();// Set of names of level 1 variables
		for(Variable v : leafVar)                        //  get the names of variables
		{
			levelOne.add(v);
		}
		_varDiffLevels.put(0, levelOne);                 // Add level 1 variables names to out._varDiffLevels using their

		int level=0;
		while(internalVar.size()>0)
		{
			HashSet<Variable> levelVar = _varDiffLevels.get(level); // current level variables.Starting from bottom-up level 1=0
			level++;

			HashSet<Variable> newSet = new HashSet<Variable>();
			for(Variable s : levelVar)
			{
				String parent = _model.getNode(s).getParent().getVariable().getName(); // get the name of the parent

				{
					internalVar.remove(((BeliefNode)_model.getNode(parent)).getVariable());//  remove parent 'variable' from the set internalVar
					newSet.add(((BeliefNode)_model.getNode(parent)).getVariable()); // add parent name to newSet
				}
			}
			_varDiffLevels.put(level, newSet); // put the names of the next level variables in _varDiffLevels
		}
	}

	/**
	 * Takes the mapping from the intenalId-ExternalID(var name) and makes the mapping InteralD_Variable
	 * @param model
	 * @param oldMapping the entity mapping from internal to external id(variable name)
	 * @return New mapping from the internal ID to the variable
	 *
	 * vars must be contained in the oldMapping.
	 *
	 * Todo: Assuming that name of manifest vars is model is same as the external ID in mapping.
	 * make IT MORE GENERAL LATER. OR save the mapping while making model
	 */
	public void projectEntityMappingVar(Set<Variable> vars, IEntityMapping oldMapping, EntityMappingVariable new_item_mapping){

		//EntityMappingVariable //new_item_mapping = new EntityMappingVariable();
		for (Variable var : vars){
			String name = var.getName();
			Integer old_internal = oldMapping.toInternalID(name);
			new_item_mapping.original_to_internal.put(var, old_internal);
			new_item_mapping.internal_to_original.put(old_internal, var);
		}

	}



	/**
	 * Implement this later
	 */
	public boolean canPredict(int userId, int itemId){
		return true;
	}
	/**
	 * Read the training set with time information , sort each users rating and pick the top
	 * historySize ratings and make a new dataset : _timeRestrictedTrainData
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws IOException
	 */
	private void recentRatings() throws InstantiationException, IllegalAccessException, IOException{
		// Key is internal userID, and entery has the key as internal item Id and value as the timestamp in ymdhms format e.g "2003112071243"

		timeData = new HashMap<Integer,List<Map.Entry<Integer, Double>>>();

		// Read ratings from train set including time stamp
		BufferedReader reader = new BufferedReader(new FileReader(timeRestrictedTrainfileName));

		//reader.readLine(); // ignoring the first line

		_timeRestrictedtrainData= new PosOnlyFeedback<SparseBooleanMatrix>(SparseBooleanMatrix.class);
	    String line;

	    while ((line = reader.readLine()) != null ) {
	      line = line.trim();
	      if(line.length() == 0) continue;
	      String[] tokens = line.split(Constants.SPLIT_CHARS, 0);
	      if(tokens.length < 3) throw new IOException("Expected at least three columns: " + line);

	      // Getting the internal IDs using the user and item mappings of the original data without time stamps
	      int user_id = _user_mapping.toInternalID((tokens[0]));
	      int item_id = _item_mapping.toInternalID((tokens[1]));

	      /*if(test_users!=null && !test_users.contains(user_id)){
				continue;
			}*/

	      // if the entry of this user is not created then create it
	      if(!timeData.containsKey(user_id)){
	    	  timeData.put(user_id, new ArrayList<Map.Entry<Integer, Double>>());
	      }

	      // add the datacase
	      Map.Entry<Integer,Double> entry =
			    new AbstractMap.SimpleEntry<Integer,Double>(item_id,Double.parseDouble(tokens[2]));
	      timeData.get(user_id).add(entry);

	    }

	    // Sort the entries for each user in descending order of time
	    for(int user : timeData.keySet()){
	    	List<Map.Entry<Integer, Double>> sortedItems = Utils.sortByDescendingOrder(timeData.get(user));
	    	timeData.put(user,sortedItems);

	    	// Add the latest items in the dataset
	    	int length = historySize; // if the number of items are less than historySize then use all the avaialbe items

	    	if(sortedItems.size() < historySize){
	    		length = sortedItems.size();
	    	}

	    	for (int i = 0 ; i < length ; i++){

	    		if (i >= sortedItems.size()){ // b/c we increase length we need this condition to avoid index out of bounds
	    			break;
	    		}

	    		// if user-item pair already exists then skip this case.If we want to compute our data fit dont do it
	    		if(_timeRestrictedtrainData.userMatrix().get(user,sortedItems.get(i).getKey()) && !compute_fit){ //
	    			length++;
	    			continue;
	    		}
	    		_timeRestrictedtrainData.add(user,sortedItems.get(i).getKey() );
	    	}

	    }


	}


	 /** { @inheritDoc } */
	  public void saveModel(String filename) throws IOException {
	    PrintWriter writer = Model.getWriter(filename, this.getClass(), "1");
	    saveModel(writer);
	    writer.flush();
	    writer.close();
	  }

	  /** { @inheritDoc } */
	  public void saveModel(PrintWriter writer) {
	    //IMatrixExtensions.writeMatrix(writer, userFactors);
	    IMatrixExtensions.writeMatrix(writer, itemFactors);
	    for (int i = 0 ; i< _variables.length ; i++) {
	    	writer.print(((Variable)_variables[i]).getName()+",");
	    }
	    boolean error = writer.checkError();
	    if(error) System.out.println("Error writing file.");
	  }


	/**
	 * Implement this later
	 */
	public void loadModel(String filename) throws IOException{

	}

	@Override
	public void train(CliqueTreePropagation ctp,
			HashMap<Integer, Variable> variableIntToVariable,
			double _RatioNegativeSamples) {
		// TODO Auto-generated method stub

	}

	@Override
	public int[][] train(CorrelationMatrix correlation,
			double _RatioNegativeSamples) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Todo:Implement this later
	 */
	public void loadModel(BufferedReader reader) throws IOException{

	}

	  public String toString() {
		    return "LTM-MF "+
		        " numFactors=" + numFactors +
		        " latentLevel="       + latentLevel +
		    	" PZ1threshold=" + PZ1threshold+
		    	" Model Name=" + modelPath+
		    	" # of Actual test users=" + test_users.size()+
		    	" # of Actual test items=" + candidate_items.size()+
		    	" History size= "+ historySize +
		    	" userHistorySize"+ userHistorySize +
		    	" TimeinGroupPreference= " + timeInGroupPreference+
		    	" timeUserPreference= " + timeUserPreference+
		    	" lambda= " + lambda+
		    	" timeDecayGroupTargetTime" + timeDecayGroupTargetTime;
		  }


}
