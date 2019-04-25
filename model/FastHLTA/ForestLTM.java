package FastHLTA;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.graph.Edge;
import org.latlab.graph.UndirectedGraph;
import org.latlab.io.Parser;
import org.latlab.io.bif.BifParser;
import org.latlab.learner.ParallelEmLearner;
import org.latlab.learner.ParallelStepwiseEmLearner;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.reasoner.CliqueTreePropagation;
import org.latlab.util.DataSet;
import org.latlab.util.DataSet.DataCase;
import org.mymedialite.util.Random;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import org.latlab.util.DataSetLoader;
import org.latlab.util.Function;
import org.latlab.util.ScoreCalculator;
import org.latlab.util.StringPair;
import org.latlab.util.Utils;
import org.latlab.util.Variable;
import org.latlab.learner.SparseDataSet;

/**
 * Hierarchical Latent Tree Analysis for recommendation.
 * This is a new and fast algorithm for learning HLTA for binary data. Details can be found in the paper https://arxiv.org/pdf/1806.02056.
 *
 * @author fkhawar 
 * INPUTS:
 * _OrigSparseData: the input data in user,item tuple format
 * _EmMaxSteps: The maximum number of steps for the EM algorithm
 * _EmNumRestarts: The number of random starts for EM
 * _emThreshold: Convergence threshold for EM
 * _UDthreshold: The threshold for UD-test (uni-dimensionality test)
 * _modelname: The name of the output model
 * _maxIsland: The maximum number of variables in one island
 * _maxTop: The maximum number of variables at the top level
 * _sizeBatch: Size of a batch of data for stepwise EM (for parameter estimation)
 * _maxEpochs: Maximum number of epochs of data
 * _globalEMmaxSteps: The maximum number of steps for the global EM algorithm
 * _sizeFirstBatch: The size of the data batch used for model building
 * _IslandEmMaxSteps: The maximum number of EM steps for each island(sun model)
 * _IslandEmNumRestarts: The number of random restarts during EM for each island
 * 
 * OUTPUT
 * An HLTA model
 *
 */

public class ForestLTM {

	private LTM _model;
	
	private BayesNet _modelEst;
	/**
	 * Original data.
	 */
	private SparseDataSet _OrigSparseData;

	private DataSet _OrigDenseData;

	private DataSet _workingData;

	private DataSet _test;
	/**
	 * Threshold for UD-test.
	 */
	private double _UDthreshold;

	/**
	 * Threshold for EM.
	 */
	private double _emThreshold;

	/**
	 * Parameter for EM.
	 */
	private int _EmMaxSteps;

	private int _IslandEmMaxSteps;

	private int _IslandEmNumRestarts;

	private int _EmNumRestarts;
	/**
	 * The maximum number of latent variables at top level
	 */
	private int _maxTop;
	/**
	 * The collection of hierarchies. Each hierarchy represents a LCM and is
	 * indexed by the variable at its root.
	 */
	private Map<Variable, LTM> _hierarchies;

	/**
	 * The ArrayList of manifest variables with orders.
	 */
	private ArrayList<Variable> _Variables;

	/**
	 * The collection of manifest variables that wait to do UD-test.
	 */
	private Set<Variable> _VariablesSet;

	/**
	 * The collection of posterior distributions P(Y|d) for each latent variable
	 * Y at the root of a hierarchy and each data case d in the training data.
	 */
	private Map<Variable, Map<DataCase, Function>> _latentPosts;

	private int _globalEMmaxSteps;
	private int _maxEpochs;
	private int _sizeBatch;
	private String _sizeFirstBatch;
	/**
	 * Maximum number of island size
	 */
	private int _maxIsland;
	/**
	 * The collection of pairwise mutual information.
	 */
	private ArrayList<double[]> _mis;
	/**
	 * whether true class labels are included in data file.
	 */
	boolean _noLabel = false;

	/**
	 * Save bestPair of observed variables for every latent variable(LCM)
	 */
	Map<String, ArrayList<Variable>> _bestpairs =
			new HashMap<String, ArrayList<Variable>>();



	/**
	 * This Array will contain the variables in randomised order and they are populated from the original _VariablesSet
	 */
	private static ArrayList<Variable> _VariableArray;


	/**
	 * This list will store the random order of the variables. each index if this array stores the location of the
	 * variable after randomization
	 */
	private ArrayList<Integer> _randomVariableIndex; // CHANGE IT TO get index from Orign data to random


	/**
	 * Name the model you obtain
	 */
	String _modelname;

	/**
     * Store the variable index
     */
	static Map<String, Integer> _varId;


	/**
	 * Main Method
	 *
	 * @param args
	 * @throws Throwable
	 */

	public static void main(String[] args) throws Exception {
		if (args.length != 14 &&args.length != 1 &&args.length != 3 &&args.length!= 0) {
			System.err.println("Usage: java ForestLTM trainingdata (EmMaxSteps EmNumRestarts EM-threshold UDtest-threshold outputmodel MaxIsland MaxTop GlobalsizeBatch GlobalMaxEpochs GlobalEMmaxsteps FirstBatch IslandEmMaxSteps IslandEmNumRestarts");
			System.exit(1);
		}
		// TODO Auto-generated method stub

		if(args.length ==14||args.length ==1||args.length ==0){
			ForestLTM Fast_learner = new ForestLTM();
			Fast_learner.initialize(args);


			Fast_learner.IntegratedLearn();
		}
		if(args.length==3){
			ForestLTM test = new ForestLTM();
			test.testtest(args);
		}

	}

	/**
	 * Initialize All
	 *
	 * @param args
	 * @throws IOException
	 * @throws Exception
	 */
	public void initialize(String[] args) throws IOException, Exception

	{

		// _model = new LTM();
		// Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
		// parser.parse(_model);
        System.out.println("Initializing......");
		// Read the data set

        if(args.length==0){
        	 _OrigSparseData = new SparseDataSet("./data/SampleData_5000.arff");

        }else{
		_OrigSparseData = new SparseDataSet(args[0]);
        }

		if(args.length==14){
		_EmMaxSteps = Integer.parseInt(args[1]);

		_EmNumRestarts = Integer.parseInt(args[2]);

		_emThreshold = Double.parseDouble(args[3]);

		_UDthreshold = Double.parseDouble(args[4]);

		_modelname = args[5];

		_maxIsland = Integer.parseInt(args[6]);
		_maxTop = Integer.parseInt(args[7]);
		_sizeBatch = Integer.parseInt(args[8]);
		_maxEpochs = Integer.parseInt(args[9]);
		_globalEMmaxSteps = Integer.parseInt(args[10]);
		_sizeFirstBatch = args[11];
		if(_sizeFirstBatch.contains("all")){
            _OrigDenseData = _OrigSparseData.getWholeDenseData();
		}else{
            _OrigDenseData = _OrigSparseData.GiveDenseBatch(Integer.parseInt(_sizeFirstBatch));
		}

		_IslandEmMaxSteps = Integer.parseInt(args[12]);

		_IslandEmNumRestarts = Integer.parseInt(args[13]);

		}else{
			_EmMaxSteps =50;
			_EmNumRestarts=3;
			_emThreshold=0.01;
			_UDthreshold=3;
			_modelname ="HLTAModel";
			_maxIsland = 15;
			_maxTop =30;
			_sizeBatch =500;
			_maxEpochs = 10;
			_globalEMmaxSteps =100;
			_sizeFirstBatch = "all";
            _OrigDenseData = _OrigSparseData.getWholeDenseData();
            _IslandEmMaxSteps = 10;
            _IslandEmNumRestarts = 1;
		}
	}


	public void testtest(String[] args) throws IOException, Exception{
		 _model = new LTM();
		 Parser parser = new BifParser(new FileInputStream(args[0]),"UTF-8");
		 parser.parse(_model);

		 _test = new DataSet(DataSetLoader.convert(args[1]));

		 double perLL = evaluate(_model);
		 BufferedWriter BWriter = new  BufferedWriter(new FileWriter(args[2]+File.separator+"EvaluationResult.txt"));
         BWriter.write("Per-document log-likelihood =  "+perLL);
         BWriter.close();
	}




	public void IntegratedLearn() {
		try {

			_modelEst = FastHLTA_learn();

		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	/**
	 * Build the whole HLTA layer by layer
	 *
	 * @return BayesNet HLTA
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	public LTM FastHLTA_learn() throws FileNotFoundException,
			UnsupportedEncodingException {
			_workingData = _OrigDenseData; // dense data is related to the number of datacases, either whole data for specified by the user
			LTM CurrentModel = null;
			long start = System.currentTimeMillis();
			System.out.println("Start model construction...");
			int level = 2;
			while (true) {

			long startlayer = System.currentTimeMillis();
			LTM Alayer = FastLTA_flat(_workingData, level);
			System.out.println("--- Layer-"+ (level-1) +" Time: "+ (System.currentTimeMillis() - startlayer) + " ms ---");

			CurrentModel = BuildHierarchy(CurrentModel, Alayer); // stack the layers

			int a = _hierarchies.keySet().size();

			if (a <= _maxTop)
				break;

			System.out.println("Start hard assignment...");

			long startHA = System.currentTimeMillis();
			// non-parallel hardAssignment
			_workingData = IslandHardAssign(_workingData);

			System.out.println("--- Hard Assignment Time: "+ (System.currentTimeMillis() - startHA) + " ms ---");

			level++;
		}

		System.out.println("Model construction is completed. EM parameter estimation begins...");

		ParallelStepwiseEmLearner emLearner = new ParallelStepwiseEmLearner();
		emLearner.setMaxNumberOfSteps(_globalEMmaxSteps);
		emLearner.setNumberOfRestarts(1);
		emLearner.setReuseFlag(true);
		emLearner.setThreshold(_emThreshold);
	    emLearner.setBatchSize(_sizeBatch);
	    emLearner.setMaxNumberOfEpochs(_maxEpochs);

		long startGEM = System.currentTimeMillis();
		CurrentModel = (LTM)emLearner.em(CurrentModel, _OrigSparseData);


		System.out.println("--- Global EM Time: "
				+ (System.currentTimeMillis() - startGEM) + " ms ---");

		// rename latent variables, reorder the states.
		System.out.println("--- Total Time: "
				+ (System.currentTimeMillis() - start) + " ms ---");
		CurrentModel = postProcessingModel(CurrentModel);

		// output final model.
		CurrentModel.saveAsBif(_modelname + ".bif");


		return CurrentModel;
	}



	/**
	 * Randomize the order of the datacases. create an list with size _totalDatacases and in which
	 * each entry creates a randomized index to original datacase
	 */
	private void randomiseVariableIndex(int NumOfVariables){

		// First build the index if it does not exist or is of less size
		if (_randomVariableIndex == null || _randomVariableIndex.size() != NumOfVariables) {
			_randomVariableIndex = new ArrayList<Integer>(NumOfVariables);
		      for (int index = 0; index < NumOfVariables; index++)
		    	  _randomVariableIndex.add(index, index);
		    }
		// Then randomize it
		Random rand = Random.getInstance();


		    Collections.shuffle(_randomVariableIndex, rand);


	}

	/**
	 *
	 * @return
	 */

	private void fillVariableArrayRandomly( Variable[] WorkingDataVariables) {

		randomiseVariableIndex(WorkingDataVariables.length);
		_VariableArray= new ArrayList<Variable>(WorkingDataVariables.length);

		for(int i =0 ; i < WorkingDataVariables.length ; i++) {
			_VariableArray.add(i, WorkingDataVariables[_randomVariableIndex.get(i)]);
		}
		System.out.println("Total number of manifest Variables in Layer1:" +_VariableArray.size());
	}

	/**
	 * Build One layer: Algo:2
	 *
	 * @param _data
	 * @param Level
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 */

	public LTM FastLTA_flat(DataSet _data, int Level) throws FileNotFoundException, UnsupportedEncodingException {

		int i = 1;
		initialize(_data);
		System.out.println("===========Building Level "+ (Level-1)+ "=============");
		// Call lcmLearner iteratively and learn the LCMs.
		while (!isDone()) {
			System.out.println("======================= Learn Island : " + i
					+ " , number of variables left: " + _VariablesSet.size()
					+ "  =================================");
			if (_VariablesSet.size() == 3) {
				if (_mis.isEmpty()) {
					// compute MI and find the pair with the largest MI value
					long startMI = System.currentTimeMillis();
					_mis = computeMis(_data);
					System.out.println("======================= _mis has been calculated  =================================");
					System.out.println("--- ComputingMI Time: "
							+ (System.currentTimeMillis() - startMI)
							+ " ms ---");

				}
				ArrayList<Variable> bestP = new ArrayList<Variable>();
				findBestPair(bestP, _VariablesSet);
			//	System.out.println("Best Pair " + bestP.get(0).getName() +" and " + bestP.get(1).getName());
				ArrayList<Variable> Varstemp =
						new ArrayList<Variable>(_VariablesSet);
				DataSet data_proj = _data.project(Varstemp);
				LTM subModel = LCM3N(Varstemp, data_proj);
				updateHierarchies(subModel, bestP);
				updateVariablesSet(subModel);
				break;
			}

			ArrayList<Variable> bestPair = new ArrayList<Variable>();
			// _mis only needs to compute once

			if (_mis.isEmpty()) {
				// compute MI and find the pair with the largest MI value
				long startMI = System.currentTimeMillis();
				if (Level ==2) {
					_mis = computeMis(_data,_Variables,"cosine");
				}
				else
					_mis = computeMis( _data);
				bestPair.add(0,_VariableArray.get(0)); // adding first variable to best pair randomly


				System.out.println("======================= _mis has been calculated  =================================");
				System.out.println("--- ComputingMI Time: "
						+ (System.currentTimeMillis() - startMI) + " ms ---");

			} else {
				bestPair.add(0,_VariableArray.get(0)); // adding first variable to best pair randomly
			}

			Set<Variable> cluster = new HashSet<Variable>(bestPair);
			// try to find the closest variable to make the cluster have 2 and 3
			// variables now.
			for (int k =0 ; k< 2 ; k++) {
				ArrayList<Variable> ClosestVariablePair =
						findShortestOutLink(_mis, null, cluster, _VariablesSet);
				if (!ClosestVariablePair.isEmpty()) {
					if (k==0)
						bestPair.add(1,ClosestVariablePair.get(1)); // add another variable to best pair that was closest to the first variable of best pair
					cluster.add(ClosestVariablePair.get(1));
				}
			}

			LTM subModel = null;
			// cluster is the working set
			while (true) {
				ArrayList<Variable> ClosestVariablePair =
						findShortestOutLink(_mis, bestPair, cluster,
								_VariablesSet);
				cluster.add(ClosestVariablePair.get(1));

				if (_VariablesSet.size() - cluster.size() == 0
						|| (cluster.size() >= _maxIsland && (_VariablesSet.size() - cluster.size()) >= 3)) { // unimodel is better so we should contine to add more vars but we either ran out of vars, or exceeded maxisland(while there were more than 3 more vars left)

					LTM LCM = LTM.createLCM(new ArrayList<Variable>(cluster), 2);
					LCM.randomlyParameterize();

					DataSet data_proj2l =
							_data.project(new ArrayList<Variable>(cluster)); // projected data for current working set

					subModel = islandEM(LCM,data_proj2l);// run IslandEM

					updateHierarchies(subModel, bestPair);
					updateVariablesSet(subModel);
					updateVariableArray(subModel);
					break;
				}
			}
			i++;
		}
		LTM latentTree = null;

		// link the islands.
		if (_hierarchies.keySet().size()<= _maxTop)
			latentTree = BuildLatentTree(_data);
		else
			latentTree = PoolIslands();


		return latentTree;
	}


	private LTM islandEM(LTM model, DataSet data) {
		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_IslandEmMaxSteps);
		emLearner.setNumberOfRestarts(_IslandEmNumRestarts);
		emLearner.setReuseFlag(true);
		emLearner.setThreshold(_emThreshold);

		LTM newModel =
				(LTM) emLearner.em(model,
						data);

		return newModel ;

	}
	/**
	 * Learn a 3 node LCM
	 *
	 */
	private LTM LCM3N(ArrayList<Variable> variables3, DataSet data_proj) {
		LTM LCM_new = LTM.createLCM(variables3, 2);


		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);

		LCM_new = (LTM) emLearner.em(LCM_new, data_proj.project(variables3));

		return LCM_new;
	}

	private LTM EmLCM_learner(LTM modelold, Variable x,
			ArrayList<Variable> bestPair, DataSet data_proj) {

		ArrayList<Variable> cluster3node = new ArrayList<Variable>(bestPair);
		cluster3node.add(x);
		// Learn a 3node LTM : bestpair and newly added node
		LTM LCM3var = LTM.createLCM(cluster3node, 2);
		LCM3var.randomlyParameterize();
		HashSet<String> donotUpdate = new HashSet<String>();

		ArrayList<Variable> var2s =
				new ArrayList<Variable>(
						LCM3var.getNode(bestPair.get(0)).getCpt().getVariables()); // get the variables involved in the cpt of first var in bestpair
		LCM3var.getNode(bestPair.get(0)).getCpt().setCells(var2s,
				modelold.getNode(bestPair.get(0)).getCpt().getCells()); // get the cpt of first var in best pair and copy that to LCM3var
		donotUpdate.add(bestPair.get(0).getName()); // do not update this cpt during em

		// do the same as above for second var of best pair
		var2s =
				new ArrayList<Variable>(
						LCM3var.getNode(bestPair.get(1)).getCpt().getVariables());
		LCM3var.getNode(bestPair.get(1)).getCpt().setCells(var2s,
				modelold.getNode(bestPair.get(1)).getCpt().getCells());
		donotUpdate.add(bestPair.get(1).getName());

		// do the same as above for root
		var2s =
				new ArrayList<Variable>(
						LCM3var.getRoot().getCpt().getVariables());
		LCM3var.getRoot().getCpt().setCells(var2s,
				modelold.getRoot().getCpt().getCells());
		donotUpdate.add(LCM3var.getRoot().getName());

		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);
		emLearner.setDontUpdateNodes(donotUpdate);
		LCM3var = (LTM) emLearner.em(LCM3var, data_proj.project(cluster3node)); // keeping all param fixed , just learn the cpt between new node and root

		LTM uniModel = modelold.clone();

		uniModel.addNode(x); // add the new node in the original model

		uniModel.addEdge(uniModel.getNode(x), uniModel.getRoot()); // add the edge for this new node
		ArrayList<Variable> vars =
				new ArrayList<Variable>(
						uniModel.getNode(x).getCpt().getVariables()); // get the variables of cpt of this new node i.e. this node and root
		uniModel.getNode(x).getCpt().setCells(vars,
				LCM3var.getNode(x).getCpt().getCells()); // copy this cpt from LCM3var

		return uniModel;
	}

	/**
	 * Closest pair moved out of root of m1 and added under a new latent variable
	 * @param unimodel
	 * @param bestPair
	 * @param ClosestPair
	 * @param data_proj
	 * @return a model with two latent variables (without node relocation step)
	 */

	private LTM EmLTM_2L_learner(LTM unimodel, ArrayList<Variable> bestPair,
			ArrayList<Variable> ClosestPair, DataSet data_proj) {

		ArrayList<Variable> cluster2BeAdded =
				new ArrayList<Variable>(unimodel.getManifestVars()); // stores all manifest vars that are yet to be added to any lcm
		ArrayList<Variable> cluster4var = new ArrayList<Variable>(bestPair);

		// construct a LTM with 4 observed variables 2 latent variables
		LTM lCM = new LTM();
		BeliefNode h2 = lCM.addNode(new Variable(2));
		BeliefNode h1 = lCM.addNode(new Variable(2));

		// first latent var is connected to best pair variables
		for (Variable var : bestPair) {
			lCM.addEdge(lCM.addNode(var), h1);
			cluster2BeAdded.remove(var);

		}

		// second latnt var is connected to closest pair variables
		for (Variable var : ClosestPair) {
			lCM.addEdge(lCM.addNode(var), h2);
			cluster4var.add(var);
			cluster2BeAdded.remove(var);

		}
		lCM.addEdge(h2, h1); // add edge between the two latent variables. h1 is root

		// copy parameters of unimodel i.e m1 to lCM , of root i.e.h1, bestpairs variables and donot update their cpt during em
		HashSet<String> donotUpdate = new HashSet<String>();
		ArrayList<Variable> var1 =
				new ArrayList<Variable>(lCM.getRoot().getCpt().getVariables());
		lCM.getRoot().getCpt().setCells(var1,
				unimodel.getRoot().getCpt().getCells());

		ArrayList<Variable> var2s =
				new ArrayList<Variable>(
						lCM.getNode(bestPair.get(0)).getCpt().getVariables());
		lCM.getNode(bestPair.get(0)).getCpt().setCells(var2s,
				unimodel.getNode(bestPair.get(0)).getCpt().getCells());
		var2s =
				new ArrayList<Variable>(
						lCM.getNode(bestPair.get(1)).getCpt().getVariables());
		lCM.getNode(bestPair.get(1)).getCpt().setCells(var2s,
				unimodel.getNode(bestPair.get(1)).getCpt().getCells());

		donotUpdate.add(h1.getName());
		donotUpdate.add(bestPair.get(0).getName());
		donotUpdate.add(bestPair.get(1).getName());




		ParallelEmLearner emLearner = new ParallelEmLearner();
		emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
		emLearner.setMaxNumberOfSteps(_EmMaxSteps);
		emLearner.setNumberOfRestarts(_EmNumRestarts);
		emLearner.setReuseFlag(false);
		emLearner.setThreshold(_emThreshold);
		emLearner.setDontUpdateNodes(donotUpdate);

		LTM LTM4var = (LTM) emLearner.em(lCM, data_proj.project(cluster4var));

		// Add the rest of variables to m1 (unimodel) and copy parameters
		LTM multimodel = LTM4var.clone();
		for (Variable v : cluster2BeAdded) {

			multimodel.addEdge(multimodel.addNode(v), multimodel.getRoot());// add the remaining vars of m1 to the mutlimodel with h1 latenet variable
			var2s =
					new ArrayList<Variable>(
							multimodel.getNode(v).getCpt().getVariables());
			multimodel.getNode(v).getCpt().setCells(var2s,
					unimodel.getNode(v).getCpt().getCells()); // copy param from m1 of this newly added node
		}

		return multimodel;
	}


	/**
	 * Update the collection of hierarchies.
	 */
	private void updateHierarchies(LTM subModel, ArrayList<Variable> bestPair) {
		BeliefNode root = subModel.getRoot();
		_bestpairs.put(root.getName(), bestPair);
		// add new hierarchy
		_hierarchies.put(root.getVariable(), subModel);

	}

	/**
	 * Update variable set.
	 *
	 * @param subModel
	 */
	private void updateVariablesSet(LTM subModel) {
		BeliefNode root = subModel.getRoot();

		for (DirectedNode child : root.getChildren()) {
			_VariablesSet.remove(((BeliefNode) child).getVariable());
		}
	}


	/**
	 * Update variable array.
	 *
	 * @param subModel
	 */
	private void updateVariableArray(LTM subModel) {
		BeliefNode root = subModel.getRoot();

		for (DirectedNode child : root.getChildren()) {
			if(!_VariableArray.remove(((BeliefNode) child).getVariable())) {
				try {
					throw new Exception("the island variables were not in the _variableArray");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}
	}

	/**
	 * Pool all the islands together into a single BN , without connecting the latent variables
	 * @author fkhawar
	 * @return
	 */
	private LTM PoolIslands() {
		LTM PoolIslands = new LTM();

		// First, add all manifest nodes and latent nodes.
		// Second, copy the edges and CPTs in each LCMs.
		for (Variable var : _hierarchies.keySet()) {
			LTM tempTree = _hierarchies.get(var);

			// copy nodes of each island
			for (AbstractNode node : tempTree.getNodes()) {
				PoolIslands.addNode(((BeliefNode) node).getVariable());
			}

			// copy the edges and CPTs
			for (AbstractNode node : tempTree.getNodes()) {
				BeliefNode bNode = (BeliefNode) node;

				if (!bNode.isRoot()) {
					BeliefNode parent = (BeliefNode) bNode.getParent();

					BeliefNode newNode =
							PoolIslands.getNode(bNode.getVariable());
					BeliefNode newParent =
							PoolIslands.getNode(parent.getVariable());

					PoolIslands.addEdge(newNode, newParent);
					newNode.setCpt(bNode.getCpt().clone()); // copy the parameters of manifest variables
				}else {
					PoolIslands.getNodeByName(node.getName()).setCpt(
							bNode.getCpt().clone());
				}
			}
		}

		return PoolIslands;
	}


	 /**
	 * Do hardassignment for each island. Note that hardassignment is done use the working data and not the original data. For minimizing inference
	 * @author fkhawar
	 * @param _data the working dataset that is to be hardassigned
	 */

	private DataSet IslandHardAssign(DataSet _data) {

		Map<Variable, Integer> varIdx = _data.createVariableToIndexMap();// create a map from the manifest variables in this dataset to their index in the dataset

		Variable[] LatentVars = _hierarchies.keySet().toArray(new Variable[0]); // These will be the variable of the new DataSet

		ArrayList<DataCase> data = _data.getData(); // get the arraylist of datacases

		int[][] newData = new int[data.size()][LatentVars.length];// array that will stor the hardassigned data

		DataSet da = new DataSet(LatentVars); // the new hard assigned dataset

		HashMap<Integer,HashMap<IntList,Integer>> LatentVariableToDatasetMap = new HashMap<Integer,HashMap<IntList,Integer>>(); // map from the index of latent variable in LatentVars to the value. The value is a map with key as the array that contains the unique states for this latent variable an dvalue as the hardassigned value of the latent varible for this datacse

		// saving the ctp for each island in a map for future use
		HashMap<Integer,CliqueTreePropagation> latentIndextoCtpMap = new HashMap<Integer,CliqueTreePropagation>();
		for (int l =0 ; l < LatentVars.length ; l++ ) {
			CliqueTreePropagation ctp = new CliqueTreePropagation(_hierarchies.get(LatentVars[l]));
			latentIndextoCtpMap.put(l, ctp);
		}

		// update for every data case
		for (int j = 0; j < data.size(); j++) {

			DataCase dataCase = data.get(j); // get the datacase

			int[] states = dataCase.getStates();


			//for (Variable latent : LatentVars) {
			for (int k = 0; k < LatentVars.length; k++) {

				Variable latent = LatentVars[k];

				LTM island = _hierarchies.get(latent); // get the island

				Variable[] islandVars =
						island.getManifestVars().toArray(new Variable[0]);

				// Initialize the map for this latent vatiable
				if (!LatentVariableToDatasetMap.containsKey(k)) {
					HashMap<IntList,Integer> states2HardAssignValueMap = new HashMap<IntList,Integer>();
					LatentVariableToDatasetMap.put(k, states2HardAssignValueMap);
				}

				int nislandVars = islandVars.length;
				int[] subStates = new int[nislandVars];
				IntList projStates = new IntArrayList(); // same as subStates but its an IntList

				// project states
				for (int i = 0; i < nislandVars; i++) {
					subStates[i] = states[varIdx.get(islandVars[i])];
					projStates.add(i, states[varIdx.get(islandVars[i])]);
				}

				if(LatentVariableToDatasetMap.get(k).containsKey(projStates)) {
					newData[j][k] = LatentVariableToDatasetMap.get(k).get(projStates); // get the already computed hardassigned value

					// for printing only those cases which has non zero states
					/*int size = 0;
					for (int l =0 ; l< projStates.size() ; l++) {
						size+=projStates.get(l);
					}
					if(size > 0) {
					try {
						throw new Exception("The states:"+projStates+ " were found in map for latent variable: "+k);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					}*/
				}
				else {
				// set evidence and propagate
				CliqueTreePropagation ctp = latentIndextoCtpMap.get(k);	// got the ctp

				ctp.setEvidence(islandVars, subStates);
				ctp.propagate();

				// compute P(Y|d)
				Function post = ctp.computeBelief(latent);

				// hardAssign the datacase
				double cell = 0;
				int assign = 0;

				for (int l = 0; l < post.getDomainSize(); l++) {
					if (post.getCells()[l] > cell) {
						cell = post.getCells()[l];
						assign = l;
					}
				}

				newData[j][k] = assign;

				LatentVariableToDatasetMap.get(k).put(projStates,assign);

				}

			}

			da.addDataCase(newData[j], data.get(j).getWeight()); // add the new hardassigned datacase

		}

		return da;

	}



	private LTM BuildLatentTree(DataSet _data) throws FileNotFoundException, UnsupportedEncodingException {

		long LatentPostTime = System.currentTimeMillis();
		if(_latentPosts.isEmpty())
		{
			for(Variable var : _hierarchies.keySet())
			{
				LTM subModel = _hierarchies.get(var);
				updateStats(subModel,_data);
			}
		}
		System.out.println("Compute Latent Posts Time: " + (System.currentTimeMillis() - LatentPostTime) + " ms ---");

		LTM latentTree = new LTM();

		// Construct tree: first, add all manifest nodes and latent nodes.
		// Second, copy the edges and CPTs in each LCMs.
		for (Variable var : _hierarchies.keySet()) {
			LTM tempTree = _hierarchies.get(var);

			for (AbstractNode node : tempTree.getNodes()) {
				latentTree.addNode(((BeliefNode) node).getVariable());
			}

			// copy the edges and CPTs
			for (AbstractNode node : tempTree.getNodes()) {
				BeliefNode bNode = (BeliefNode) node;

				if (!bNode.isRoot()) {
					BeliefNode parent = (BeliefNode) bNode.getParent();

					BeliefNode newNode =
							latentTree.getNode(bNode.getVariable());
					BeliefNode newParent =
							latentTree.getNode(parent.getVariable());

					latentTree.addEdge(newNode, newParent);
					newNode.setCpt(bNode.getCpt().clone()); // copy the parameters of manifest variables
				}else {
					latentTree.getNodeByName(node.getName()).setCpt(
							bNode.getCpt().clone());
				}
			}
		}

		// learn MST
		UndirectedGraph mst = learnMaximumSpanningTree(_hierarchies, _data);


		// Choose a root with more than 3 observed variables
		Queue<AbstractNode> frontier = new LinkedList<AbstractNode>();
		frontier.offer(mst.getNodes().peek()); // get the head of mst

		// add the edges among latent nodes.
		while (!frontier.isEmpty()) {
			AbstractNode node = frontier.poll();
			DirectedNode dNode =
					(DirectedNode) latentTree.getNode(node.getName());

			for (AbstractNode neighbor : node.getNeighbors()) {// neighbors of the current node from the MST
				DirectedNode dNeighbor =
						(DirectedNode) latentTree.getNode(neighbor.getName());
				if (!dNode.hasParent(dNeighbor)) {
					latentTree.addEdge(dNeighbor, dNode);
					frontier.offer(neighbor);
				}
			}
		}



		ArrayList<Variable> LatVarsOrdered = latentTree.getLatVarsfromTop();
		for(Variable v: LatVarsOrdered){
			if(!latentTree.getNode(v).isRoot()){
				//construct a LTM with 4 observed variables 2 latent variables
				//copy parameters
				HashSet<String> donotUpdate = new HashSet<String>();
				LTM lTM_4n = new LTM();
				BeliefNode parent  = latentTree.getNode(v).getParent();


				BeliefNode h2 = lTM_4n.addNode(new Variable(2));
				BeliefNode h1 = lTM_4n.addNode(new Variable(2));

				for (Variable vtemp :_bestpairs.get(parent.getName())) {
					lTM_4n.addEdge(lTM_4n.addNode(vtemp), h1);
					ArrayList<Variable> var2s = new ArrayList<Variable>(lTM_4n.getNode(vtemp).getCpt().getVariables());
					lTM_4n.getNode(vtemp).getCpt().setCells(var2s, latentTree.getNode(vtemp).getCpt().getCells());
					donotUpdate.add(vtemp.getName());
				}

				for (Variable vtemp : _bestpairs.get(v.getName())){
					lTM_4n.addEdge(lTM_4n.addNode(vtemp), h2);
					ArrayList<Variable> var2s = new ArrayList<Variable>(lTM_4n.getNode(vtemp).getCpt().getVariables());
					lTM_4n.getNode(vtemp).getCpt().setCells(var2s, latentTree.getNode(vtemp).getCpt().getCells());
					donotUpdate.add(vtemp.getName());
				}
				lTM_4n.addEdge(h2, h1);
				LTM temp = _hierarchies.get(parent.getVariable());
				ArrayList<Variable> var2s = new ArrayList<Variable>(lTM_4n.getRoot().getCpt().getVariables());
                lTM_4n.getRoot().getCpt().setCells(var2s, temp.getRoot().getCpt().getCells());
				donotUpdate.add(h1.getName());

				ArrayList<Variable> cluster4var = new ArrayList<Variable>(lTM_4n.getManifestVars());

				ParallelEmLearner emLearner = new ParallelEmLearner();
				emLearner.setLocalMaximaEscapeMethod("ChickeringHeckerman");
				emLearner.setMaxNumberOfSteps(_EmMaxSteps);
				emLearner.setNumberOfRestarts(_EmNumRestarts);
				emLearner.setReuseFlag(false);
				emLearner.setThreshold(_emThreshold);
				emLearner.setDontUpdateNodes(donotUpdate);

				LTM LTM4var = (LTM) emLearner.em(lTM_4n, _data.project(cluster4var));

				ArrayList<Variable> vars = new ArrayList<Variable>(latentTree.getNode(v).getCpt().getVariables());
				latentTree.getNode(v).getCpt().setCells(vars, LTM4var.getNode(h2.getVariable()).getCpt().getCells());
			}
		}

		return latentTree;
	}



	public class EmpiricalMiComputer {
		private final DataSet data;
		private final List<Variable> variables;
		private final boolean normalize;

		public EmpiricalMiComputer(DataSet data, List<Variable> variables,
				boolean normalize) {
			this.data = data;
			this.normalize = normalize;
			this.variables = variables;
		}

		/**
		 * Computes the mutual information between two discrete variables.
		 *
		 * @param discretizedData
		 * @param v1
		 * @param v2
		 * @return
		 * @throws Exception
		 */
		protected double compute(Variable vi, Variable vj) {
			Function pairDist = computeEmpDist(Arrays.asList(vi, vj), data);
			double mi = Utils.computeMutualInformation(pairDist);

			// use normalized version of MI.
			if (normalize) {
				// this version used in Strehl & Ghosh (2002)
				double enti = Utils.computeEntropy(pairDist.sumOut(vj));
				double entj = Utils.computeEntropy(pairDist.sumOut(vi));
				if (mi != 0) {
					mi /= Math.sqrt(enti * entj);
				}
			}

			return mi;
		}

		/**
		 * Computes a the mutual information between each pair of variables. It
		 * does not contain any valid value on the diagonal.
		 *
		 * @param includeClassVariable
		 *            whether to include the class variable
		 * @return mutual information for each pair of variables
		 */
		public double[][] computerPairwise() {
			Implementation implementation = new Implementation();
			implementation.computeParallel();
			return implementation.values;
		}

		/**
		 * Implementation for computing
		 *
		 * @author kmpoon
		 *
		 */
		public class Implementation {
			private double[][] values;

			private Implementation() {
				this.values = new double[variables.size()][variables.size()];
			}

			// private void compute() {
			// computeFirstRange(0, variables.size());
			// }

			private void computeParallel() {
				ForkJoinPool pool = new ForkJoinPool();
				pool.invoke(new ParallelComputation(0, variables.size()));
			}

			private void computeFirstRange(int start, int end) {
				for (int i = start; i < end; i++) {
					computeSecondRange(i, i + 1, variables.size());
				}
			}

			private void computeSecondRange(int base, int start, int end) {
				Variable v1 = variables.get(base);
				for (int j = start; j < end; j++) {
					Variable v2 = variables.get(j);
					values[base][j] = compute(v1, v2);
					values[j][base] = values[base][j];
				}
			}

			@SuppressWarnings("serial")
			public class ParallelComputation extends RecursiveAction {

				private final int start;
				private final int end;
				private static final int THRESHOLD = 10;

				private ParallelComputation(int start, int end) {
					this.start = start;
					this.end = end;
				}

				private void computeDirectly() {
					computeFirstRange(start, end);
				}

				@Override
				protected void compute() {
					int length = end - start;
					if (length <= THRESHOLD) {
						computeDirectly();
						return;
					}

					int split = length / 2;
					invokeAll(new ParallelComputation(start, start + split),
							new ParallelComputation(start + split, end));
				}
			}
		}

	}

	protected ArrayList<double[]> computeMis(DataSet _data) {
		return computeMisByCount(_data);
	}



	protected ArrayList<double[]> computeMisByCount(DataSet _data) {


		EmpiricalMiComputerForBinaryDataStep computer =
				new EmpiricalMiComputerForBinaryDataStep(_data, _Variables);
		ArrayList<double[]> miArray = computer.computerPairwise();

		return  miArray;
	}

	/**
	 * @author fkhawar
	 * @param _data
	 * @param Variables
	 * @param cosine
	 * @return
	 */
	public static ArrayList<double[]> computeMis(DataSet _data,ArrayList<Variable> Variables ,String cosine ) {
		return computeMisByCount(_data,Variables,cosine);
	}

	protected static ArrayList<double[]> computeMisByCount(DataSet _data,ArrayList<Variable> Variables, String cosine ) {


		EmpiricalMiComputerForBinaryDataStep computer =
				new EmpiricalMiComputerForBinaryDataStep(_data, Variables);
		ArrayList<double[]> miArray = computer.computerPairwise(cosine);

		return  miArray;
	}


	/**
	 *
	 * Return the best pair of variables with max MI in _mis.
	 */
	private void findBestPair(ArrayList<Variable> bestPair,
			Set<Variable> VariablesSet) {
		// Initialize vars as _VarisblesSet
		List<Variable> vars = new ArrayList<Variable>(VariablesSet);

		List<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		double maxMi = Double.NEGATIVE_INFINITY;
		Variable first = null, second = null;

		int nVars = vars.size();

		// enumerate all pairs of variables
		for (int i = 0; i < nVars; i++) {
			Variable vi = vars.get(i);
			int iId = _varId.get(vi.getName());
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) {
				Variable vj = vars.get(j);
				varPair.set(1, vj);
				int jId = _varId.get(vj.getName());
				double mi = _mis.get(iId)[jId];

				// update max MI and indices of best pair
				if (mi > maxMi) {
					maxMi = mi;
					first = vi;
					second = vj;
				}
			}
		}

		// set the best pair
		bestPair.add(first);
		bestPair.add(second);
	}

	/**
	 * Compute the empirical distribution of the given pair of variables
	 */
	private Function computeEmpDist(List<Variable> varPair, DataSet _data) {
		Variable[] vars = _data.getVariables();

		Variable vi = varPair.get(0);
		Variable vj = varPair.get(1);

		int viIdx = -1, vjIdx = -1;

		// retrieve P(Y|d) for latent variables and locate manifest variables
		Map<DataCase, Function> viPosts = _latentPosts.get(vi);
		if (viPosts == null) {
			viIdx = Arrays.binarySearch(vars, vi);
		}

		Map<DataCase, Function> vjPosts = _latentPosts.get(vj);
		if (vjPosts == null) {
			vjIdx = Arrays.binarySearch(vars, vj);
		}

		Function empDist = Function.createFunction(varPair);

		for (DataCase datum : _data.getData()) {
			int[] states = datum.getStates();

			// If there is missing data, continue;
			if ((viIdx != -1 && states[viIdx] == -1)
					|| (vjIdx != -1 && states[vjIdx] == -1)) {
				continue;
			}
			// P(vi, vj|d) = P(vi|d) * P(vj|d)
			Function freq;

			if (viPosts == null) {
				freq = Function.createIndicatorFunction(vi, states[viIdx]);
			} else {
				freq = viPosts.get(datum);
			}

			if (vjPosts == null) {
				freq =
						freq.times(Function.createIndicatorFunction(vj,
								states[vjIdx]));
			} else {
				freq = freq.times(vjPosts.get(datum));
			}

			freq = freq.times(datum.getWeight());

			empDist.plus(freq);
		}

		empDist.normalize();

		return empDist;
	}

	/**
	 * Return true if and only if the whole clustering procedure is done, or
	 * equivalently, there is only one hierarchy left.
	 */
	private boolean isDone() {
		return _VariablesSet.size() < 1;
	}

	/**
	 * Find the closest variable to cluster. Note: Never move the bestpair out
	 * Version:          MI(X, S) = max_{Z \in S} MI(X, Z).
	 * @param mis
	 * @param cluster
	 * @return
	 */
	private ArrayList<Variable> findShortestOutLink(
			ArrayList<double[]> mis,
			ArrayList<Variable> bestPair, Set<Variable> cluster,
			Set<Variable> VariablesSet) {
		double maxMi = Double.NEGATIVE_INFINITY;
		Variable bestInCluster = null, bestOutCluster = null;

		for (Variable inCluster : cluster) {
			boolean a = bestPair == null;
			if (a || !bestPair.contains(inCluster)) {
				for(int l = 0; l< mis.get(_varId.get(inCluster.getName())).length;l++ ){
				//for (Entry<Integer, Double> entry : mis.get(_varId.get(inCluster.getName())).entrySet()) {
					Variable outCluster =_Variables.get(l);
					double mi = mis.get(_varId.get(inCluster.getName()))[l];

					// skip variables already in cluster
					if (cluster.contains(outCluster)
							|| !(VariablesSet.contains(outCluster))) {
						continue;
					}

					// keep the variable with max MI.
					if (mi > maxMi) {
						maxMi = mi;
						bestInCluster = inCluster;
						bestOutCluster = outCluster;
					}
				}
			}
		}

		ArrayList<Variable> ClosestVariablePair = new ArrayList<Variable>();
		ClosestVariablePair.add(bestInCluster);
		ClosestVariablePair.add(bestOutCluster);

		return ClosestVariablePair;
	}


	/**
	 * Stack the results
	 * @param _data
	 */
private LTM BuildHierarchy(LTM OldModel, LTM tree) {

		LTM CurrentModel = new LTM(); // the new model to be made
		if (OldModel == null) { // if no old model then just return the curretn model
			return tree;
		}

		CurrentModel = OldModel; // build upon the OldModel


		// get internal variables
		Set<Variable> internalVars = _hierarchies.keySet();// get internal vars of current layer

		// add latent varibles of the new layer to the Current Model

		for (Variable v : internalVars) {
			CurrentModel.addNode(v);
		}
		// then add their edges
		for (Edge e : tree.getEdges()) {
			String head = e.getHead().getName();
			String tail = e.getTail().getName();

			CurrentModel.addEdge(CurrentModel.getNodeByName(head),
					CurrentModel.getNodeByName(tail));
		}



		// set their CPT in the Current Model,
		for(AbstractNode nd: tree.getNodes()){
			BeliefNode bnd  = (BeliefNode)nd;
			if(!bnd.isRoot()){

			ArrayList<Variable> pair = new ArrayList<Variable>();
			pair.add(CurrentModel.getNodeByName(bnd.getName()).getVariable());
			pair.add(CurrentModel.getNodeByName(bnd.getName()).getParent().getVariable());

			double[] Cpt = bnd.getCpt().getCells();

			CurrentModel.getNodeByName(nd.getName()).getCpt().setCells(pair,Cpt);
			}
			// if the node is root then it has no parent so just get its CPT
			else {
				CurrentModel.getNodeByName(nd.getName()).setCpt(
						bnd.getCpt().clone());
			}
		}

		return CurrentModel;
	}




	 private double evaluate(BayesNet _modelEst2){
	 double Loglikelihood=
	  ScoreCalculator.computeLoglikelihood((BayesNet)_modelEst2, _test);
	 double perLL = Loglikelihood/_test.getTotalWeight();
	  System.out.println("Per-document log-likelihood =  "+perLL);
	 return perLL;
	 }




	/**
	 * Initialize before building each layer
	 *
	 * @param data
	 */

	protected void initialize(DataSet data) {
		//System.out.println("=== Initialization ===");

		// initialize data structures for P(Y|d).
		_latentPosts = new HashMap<Variable, Map<DataCase, Function>>();

		// initialize hierarchies
		// _hirearchies will be used to keep all LCMs found by U-test.
		_hierarchies = new HashMap<Variable, LTM>();

		_Variables = new ArrayList<Variable>();
		_VariablesSet = new HashSet<Variable>();
		_mis = new ArrayList<double[]>();

		// add all manifest variable to variable set _VariableSet.
		for (Variable var : data.getVariables()) {
			_VariablesSet.add(var);
			_Variables.add(var);
		}

		_varId = new HashMap<String, Integer>();
		for(int i =0;i<_Variables.size();i++){
			_varId.put(_Variables.get(i).getName(), i);
		}

		// create _VariableArray from _VariablesSet
		fillVariableArrayRandomly( data.getVariables());
	}




	private LTM postProcessingModel(LTM model) {
		HashMap<Integer, HashSet<String>> varDiffLevels =
				processVariables(model);
		HashMap<Integer, Integer> levelAndIndex =
				new HashMap<Integer, Integer>();

		// reorderStates first.
		model = reorderStates(model, varDiffLevels);

		int topLevelIndex = varDiffLevels.size() - 1;

		for (int i = 1; i < topLevelIndex + 1; i++) {
			levelAndIndex.put(i, 0);
		}

		HashSet<String> topLevel = varDiffLevels.get(topLevelIndex);

		// renameVariables
		for (String str : topLevel) {
			processName(model, str, topLevelIndex, levelAndIndex, varDiffLevels);
		}

		model = smoothingParameters(model);

		return model;
	}

	private LTM reorderStates(LTM bn,
			HashMap<Integer, HashSet<String>> varDiffLevels) {
		// inference engine
		CliqueTreePropagation ctp = new CliqueTreePropagation(bn);
		ctp.clearEvidence();
		ctp.propagate();
		Variable[] latents = new Variable[1];
		int[] states = new int[1];

		// reorder states for each latent variable
		for (Variable latent : bn.getInternalVars("tree")) {
			latents[0] = latent;

			// calculate severity of each state
			int card = latent.getCardinality();
			double[] severity = new double[card];
			int VarLevel = 0;
			for (int key : varDiffLevels.keySet()) {
				HashSet<String> set = varDiffLevels.get(key);
				if (set.contains(latent.getName())) {
					VarLevel = key;
					break;
				}
			}

			Set<DirectedNode> setNode = new HashSet<DirectedNode>();
			Set<DirectedNode> childSet = bn.getNode(latent).getChildren();
			for (DirectedNode node : childSet) {
				Variable var = ((BeliefNode) node).getVariable();

				if (!varDiffLevels.get(VarLevel - 1).contains(var.getName()))
					continue;

				if (((BeliefNode) node).isLeaf()) {
					setNode.add((DirectedNode) node);
				} else {
					for (AbstractNode nodeDes : bn.getNode(var).getDescendants()) {
						if (((BeliefNode) nodeDes).isLeaf()) {
							setNode.add((DirectedNode) nodeDes);
						}
					}
				}
			}
			List<Map.Entry<Variable, Double>> list =
					SortChildren(latent, setNode, ctp);
			ArrayList<Variable> collectionVar = new ArrayList<Variable>();
			if (list.size() > 3) {
				for (int Id = 0; Id < 3; Id++) {
					collectionVar.add(list.get(Id).getKey());
				}
			} else {
				for (DirectedNode node : setNode) {
					Variable manifest = ((BeliefNode) node).getVariable();
					collectionVar.add(manifest);
				}
			}
			for (int i = 0; i < card; i++) {
				states[0] = i;

				ctp.setEvidence(latents, states);
				ctp.propagate();

				// accumulate expectation of each manifest variable

				for (Variable manifest : collectionVar) {
					double[] dist = ctp.computeBelief(manifest).getCells();

					for (int j = 1; j < dist.length; j++) {
						severity[i] += Math.log(j * dist[j]);
					}
				}

			}

			// initial order
			int[] order = new int[card];
			for (int i = 0; i < card; i++) {
				order[i] = i;
			}

			// for More than 2 states,but now we don't need bubble sort
			// bubble sort

			  for (int i = 0; i < card - 1; i++) {
				  for (int j = i + 1; j < card; j++) {
					  if (severity[i] > severity[j]) {
						  int tmpInt = order[i];
						  order[i] = order[j];
						  order[j] = tmpInt;

						  double tmpReal = severity[i];
						  severity[i] = severity[j];
						  severity[j] = tmpReal;
					  }
				  }
			  }

			// reorder states
			bn.getNode(latent).reorderStates(order);
			latent.standardizeStates();
		}

		return bn;
	}

	private void processName(BayesNet model, String str, int level,
			HashMap<Integer, Integer> levelAndIndex,
			HashMap<Integer, HashSet<String>> varDiffLevels) {
		if (varDiffLevels.get(0).contains(str))
			return;

		Set<DirectedNode> set = model.getNodeByName(str).getChildren();

		changeName(model, str, level, levelAndIndex);

		for (DirectedNode child : set) {
			if (!varDiffLevels.get(level - 1).contains(child.getName()))
				continue;

			processName(model, child.getName(), level - 1, levelAndIndex,
					varDiffLevels);
		}
	}

	private void changeName(BayesNet model, String str, int level,
			HashMap<Integer, Integer> levelAndIndex) {
		BeliefNode var = model.getNodeByName(str);

		int index = levelAndIndex.get(level) + 1;
		String newName = "Z" + level + index;
		var.setName(newName);

		levelAndIndex.put(level, index);
	}

	private HashMap<Integer, HashSet<String>> processVariables(BayesNet model) {
		HashMap<Integer, HashSet<String>> varDiffLevels =
				new HashMap<Integer, HashSet<String>>();

		Set<Variable> internalVar = model.getInternalVars("tree");
		Set<Variable> leafVar = model.getLeafVars("tree");

		HashSet<String> levelOne = new HashSet<String>();
		for (Variable v : leafVar) {
			levelOne.add(v.getName());
		}
		varDiffLevels.put(0, levelOne);

		int level = 0;
		while (internalVar.size() > 0) {
			HashSet<String> levelVar = varDiffLevels.get(level);
			level++;

			HashSet<String> newSet = new HashSet<String>();
			for (String s : levelVar) {
				String parent = model.getNodeByName(s).getParent().getName();

				if (parent != null) {
					internalVar.remove(model.getNodeByName(parent).getVariable());
					newSet.add(parent);
				}
			}
			varDiffLevels.put(level, newSet);
		}

		return varDiffLevels;
	}

	private List<Map.Entry<Variable, Double>> SortChildren(Variable var,
			Set<DirectedNode> nodeSet, CliqueTreePropagation ctp) {
		Map<Variable, Double> children_mi = new HashMap<Variable, Double>();

		for (DirectedNode node : nodeSet) {
			Variable child = ((BeliefNode) node).getVariable();
			double mi = computeMI(var, child, ctp);
			children_mi.put(child, mi);
		}

		List<Map.Entry<Variable, Double>> List =
				Utils.sortByDescendingOrder(children_mi);

		return List;
	}

	private double computeMI(Variable x, Variable y, CliqueTreePropagation ctp) {
		List<Variable> xyNodes = new ArrayList<Variable>();
		xyNodes.add(x);
		xyNodes.add(y);

		return Utils.computeMutualInformation(ctp.computeBelief(xyNodes));
	}





	/**
	 * Regular way of smoothing
	 *
	 */
	private LTM smoothingParameters(LTM model)
	{
		for(AbstractNode node : model.getNodes())
		{
			Function fun = ((BeliefNode)node).getCpt();

			for(int i=0; i<fun.getDomainSize(); i++)
			{
				fun.getCells()[i] = (fun.getCells()[i]*_OrigDenseData.getTotalWeight()+1)/(_OrigDenseData.getTotalWeight()+ ((BeliefNode)node).getVariable().getCardinality());
			}
		}
		return model;
	}


	/**
	 * Update the collections of P(Y|d). Specifically, remove the entries for
	 * all the latent variables in the given sub-model except the root, and
	 * compute P(Y|d) for the latent variable at the root and each data case d.
	 */
	private void updateStats(LTM subModel, DataSet _data) {
		BeliefNode root = subModel.getRoot();
		Variable latent = root.getVariable();
		// Function prior = root.getCpt();

		for (DirectedNode child : root.getChildren()) {
			_latentPosts.remove(((BeliefNode) child).getVariable());
		}

		Map<DataCase, Function> latentPosts = new HashMap<DataCase, Function>();

		CliqueTreePropagation ctp = new CliqueTreePropagation(subModel);

		Map<Variable, Integer> varIdx = _data.createVariableToIndexMap();

		Variable[] subVars =
				subModel.getManifestVars().toArray(new Variable[0]);
		int nSubVars = subVars.length;
		int[] subStates = new int[nSubVars];

		// update for every data case
		for (DataCase dataCase : _data.getData()) {
			// project states
			int[] states = dataCase.getStates();
			for (int i = 0; i < nSubVars; i++) {
				subStates[i] = states[varIdx.get(subVars[i])];
			}

			// set evidence and propagate
			ctp.setEvidence(subVars, subStates);
			ctp.propagate();

			// compute P(Y|d)
			Function post = ctp.computeBelief(latent);
			latentPosts.put(dataCase, post);
		}

		_latentPosts.put(latent, latentPosts);
	}

	private UndirectedGraph learnMaximumSpanningTree(
			Map<Variable, LTM> hierarchies, DataSet _data) {
		// initialize the data structure for pairwise MI
		List<StringPair> pairs = new ArrayList<StringPair>();

		// the collection of latent variables.
		List<Variable> vars = new ArrayList<Variable>(hierarchies.keySet());

		List<Variable> varPair = new ArrayList<Variable>(2);
		varPair.add(null);
		varPair.add(null);

		int nVars = vars.size();

		// enumerate all pairs of latent variables
		for (int i = 0; i < nVars; i++) {
			Variable vi = vars.get(i);
			varPair.set(0, vi);

			for (int j = i + 1; j < nVars; j++) {
				Variable vj = vars.get(j);
				varPair.set(1, vj);

				// compute empirical MI
				Function pairDist = computeEmpDist(varPair,_data);
				double mi = Utils.computeMutualInformation(pairDist);

				// keep both I(vi; vj) and I(vj; vi)
				pairs.add(new StringPair(vi.getName(), vj.getName(), mi));
			}
		}

		// sort the pairwise MI.
		Collections.sort(pairs);

		// building MST using Kruskal's algorithm
		UndirectedGraph mst = new UndirectedGraph();

		// nVars = latentTree.getNumberOfNodes();
		HashMap<String, ArrayList<String>> components =
				new HashMap<String, ArrayList<String>>();
        //move the node with more than 2 variables to the first place
		boolean flag = false;
		for (Variable var : hierarchies.keySet()) {
			String name = var.getName();
			mst.addNode(name);

			if(hierarchies.get(var).getLeafVars().size()>=3&& !flag){
				mst.move2First(name);
				flag = true;
			}
			ArrayList<String> component = new ArrayList<String>(nVars);
			component.add(name);
			components.put(name, component);
		}

		// examine pairs in descending order w.r.t. MI
		for (int i = pairs.size() - 1; i >= 0; i--) {
			StringPair pair = pairs.get(i);
			String a = pair.GetStringA();
			String b = pair.GetStringB();
			ArrayList<String> aComponent = components.get(a);
			ArrayList<String> bComponent = components.get(b);

			// check whether a and b are in the same connected component
			if (aComponent != bComponent) {
				// connect nodes
				mst.addEdge(mst.getNode(a), mst.getNode(b));

				if (aComponent.size() + bComponent.size() == nVars) {
					// early termination: the tree is done
					break;
				}

				// merge connected component
				aComponent.addAll(bComponent);
				for (String c : bComponent) {
					components.put(c, aComponent);
				}
			}
		}

		return mst;
	}



}

