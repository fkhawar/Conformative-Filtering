/**
 * CliqueTreePropagation.java 
 * Tao Chen, Kin Man Poon, Yi Wang,  Nevin L. Zhang, and Farhan Khawar (implemented restricted propagation)
 */
package org.latlab.reasoner;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.latlab.graph.AbstractNode;
import org.latlab.graph.DirectedNode;
import org.latlab.model.BayesNet;
import org.latlab.model.BeliefNode;
import org.latlab.model.LTM;
import org.latlab.util.DataSet;
import org.latlab.util.Function;
import org.latlab.util.Variable;


/**
 * This class provides an implementation for clique tree propagation (CTP)
 * algorithm.
 * 
 * @author Yi Wang
 * 
 *         Be careful about the type of model. The method to construct the
 *         clique tree and the method to do the propagation for general bayesnet
 *         and for LTM are different.
 * 
 *         If you are working with non-LTM bayesnet, set the model as class
 *         "BayesNet"; otherwise, you can set it as class "LTM".
 * 
 * @author LIU Tengfei
 * 
 * @author fkhawar : made some additions for focusedsubtree propagation, to scale propagation
 *  to huge trees like the ones used in recommender systems
 * 
 * 
 */
public final class CliqueTreePropagation implements Cloneable {

	/**
	 * The BN under query.
	 */
	private BayesNet _bayesNet;

	/**
	 * The CT used by this CTP.
	 */
	private CliqueTree _cliqueTree;

	/**
	 * 
	 */
	private Map<Variable, Integer> _evidence = new HashMap<Variable, Integer>();

	private double lastLogLikelihood = Double.NaN;
	
	/**
	 *  Stores the messages sent by each clique node,when there the evidence set is 
	 *  0 for all observed variables, both in collect and distribute phase. The key of the map is a 
	 *  particular clique node and the entry is the Map containing the _msgs of that clique node.
	 *  @author fkhawar
	 */
	private Map<String,Map<AbstractNode,Function>> _defaultMessages = null;

	
	private HashMap<String, HashMap<AbstractNode, Double>> _defaultAlphas = null;
	private HashMap<String, HashMap<AbstractNode, Double>> _defaultLogAlphas = null;
	private HashMap<String,LinkedList<Function>> _defaultfunctions = null;
	private HashMap<String,LinkedHashSet<CliqueNode>> _defualtQualifiedNeiMsgs = null;
	private HashMap<String,Function> _defaultmsgsProd = null;
	
	
	/**
	 * A HashMap that contains all the descendants cliques of a top level latent variable including the cliques of the toplevel variable. The key of the map is the name of the 
	 * top level variable and the value is the set of descendant variables' clique nodes from the clique tree.
	 * 
	 * @author fkhawar
	 */
	private Map<String,Set<CliqueNode>> _mapTopLevelLatentVariableDescendantCliques = null;
	
	
	/**
	 * Map that contains the path from source top level latent variable name to the destination top level variable name. The path is a set of cliqueNodes
	 * corresponding to the variables in the path i.e. variable cliques and family cliques of the path variables.
	 * 
	 * The key of the map is a source top level variable name and the value is another map whose key is the destination top level variable name and the value
	 * is the set of clique nodes on the path from source to destination. Path includes source and destination cliques.
	 * 
	 * Contains the path from source to destination and destination to source even though they are same
	 * 
	 * @author fkhawar
	 */
	private Map<String, HashMap<String, HashSet<CliqueNode>>> _mapPath =null;
	
	/**
	 * Map from each leaf variable to the name of its corresponding top level latent variable. Along with leaves we also store the evidence variables as teh 
	 * keys in the map in case all evidence variables are not leaf variables
	 * 
	 * @author fkhawar
	 */
	private HashMap<Variable,String> _mapLeafVariableToToplevelVariable =null;
	
	private Set<Variable> _evidenceVariables =null;
 	
	private Set<AbstractNode> _topVariablesNeighborCliques = null;
	/**
	 * Dummy constructor. It is supposed that only
	 * <code>CliqueTreePropagation.clone()</code> will invoke it.
	 */
	private CliqueTreePropagation() {
	}

	/**
	 * Constructs a CTP for the specified HLCM. The CliqueTree is the socalled
	 * natural clique tree for HLCM .
	 * 
	 * @param LTM
	 *            model under query.
	 */
	public CliqueTreePropagation(LTM model) {
		_bayesNet = model;
		set_cliqueTree(new CliqueTree(model));
		set_evidence(new HashMap<Variable, Integer>());
	}

	/**
	 * @param model
	 *            HLCM under query.
	 * @param families
	 *            To define the focusedsubtree.
	 * @param variables
	 *            To define the focusedsubtree.
	 */
	public CliqueTreePropagation(LTM model, Variable[] families,
			Variable[] variables) {
		_bayesNet = model;
		set_cliqueTree(new CliqueTree(model, families, variables));
		set_evidence(new HashMap<Variable, Integer>());
	}

	/**
	 * <p>
	 * Collects all the messages of all the clique nodes in this clique tree when all the leaf variables are 
	 * set to evidence = 0; And then returns these messages.
	 * </p>
	 * 
	 * <p>
	 * Creates a map with the key as a clique node and the value as another map that contains the messages of this
	 * cliques to other cliques. The key of the value map is the destination cliques and the value is the message
	 * sent to this destination clique.
	 * </p>
	 * @author fkhawar
	 */
	
	public Map<String, Map<AbstractNode, Function>>  makeDefaultMessageMap() {

		System.out.println("Making Default Messages...");
		
		long startDM = System.currentTimeMillis();
		
		System.out.println("--- Making Default Messages Time: "+ (System.currentTimeMillis() - startDM) + " ms ---");
		
		
		// clear the focusedsubtree if any
		get_cliqueTree().resetFocusedSubtree();
		
			
		// set all leaf variables of _bayesNet to evidence 0
		setPositiveOnlyEvidence( null, _bayesNet.getLeafVars());
		
		// propagate all the messages
		propagate();
		
		// save messages outgoing from all clique nodes
		_defaultMessages = new HashMap<String,Map<AbstractNode,Function>>();
		_defaultAlphas = new HashMap<String, HashMap<AbstractNode, Double>>(); 
		_defaultLogAlphas = new HashMap<String, HashMap<AbstractNode, Double>>(); 
		_defaultfunctions = new HashMap<String,LinkedList<Function>>();
		_defualtQualifiedNeiMsgs = new HashMap<String,LinkedHashSet<CliqueNode>>();
		_defaultmsgsProd = new HashMap<String,Function>();
		
		
		// save messages for all clique nodes i.e. variableCliques and familyCliques
		for (AbstractNode node : get_cliqueTree().getNodes()) {
			
	
			String nodeName = node.getName();
			_defaultMessages.put(nodeName, ((CliqueNode)node).getMessages());
			_defaultAlphas.put(nodeName,((CliqueNode)node).getNormalization());
			_defaultLogAlphas.put(nodeName,((CliqueNode)node).getLogNormalization());
			
			_defaultfunctions.put(nodeName,((CliqueNode)node).getFunction());
			_defualtQualifiedNeiMsgs.put(nodeName,((CliqueNode)node).getQualifiedNeiMsgs());
			_defaultmsgsProd.put(nodeName,((CliqueNode)node).getmsgsProd());
			
	
		}
		
		return _defaultMessages;
		
	}
	
	public Map<String, Map<AbstractNode, Function>>  makeDefaultMessageMap(String dummy) {

		// clear the focusedsubtree if any
		get_cliqueTree().resetFocusedSubtree();
		
			
		
		get_evidence().clear();
		
		// propagate all the messages
		propagate();
		
		// save messages outgoing from all clique nodes
		_defaultMessages = new HashMap<String,Map<AbstractNode,Function>>();
		_defaultAlphas = new HashMap<String, HashMap<AbstractNode, Double>>(); 
		_defaultLogAlphas = new HashMap<String, HashMap<AbstractNode, Double>>(); 
		_defaultfunctions = new HashMap<String,LinkedList<Function>>();
		_defualtQualifiedNeiMsgs = new HashMap<String,LinkedHashSet<CliqueNode>>();
		_defaultmsgsProd = new HashMap<String,Function>();
		
		

		
		// save messages for all clique nodes i.e. variableCliques and familyCliques
		for (AbstractNode node : get_cliqueTree().getNodes()) {
			
	
			String nodeName = node.getName();
			_defaultMessages.put(nodeName, ((CliqueNode)node).getMessages());
			_defaultAlphas.put(nodeName,((CliqueNode)node).getNormalization());
			_defaultLogAlphas.put(nodeName,((CliqueNode)node).getLogNormalization());
			
			_defaultfunctions.put(nodeName,((CliqueNode)node).getFunction());
			_defualtQualifiedNeiMsgs.put(nodeName,((CliqueNode)node).getQualifiedNeiMsgs());
			_defaultmsgsProd.put(nodeName,((CliqueNode)node).getmsgsProd());
	
		}
		
		return _defaultMessages;
		
	}
	
	
	/**
	 * 
	 * @param consumedItems : they are populated when the evidence is set
	 * @param topLevelLatentVariableNames
	 * 
	 * @return propagationRangeCliques : All cliques in the propagation range
	 * 
	 * @author fkhawar
	 */
	public Set<CliqueNode> findAndSetPropagationRange(HashSet<String> topLevelLatentVariableNames) {
		
		Set<Variable> consumedItems = _evidenceVariables;
		// if the latent variable to descendant map is not made then make it
		if(_mapTopLevelLatentVariableDescendantCliques == null || _mapLeafVariableToToplevelVariable == null) {
			
			 _mapLeafVariableToToplevelVariable = new HashMap<Variable,String>();
			_mapTopLevelLatentVariableDescendantCliques = new HashMap<String,Set<CliqueNode>>(); 
			
			// for each top level latent variable store its descendant variable and family cliques
			for (String topLatentVaraibleName : topLevelLatentVariableNames) {
				
				// stores all descendant clique tree nodes of this top level latent variable and put them in the map
				Set<CliqueNode> descendantCliques = new HashSet<CliqueNode>();
				
				/* get the descendant of toplevel latent variable from LTM and checking which are leaves. Take the name of the leaves and
				   get the corresponding clique tree familyClique nodes and if not leaves then take familyCliques and variable cliques.
				   Then add them to descendant cliques*/ 
				for (AbstractNode descendant : getDescend(((DirectedNode)((LTM)_bayesNet).getNode(topLatentVaraibleName)),topLevelLatentVariableNames)) {
					
					Variable descendantVariable = ((BeliefNode)descendant).getVariable();
					
					if(topLevelLatentVariableNames.contains(descendantVariable.getName()))
						continue;
					
					// if the descendant is a leaf then get its corresponding familyClique (leaves only have family clique)
					if(((DirectedNode)descendant).isLeaf() ) {
						
						//Variable leafVariable = ((BeliefNode)descendant).getVariable();
						
						// add the family clique of the leaf to the descendantCliques
						descendantCliques.add(get_cliqueTree().get_familyCliques().get(descendantVariable));
						
						_mapLeafVariableToToplevelVariable.put(descendantVariable, topLatentVaraibleName);
					}
					else {
						
						//Variable nonLeafVariable = ((BeliefNode)descendant).getVariable();
						
						// add the family clique of the nonLeafVariable to the descendantCliques
						descendantCliques.add(get_cliqueTree().get_familyCliques().get(descendantVariable));
						
						// add the variable clique of the nonLeafVariable to the descendantCliques
						descendantCliques.add(get_cliqueTree().get_variableCliques().get(descendantVariable));	
					}
					
				
					
				}
				
				// add the top level variable's cliques
				Variable topLatentVaraible = _bayesNet.getNodeByName(topLatentVaraibleName).getVariable();
				descendantCliques.add(get_cliqueTree().get_familyCliques().get(topLatentVaraible));
				descendantCliques.add(get_cliqueTree().get_variableCliques().get(topLatentVaraible));
				_mapTopLevelLatentVariableDescendantCliques.put(topLatentVaraibleName, descendantCliques);	
			}
			
			///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// for each pair of top level latent variable, get path from one top level variable to the other and then get all cliques in that path
			ArrayList<String> topLevelArray = new ArrayList<String>(topLevelLatentVariableNames); // ArrayList of toplevel latent variables
			
			topLevelArray.add(((LTM)_bayesNet).getRoot().getName()); // make sure root is always in teh restricted subtree regardless of the level of the latent variables being considered as the top level latetn variables
			
			
			_mapPath = new HashMap<String,HashMap<String,HashSet<CliqueNode>>>();
			
			for (int i = 0 ; i < topLevelArray.size() ; i++ ) {
				
				Variable sourceVariable = ((BeliefNode)_bayesNet.getNode(topLevelArray.get(i))).getVariable();
				
				HashMap<String,HashSet<CliqueNode>> mapDestinationToPathCliques = new HashMap<String,HashSet<CliqueNode>>(); // map from destination node name to cliques along the path
				
				for(int j = 0 ; j < topLevelArray.size() ; j++) {
					
					if ( i!=j) { // source and destination nodes should be different
						
						Variable destinationVariable = ((BeliefNode)_bayesNet.getNode(topLevelArray.get(j))).getVariable();
						
						ArrayList<BeliefNode> ltmPathNodes = ((LTM)_bayesNet).computePath(sourceVariable, destinationVariable); // path includes source and destination nodes
						
						HashSet<CliqueNode> cliqueNodes = new HashSet<CliqueNode>();
	
						
						// add the variableclique and family cliques of the path node
						for (BeliefNode pathNode : ltmPathNodes) {
							Variable nodeVariable = pathNode.getVariable();
							cliqueNodes.add(get_cliqueTree().get_familyCliques().get(nodeVariable));
							cliqueNodes.add(get_cliqueTree().get_variableCliques().get(nodeVariable));
							//cliqueNodes.addAll(_mapTopLevelLatentVariableDescendantCliques.get(nodeVariable.getName())); // add all descendants of intermediate latent variables
						}	
						
						mapDestinationToPathCliques.put(topLevelArray.get(j), cliqueNodes);
					}
					
	
				}
				_topVariablesNeighborCliques = new HashSet<AbstractNode>(get_cliqueTree().getVariableClique(sourceVariable).getNeighbors());
				_topVariablesNeighborCliques.addAll(get_cliqueTree().getFamilyClique(sourceVariable).getNeighbors());
				
				_mapPath.put(topLevelArray.get(i),mapDestinationToPathCliques);	
			}		
		}

		///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// For each evidence variable get all its propagation range	cliques
		Set<CliqueNode> propagationRangeCliques = new HashSet<CliqueNode>(); // All cliques belonging to the propagation range for all evidence variables
		
		if (consumedItems !=null) {
			HashSet<String> topLevelAncestorNames = new HashSet<String>(); // Stores the names of all top level latent variables for evidence variables
			
			// For each evidence variable get all cliques under its top level latent variable
			for (Variable evidence : consumedItems) {
				
				String topAncestorName = _mapLeafVariableToToplevelVariable.get(evidence);
				
				// Add all the cliques under this top level latent variable
				propagationRangeCliques.addAll(_mapTopLevelLatentVariableDescendantCliques.get(topAncestorName));
				
				topLevelAncestorNames.add(topAncestorName);
			}
			
			// Add all cliques from the path between each pair of top level ancestor variables of the evidence
			ArrayList<String> topLevelAncestorNamesArray = new ArrayList<String>(topLevelAncestorNames);
			
			for (int i = 0 ; i < topLevelAncestorNamesArray.size() ; i++ ) {
				
				for(int j = i+1 ; j < topLevelAncestorNamesArray.size() ; j++) {
				
					// Add all cliques from the path between each pair of top level ancestor variables of the evidence
					propagationRangeCliques.addAll(_mapPath.get(topLevelAncestorNamesArray.get(i)).get(topLevelAncestorNamesArray.get(j)));
					
				}
			}
			
			// to keep the root as the pivot , add path from one of the top level latent varibles to root and all cliques in between , if root is already not among the top level variables for an evidence
			if(!((LTM)_bayesNet).getNodeByName(topLevelAncestorNamesArray.get(0)).isRoot())
			propagationRangeCliques.addAll(_mapPath.get(topLevelAncestorNamesArray.get(0)).get(((LTM)_bayesNet).getRoot().getName()));
			

			///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			
			// Now set the _focusedSubtree of this clique tree. It automatically set a pivot from the focused subtree(This should be ok I guess)
			get_cliqueTree().setFocusedSubtree(propagationRangeCliques);
			
		}
		//System.out.println(propagationRangeCliques.size());
		return propagationRangeCliques;

	}
	
	/**
	 * Returns the set of descendants of this node.
	 * 
	 * @return the set of descendants of this node.
	 */
	public final Set<AbstractNode> getDescend(AbstractNode node,HashSet<String> topLevelLatentVariableNames) {
		// discovering and finishing time
		HashMap<AbstractNode, Integer> d = new HashMap<AbstractNode, Integer>();
		HashMap<AbstractNode, Integer> f = new HashMap<AbstractNode, Integer>();

		// starting with this node, DFS
		dfs(node, 0, d, f,topLevelLatentVariableNames);

		// except this node, all discovered nodes are descendants
		Set<AbstractNode> descendants = d.keySet();
		descendants.remove(node);

		return descendants;
	}
	
	/**
	 * Traverses this graph in a depth first manner. This implementation
	 * discovers the specified node and then recursively explores its unvisited
	 * children. does not explore a child if its a top level variable
	 * 
	 * @param node
	 *            node to start with.
	 * @param d
	 *            map from nodes to their discovering time.
	 * @param f
	 *            map from nodes to their finishing time.
	 * @return the elapsed time.
	 */
	public final int dfs(AbstractNode node, int time,
			Map<AbstractNode, Integer> d, Map<AbstractNode, Integer> f,HashSet<String> topLevelLatentVariableNames) {

		// discovers the argument node
		d.put(node, time++);

		// explores unvisited children
		for (DirectedNode child : ((DirectedNode) node).getChildren()) {
			if (!d.containsKey(child) && !topLevelLatentVariableNames.contains(child.getName())) {
				time = dfs(child, time, d, f,topLevelLatentVariableNames);
			}
		}

		// finishes the argument node
		f.put(node, time++);

		return time;
	}
	
	
	
	/**
	 * <p> 
	 * Resets all the messages of the clique nodes in the propagation range of the previous propagation. The messages
	 * are reset to the messages propaged in the case when evidence 0 was set to the leaf variabels in th LTM
	 * </p>
	 * 
	 * @param propagationRangeCliques
	 * 
	 * @author fkhawar
	 */
	public void resetMessages(Set<CliqueNode> propagationRangeCliques) {
		
		for (AbstractNode cliqueNode : propagationRangeCliques) {
		//Set<String> NodeswithMessagesSaved = new HashSet<String>(_defaultMessages.keySet());
		//for (String nodeName : NodeswithMessagesSaved) {	
			String nodeName = cliqueNode.getName();
			
			//AbstractNode cliqueNode = _cliqueTree.getNode(nodeName);
			//if(_topVariablesNeighborCliques.contains(cliqueNode)) {
			
			//HashMap<AbstractNode, Function> msgs = new HashMap<AbstractNode, Function>();
			//msgs.putAll(_defaultMessages.get(cliqueNode.getName()));
			
			((CliqueNode)cliqueNode).setMessage(_defaultMessages.get(nodeName));
			((CliqueNode)cliqueNode).setLogNormalization(_defaultLogAlphas.get(nodeName));
			((CliqueNode)cliqueNode).setNormalization(_defaultAlphas.get(nodeName));
			((CliqueNode)cliqueNode).setFunction(_defaultfunctions.get(nodeName));
			((CliqueNode)cliqueNode).setQualifiedNeiMsgs(_defualtQualifiedNeiMsgs.get(nodeName));
			((CliqueNode)cliqueNode).setMsgsProd(_defaultmsgsProd.get(nodeName));
			//((CliqueNode)cliqueNode).clearFunctions();
			//((CliqueNode)cliqueNode).clearQualifiedNeiMsgs();
			//((CliqueNode)cliqueNode).setMsgsProd(Function.createIdentityFunction());
			/*}
			else {
				_defaultMessages.remove(nodeName);
				_defaultLogAlphas.remove(nodeName);
				_defaultAlphas.remove(nodeName);
			}*/
			
			//NodeswithMessagesSaved = _defaultMessages.keySet();
		
			
		}

		
	}
	
	
	
	/**
	 * Constructs a CTP for the specified BN.
	 * 
	 * @param bayesNet
	 *            BN under query.
	 */
	public CliqueTreePropagation(BayesNet bayesNet) {
		_bayesNet = bayesNet;
		set_cliqueTree(new CliqueTree(_bayesNet));
		set_evidence(new HashMap<Variable, Integer>());
	}

	/**
	 * Clears the evidence entered into this inference engine.
	 */
	public void clearEvidence() {
		get_evidence().clear();
	}

	/**
	 * Creates and returns a deep copy of this CTP.
	 * 
	 * @return A deep copy of this CTP.
	 */
	public CliqueTreePropagation clone() {
		CliqueTreePropagation copy = new CliqueTreePropagation();
		copy._bayesNet = _bayesNet;
		copy.set_cliqueTree(get_cliqueTree().clone());
		// abandon eveidence
		return copy;
	}

	/**
	 * Prepares functions attached to cliques by <b>copying</b> CPTs and
	 * absorbing evidences.
	 * <p>
	 * Note: When an attached function is the same as one cpt rather than a
	 * funciont through projction of a cpt, we use the reference directly.
	 * Therefore be careful of updating the cpts of a BayesNet.
	 * </p>
	 */
	public void absorbEvidence() {

		Set<CliqueNode> focusedSubTree = _cliqueTree.get_focusedSubtree();
		
		Collection<Variable> focusedSubTreeTreeNodes = new LinkedHashSet<Variable>(); // the belief nodes corresponding to the cliques in focusedsubtree
		
		
		if( focusedSubTree == null) { // the normal absorption
			
			for (AbstractNode node : get_cliqueTree().getNodes()) { // go over only clique nodes , but that would make it specific for when focused subtree is set
				CliqueNode cNode = (CliqueNode) node;
				cNode.clearFunctions();
				cNode.clearQualifiedNeiMsgs();
				cNode.setMsgsProd(Function.createIdentityFunction());
			}
		}
		else { // if subtree is set, for the sake of efficiency just go over the cliques in subtree
			
			for (CliqueNode cNode : focusedSubTree) { // go over only clique nodes , but that would make it specific for when focused subtree is set
				focusedSubTreeTreeNodes.addAll(cNode.getVariables()); // all all the variables atached to this clique
				cNode.clearFunctions();
				cNode.clearQualifiedNeiMsgs();
				cNode.setMsgsProd(Function.createIdentityFunction());	
			}
		}

		
		LinkedHashMap<Variable, Function> functions =
				new LinkedHashMap<Variable, Function>();

		if( focusedSubTree == null) { // the normal absorption
			for (AbstractNode node : _bayesNet.getNodes()) { // go over only nodes with in focused tree, get those nodes while doing above
				BeliefNode bNode = (BeliefNode) node;
				Variable var = bNode.getVariable();

				CliqueNode familiyClique = get_cliqueTree().getFamilyClique(var);
				if (familiyClique != null && get_cliqueTree().inFocusedSubtree(familiyClique) )
					// initializes function as CPT
					functions.put(var, bNode.getCpt());
			}
		}
		else { // if focusedSubTree is set
			for (Variable var : focusedSubTreeTreeNodes) { // go over only nodes with in focused tree, get those nodes while doing above
				
				BeliefNode bNode = (BeliefNode) _bayesNet.getNode(var);
				
				CliqueNode familiyClique = get_cliqueTree().getFamilyClique(var);
				if (familiyClique != null && get_cliqueTree().inFocusedSubtree(familiyClique) )
					// initializes function as CPT
					functions.put(var, bNode.getCpt());
			}
		}
		

		Set<Variable> mutableVars = functions.keySet();

		for (Variable var : get_evidence().keySet()) { // do for only 1 evidence if focused subtree is set 
			int value = get_evidence().get(var);

			BeliefNode bNode = _bayesNet.getNodeByName(var.getName());

			if (mutableVars.contains(var)) {
				functions.put(var, functions.get(var).project(var, value));// project cpt of evidence variables

			}
			for (DirectedNode child : bNode.getChildren()) {
				BeliefNode bChild = (BeliefNode) child;
				Variable varChild = bChild.getVariable();
				if (mutableVars.contains(varChild))
					functions.put(varChild,
							functions.get(varChild).project(var, value));
			}
		}

		for (Variable var : mutableVars) {
			// attaches function to family covering clique
			get_cliqueTree().getFamilyClique(var).attachFunction(functions.get(var));
		}
	}


	/**
	 * @param source
	 * @param destination
	 * @param subtree
	 * @param standingNodes
	 * @return
	 */
	private Function collectMessage(CliqueNode source, CliqueNode destination,
			Set<CliqueNode> subtree, Collection<Variable> standingVars) {
		Function msg = Function.createIdentityFunction();

		// collects messages from neighbors of source except destination
		for (AbstractNode neighbor : source.getNeighbors()) {
			CliqueNode clique = (CliqueNode) neighbor;

			if (clique != destination) {
				if (subtree.contains(clique)) {
					msg =
							msg.times(collectMessage(clique, source, subtree,
									standingVars));
				} else {
					msg = msg.times(clique.getMessageTo(source));
				}
			}
		}

		// times up all functions in source
		for (Function func : source.getFunctions()) {
			msg = msg.times(func);
		}

		// sums out difference between source and destination but retain
		// standing nodes
		for (Variable var : source.getDifferenceTo(destination)) {
			if (!get_evidence().containsKey(var) && !standingVars.contains(var)) {
				msg = msg.sumOut(var);
			}
		}

		return msg;
	}

	/**
	 * Returns the posterior probability distribution of the specified variable
	 * 
	 * @param var
	 *            Variable under query.
	 * @return The posterior probability distribution of the specified variable.
	 */
	public Function computeBelief(Variable var) {
		// associated BN must contain var
		BeliefNode node = _bayesNet.getNode(var);
		assert node != null;

		Function belief = null;

		if (get_evidence().containsKey(var)) {
			// likelihood must be positive
			assert computeLikelihood() > 0.0;

			belief = Function.createIndicatorFunction(var, get_evidence().get(var));
		} else {
			// initialization
			belief = Function.createIdentityFunction();

			// computes potential at answer extraction clique
			CliqueNode answerClique = get_cliqueTree().getFamilyClique(var);

			// times up functions attached to answer extraction clique
			for (Function function : answerClique.getFunctions()) {
				belief = belief.times(function);
			}

			// times up messages to answer extraction clique
			for (AbstractNode neighbor : answerClique.getNeighbors()) {
				belief =
						belief.times(((CliqueNode) neighbor).getMessageTo(answerClique));
			}

			// marginalizes potential
			belief = belief.marginalize(var);

			// normalizes potential
			belief.normalize();
		}

		return belief;
	}

	/**
	 * Returns the posterior probability distribution of the specified
	 * collection of variable. The difference bwteen this method and
	 * <code> computeBelief(Collection<Variable> var)</code> is that here the
	 * Clique Subtree used for computing suffucient statistics is specified.
	 * 
	 * @param vars
	 *            Collection of variables under query.
	 * @param subtree
	 *            The clique subTree used for inference.
	 * @return The posterior probability distribution of the specified
	 *         collection of variables.
	 */
	public Function computeBelief(Collection<Variable> vars,
			Set<CliqueNode> subtree) {
		assert !vars.isEmpty();
		assert _bayesNet.containsVars(vars);

		// collects hidden and observed variables in query nodes
		LinkedList<Variable> hdnVars = new LinkedList<Variable>();
		ArrayList<Variable> obsVars = new ArrayList<Variable>();
		ArrayList<Integer> obsVals = new ArrayList<Integer>();

		for (Variable var : vars) {
			if (get_evidence().containsKey(var)) {
				obsVars.add(var);
				obsVals.add(get_evidence().get(var));
			} else {
				hdnVars.add(var);
			}
		}

		// belief over observed variables
		Function obsBel = Function.createIndicatorFunction(obsVars, obsVals);

		if (hdnVars.isEmpty()) {
			return obsBel;
		}

		// belief over hidden variables
		Function hdnBel = Function.createIdentityFunction();

		// constructs the minimal subtree that covers all query nodes
		// Set<CliqueNode> subtree = _cliqueTree.computeMinimalSubtree(nodes);
		// Set<CliqueNode> subtree =
		// _cliqueTree.computeMinimalSubtree(hdnNodes);

		// uses first clique in the subtree as the pivot
		CliqueNode pivot = subtree.iterator().next();

		// computes the local potential at the pivot clique
		for (Function func : pivot.getFunctions())
			hdnBel = hdnBel.times(func);

		// collects messages from all neighbors of pivot and times them up
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			CliqueNode clique = (CliqueNode) neighbor;

			// message from neighbor
			if (subtree.contains(clique)) {
				// recollects message
				hdnBel =
						hdnBel.times(collectMessage(clique, pivot, subtree,
								vars));
			} else {
				// reuses original message
				hdnBel = hdnBel.times(clique.getMessageTo(pivot));
			}
		}

		if (!(hdnVars.size() == hdnBel.getDimension())) {
			// marginalizes potential
			hdnBel = hdnBel.marginalize(hdnVars);
		}

		// normalizes potential
		hdnBel.normalize();

		return hdnBel.times(obsBel);
	}

	/**
	 * Returns the posterior probability distribution of the specified
	 * collection of variables.
	 * 
	 * @param vars
	 *            Collection of variables under query.
	 * @return The posterior probability distribution of the specified
	 *         collection of variables.
	 */
	public Function computeBelief(Collection<Variable> vars) {
		assert !vars.isEmpty();
		assert _bayesNet.containsVars(vars);

		// collects hidden and observed variables in query nodes
		LinkedList<Variable> hdnVars = new LinkedList<Variable>();
		ArrayList<Variable> obsVars = new ArrayList<Variable>();
		ArrayList<Integer> obsVals = new ArrayList<Integer>();

		for (Variable var : vars) {
			if (get_evidence().containsKey(var)) {
				obsVars.add(var);
				obsVals.add(get_evidence().get(var));
			} else {
				hdnVars.add(var);
			}
		}

		// belief over observed variables
		Function obsBel = Function.createIndicatorFunction(obsVars, obsVals);

		if (hdnVars.isEmpty()) {
			return obsBel;
		}

		// belief over hidden variables
		Function hdnBel = Function.createIdentityFunction();

		// constructs the minimal subtree that covers all query nodes
		// Set<CliqueNode> subtree = _cliqueTree.computeMinimalSubtree(nodes);
		Set<CliqueNode> subtree = get_cliqueTree().computeMinimalSubtree(hdnVars);

		// uses first clique in the subtree as the pivot
		CliqueNode pivot = subtree.iterator().next();

		// computes the local potential at the pivot clique
		for (Function func : pivot.getFunctions()) {
			hdnBel = hdnBel.times(func);
		}

		// collects messages from all neighbors of pivot and times them up
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			CliqueNode clique = (CliqueNode) neighbor;

			// message from neighbor
			if (subtree.contains(clique)) {
				// recollects message
				hdnBel =
						hdnBel.times(collectMessage(clique, pivot, subtree,
								vars));
			} else {
				// reuses original message
				hdnBel = hdnBel.times(clique.getMessageTo(pivot));
			}
		}

		if (!(hdnVars.size() == hdnBel.getDimension())) {
			// marginalizes potential
			hdnBel = hdnBel.marginalize(hdnVars);
		}

		// normalizes potential
		hdnBel.normalize();

		return hdnBel.times(obsBel);
	}

	/**
	 * Returns the posterior probability distribution of the family of the
	 * specified variable. It is a function of all Variables in the family no
	 * matter it is observed or hidden.
	 * 
	 * @param var
	 *            variable under query.
	 * @return the posterior probability distribution of the family of the
	 *         specified variable.
	 */
	public Function computeFamilyBelief(Variable var) {
		// associated BN must contain var
		assert _bayesNet.containsVar(var);

		// collects hidden and observed variables in family
		LinkedList<Variable> hdnVars = new LinkedList<Variable>();
		ArrayList<Variable> obsVars = new ArrayList<Variable>();
		ArrayList<Integer> obsVals = new ArrayList<Integer>();

		if (get_evidence().containsKey(var)) {
			obsVars.add(var);
			obsVals.add(get_evidence().get(var));
		} else {
			hdnVars.add(var);
		}

		BeliefNode node = _bayesNet.getNode(var);
		for (AbstractNode parent : node.getParents()) {
			BeliefNode bParent = (BeliefNode) parent;
			Variable vParent = bParent.getVariable();

			if (get_evidence().containsKey(vParent)) {
				obsVars.add(vParent);
				obsVals.add(get_evidence().get(vParent));
			} else {
				hdnVars.add(vParent);
			}
		}

		// belief over observed variables
		Function obsBel = Function.createIndicatorFunction(obsVars, obsVals);

		if (hdnVars.isEmpty()) {
			return obsBel;
		}

		// belief over hidden variables
		Function hdnBel = Function.createIdentityFunction();

		// computes potential at family covering clique
		CliqueNode familyClique = get_cliqueTree().getFamilyClique(var);

		// times up functions attached to family covering clique
		for (Function function : familyClique.getFunctions()) {
			hdnBel = hdnBel.times(function);
		}
		// (In the HLCM propogation case)After this, the hdnBel is superior to
		// any funtion multiplied.

		if (_bayesNet instanceof LTM) {
			// times up messages to family covering clique
			hdnBel.multiply(familyClique._msgsProd);
			Set<CliqueNode> already = familyClique._qualifiedNeiMsgs;
			for (AbstractNode neighbor : familyClique.getNeighbors()) {
				if (!already.contains(neighbor))
					hdnBel.multiply(((CliqueNode) neighbor).getMessageTo(familyClique));
			}
		} else {
			for (AbstractNode neighbor : familyClique.getNeighbors()) {
				hdnBel =
						hdnBel.times(((CliqueNode) neighbor).getMessageTo(familyClique));
			}
		}

		if (!(hdnVars.size() == hdnBel.getDimension())) {
			// marginalizes potential
			hdnBel = hdnBel.marginalize(hdnVars);
		}

		// normalizes potential
		hdnBel.normalize();

		return hdnBel.times(obsBel);
	}

	/**
	 * Returns the likelihood of the evidences on the associated BN. Make sure
	 * that propogation has been conducted when calling this method.
	 */
	public double computeLikelihood() {
		CliqueNode pivot = get_cliqueTree().getPivot();

		// times up functions attached to pivot
		Function potential = Function.createIdentityFunction();
		for (Function function : pivot.getFunctions()) {
			potential = potential.times(function);
		}

		// times up messages to pivot
		double normalization = 1.0;
		double logNormalization = 0;
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			CliqueNode clique = (CliqueNode) neighbor;
			//if( _cliqueTree._focusedSubtree == null || _cliqueTree.inFocusedSubtree(clique)) {
			potential = potential.times(clique.getMessageTo(pivot));
			normalization *= clique.getNormalizationTo(pivot);
			logNormalization += clique.getLogNormalizationTo(pivot);
			//}
		}

		double n = potential.sumUp();
		lastLogLikelihood = logNormalization + Math.log(n);
		return n * normalization;
	}

	/**
	 * Returns the last log-likelihood computed. It is updated after each call
	 * of {@link computeLikelihood}.
	 * 
	 * @return last log-likelihood computed
	 */
	public double getLastLogLikelihood() {
		return lastLogLikelihood;
	}

	/**
	 * Collects messages around the source and sends an aggregated message to
	 * the destination.
	 * 
	 * @param source
	 *            source around which messages are to be collected.
	 * @param destination
	 *            destination to which an aggregated message is to be sent.
	 */
	public void collectMessage(CliqueNode source, CliqueNode destination) {
		if (source.getMessageTo(destination) == null
				|| get_cliqueTree().inFocusedSubtree(source)) {
			// collects messages from neighbors of source except destination
			for (AbstractNode neighbor : source.getNeighbors()) {
				if (neighbor != destination ) {
					collectMessage((CliqueNode) neighbor, source);
				}
			}

			// When I try to merge the code of different versions, I find that
			// some version only deals with LTM and the other deal with general
			// bayesNet.
			// So I merge them here. liutf
			if (_bayesNet instanceof LTM) {
				sendMessage4HLCM(source, destination);
				Function func = source.getMessageTo(destination);

				if (!func.hasZeroCell()) {
					destination.modifyMsgProd(func); // multiply the message sent by source to the destination function
					destination._qualifiedNeiMsgs.add(source); // know that message has been sent to this destination
				}
			} else {
				sendMessage(source, destination);
			}
		}
	}

	/**
	 * Sends an aggregated message from the source to the destination and
	 * distributes the message around the destination.
	 * 
	 * @param source
	 * @param destination
	 */
	public void distributeMessage(CliqueNode source, CliqueNode destination) {
		if (get_cliqueTree().inFocusedSubtree(destination)) {

			// same reason as in function "collectMessage"
			if (_bayesNet instanceof LTM) {
				sendMessage4HLCM(source, destination);
				Function func = source.getMessageTo(destination);
				if (!func.hasZeroCell()) {
					destination.modifyMsgProd(func);
					destination._qualifiedNeiMsgs.add(source);
				}
			} else {
				sendMessage(source, destination);
			}

			// distributes messages to neighbors of destination except source
			for (AbstractNode neighbor : destination.getNeighbors()) {
				if (neighbor != source ) {
					distributeMessage(destination, (CliqueNode) neighbor);
				}
			}
		}
	}

	/**
	 * Returns the BN that is associated with this CTP.
	 * 
	 * @return the BN that is associated with this CTP.
	 */
	public BayesNet getBayesNet() {
		return _bayesNet;
	}

	/**
	 * Get _cliqueTree
	 * 
	 * @author csct
	 * @return _cliqueTree
	 */
	public CliqueTree getCliqueTree() {
		return get_cliqueTree();
	}

	/**
	 * Propagates messages on the focused CT defined by the propagation range of the evidence variables.
	 * 
	 * <p> Note that this methods <b> does not </b> set the evidence, just propagates it.
	 * @param consumedItems : set of evidence variable of this datacase
	 * @param topLevelLatentVariableNames : the names of the top level latent varible so of the LTM
	 * 
	 * 
	 * 
	 * @return LL .
	 * @author fkhawar
	 */
	public double propagate(Set<CliqueNode> lastPropagationRangeCliques) {
		
		/*// reset default messages of propagationRangeCliques
		if (lastPropagationRangeCliques != null)
			resetMessages(lastPropagationRangeCliques);*/
		
		
		/*// set the _focusedSubtree in the clique tree
		findAndSetPropagationRange(topLevelLatentVariableNames);*/
		
		double likelihood = propagate();
	
		return likelihood;
	}
	
	/**
	 *Propagates messages on the CT.
	 * 
	 * @return LL.
	 */
	public double propagate() {
		if (Thread.interrupted()) {
			throw new RuntimeException("Thread interrupted");
		}

		// absorbs evidences
		absorbEvidence();

		CliqueNode pivot = get_cliqueTree().getPivot();

		// collects messages from neighbors of pivot
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			collectMessage((CliqueNode) neighbor, pivot);
		}

		// distributes messages to neighbors of pivot
		for (AbstractNode neighbor : pivot.getNeighbors()) {
			distributeMessage(pivot, (CliqueNode) neighbor);
		}

		return computeLikelihood();
	}

	/**
	 * Sends a message from the source to the destination.
	 * 
	 * @param source
	 *            source of the message.
	 * @param destination
	 *            destination of the message.
	 */
	public void sendMessage4HLCM(CliqueNode source, CliqueNode destination) {

		Collection<CliqueNode> variableCliques =
				get_cliqueTree().get_variableCliques().values();

		Function message = null;
		double normalization = 1.0;
		double logNormalization = 0;

		if (variableCliques.contains(source)) { // if latent variable
			Set<CliqueNode> already = source._qualifiedNeiMsgs; // the neighbors to which message have already been sent

			Variable var = source.getVariables().iterator().next(); // the the only variable of this clique

			if (get_evidence().containsKey(var)) {
				// otherwise, when latent variable is listed in evidence
				// variables, there will be a bug.
				message = Function.createIdentityFunction();
			} else {
				message = Function.createIdentityFunction(var);
			}

			message.multiply(source._msgsProd); // initial msgsProd is identity when clique is created

			for (AbstractNode neighbor : source.getNeighbors()) {
				CliqueNode cNeighbor = (CliqueNode) neighbor;
				
				//if(get_cliqueTree().inFocusedSubtree(cNeighbor)) { ////////////// Added by farhan
					
					if (cNeighbor != destination) { 
						normalization *= cNeighbor.getNormalizationTo(source);
						logNormalization += cNeighbor.getLogNormalizationTo(source);

						if (!already.contains(cNeighbor)) {
							message.multiply(cNeighbor.getMessageTo(source));
						}
					} else {
						if (already.contains(cNeighbor))
							message.divide(cNeighbor.getMessageTo(source));
					}

				//}
			}

			for (Function function : source.getFunctions()) {
				message.multiply(function);
			}
		} else {// family cliques , both latent and onserved
			message = Function.createIdentityFunction();
			for (Function function : source.getFunctions()) {
				message = message.times(function);
			}
			// After this message is superior to...

			for (AbstractNode neighbor : source.getNeighbors()) {
				if (neighbor != destination ) {////////////Second condition added by farhasn
					CliqueNode clique = (CliqueNode) neighbor;
					message.multiply(clique.getMessageTo(source));
					normalization *= clique.getNormalizationTo(source);
					logNormalization += clique.getLogNormalizationTo(source);
				}
			}

		}

		// sums out difference between source and destination
		for (Variable var : source.getDifferenceTo(destination)) {
			if (!get_evidence().containsKey(var)) {
				message = message.sumOut(var);
			}
		}

		// normalizes to alleviate round off error
		double n = message.normalize();
		normalization *= n;
		logNormalization += Math.log(n);

		assert normalization >= Double.MIN_NORMAL;

		// saves message and normalization
		source.setMessageTo(destination, message);
		source.setNormalizationTo(destination, normalization);
		source.setLogNormalizationTo(destination, logNormalization);
	}

	/**
	 * Sends a message from the source to the destination.
	 * 
	 * @param source
	 *            source of the message.
	 * @param destination
	 *            destination of the message.
	 */
	public void sendMessage4HLCM_OLDVERSIONwithoutDivision(CliqueNode source,
			CliqueNode destination) {

		Collection<CliqueNode> variableCliques =
				get_cliqueTree().get_variableCliques().values();

		Function message = null;
		double normalization = 1.0;

		if (variableCliques.contains(source)) {
			Variable var = source.getVariables().iterator().next();
			message = Function.createIdentityFunction(var);

			// multiply message with neighbors message
			for (AbstractNode neighbor : source.getNeighbors()) {
				if (neighbor != destination) {
					CliqueNode clique = (CliqueNode) neighbor;
					// NOte
					message.multiply(clique.getMessageTo(source));
					normalization *= clique.getNormalizationTo(source);
				}
			}

			// multiply with own functions attached
			for (Function function : source.getFunctions()) {
				// Note
				message.multiply(function);
			}

		} else {
			message = Function.createIdentityFunction();

			for (AbstractNode neighbor : source.getNeighbors()) {
				if (neighbor != destination) {
					CliqueNode clique = (CliqueNode) neighbor;
					message = message.times(clique.getMessageTo(source));
					normalization *= clique.getNormalizationTo(source);
				}
			}

			for (Function function : source.getFunctions()) {
				message = message.times(function);
			}
		}

		// sums out difference between source and destination
		for (Variable var : source.getDifferenceTo(destination)) {
			if (!get_evidence().containsKey(var)) {
				message = message.sumOut(var);
			}
		}

		// normalizes to alleviate round off error
		normalization *= message.normalize();

		// saves message and normalization
		source.setMessageTo(destination, message);
		source.setNormalizationTo(destination, normalization);

	}

	/**
	 * Sends a message from the source to the destiation.
	 * 
	 * @param source
	 *            source of the message.
	 * @param destination
	 *            destination of the message.
	 */
	public void sendMessage(CliqueNode source, CliqueNode destination) {
		Function message = Function.createIdentityFunction();
		double normalization = 1.0;
		double logNormalization = 0;

		for (AbstractNode neighbor : source.getNeighbors()) {
			if (neighbor != destination) {
				CliqueNode clique = (CliqueNode) neighbor;
				message = message.times(clique.getMessageTo(source));
				normalization *= clique.getNormalizationTo(source);
				logNormalization += clique.getLogNormalizationTo(source);
			}
		}

		for (Function function : source.getFunctions()) {
			message = message.times(function);
		}

		// sums out difference between source and destination
		for (Variable var : source.getDifferenceTo(destination)) {
			if (!get_evidence().containsKey(var)) {
				message = message.sumOut(var);
			}
		}

		// normalizes to alleviate round off error
		double n = message.normalize();
		normalization *= n;
		logNormalization += Math.log(n);

		assert normalization >= Double.MIN_NORMAL;

		// saves message and normalization
		source.setMessageTo(destination, message);
		source.setNormalizationTo(destination, normalization);
		source.setLogNormalizationTo(destination, logNormalization);
	}

	public void setEvidence(Variable[] variables, int[] states) {
		assert variables.length == states.length;
		
		if(_evidenceVariables == null)
			_evidenceVariables =new HashSet<Variable>();
		
		/*if(_defaultMessages == null) {
			makeDefaultMessageMap();
		}*/
		
		_evidenceVariables.clear();
		
		get_evidence().clear();

		for (int i = 0; i < variables.length; i++) {
			// ignore this variable if its value is missing
			if (states[i] == DataSet.MISSING_VALUE)
				continue;

			if(states[i]==1)
				_evidenceVariables.add(variables[i]);
			
			Variable var = variables[i];

			assert _bayesNet.containsVar(var);
			assert variables[i].isValid(states[i]);

			get_evidence().put(var, states[i]);
		}
	}
	
	// supposed to be called by ParallelStepwiseEmLearner only
	// add evidence for only the manifest variables of the LTM
	// return the number of evidence variables set
	public int setEvidence(Variable[] variables, int[] states , Set<Variable> manifestVariable) {
		assert variables.length == states.length;
		
		int countEvidenceSet = 0;
		
		if(_evidenceVariables == null)
			_evidenceVariables =new HashSet<Variable>();
		
		/*if(_defaultMessages == null) {
			makeDefaultMessageMap();
		}*/
		
		_evidenceVariables.clear();
		
		get_evidence().clear();

		for (int i = 0; i < variables.length; i++) {
			// ignore this variable if its value is missing, or if this variable is not a leaf of the LTM
			if (states[i] == DataSet.MISSING_VALUE || !manifestVariable.contains(variables[i]) )
				continue;

			if(states[i]==1) { 
				_evidenceVariables.add(variables[i]);
				countEvidenceSet++;
			}
			
			Variable var = variables[i];

			assert _bayesNet.containsVar(var);
			assert variables[i].isValid(states[i]);

			get_evidence().put(var, states[i]);
		}
		
		 return countEvidenceSet;
	}
	

	/**
	 * Sets the positive only evidence of a group of  variables and 0 for all other  variable
	 * @param varName the names of the variables whose states will be set to 1
	 * NOTE: all other variables will be set to 0
	 * 
	 * @author fkhawar
	 */
	public void setPositiveOnlyEvidence( List<Variable> varName, Variable[] allVars ) {
		if(varName!= null && varName.size() >  allVars.length)
			System.out.println("The positive evidence variables are more than the total variables");
		
		get_evidence().clear();
		
		for (Variable var : allVars){
			if(varName!= null&&varName.contains(var))
				get_evidence().put(var, 1);
			else
				get_evidence().put(var, 0);
		}
		
		/*if(varName!= null){
			for(Variable v : varName){
				_evidence.put(v, 1);
			}
		}*/
		
		
	}
	
	/**
	 * 
	 * @param varName
	 * @param allVars
	 * @author fkhawar
	 */
	public void setPositiveOnlyEvidence( Set<Variable> varName, Set<Variable> allVars ) {
		if(varName!= null && varName.size() >  allVars.size())
			System.out.println("The positive evidence variables are more than the total variables");
		
		get_evidence().clear();
		
		if(_evidenceVariables == null)
			_evidenceVariables =new HashSet<Variable>();
		
		_evidenceVariables.clear();
		
		for (Variable var : allVars){
			if(varName!= null&&varName.contains(var)) {
				get_evidence().put(var, 1);
				_evidenceVariables.add(var);
			}
			else
				get_evidence().put(var, 0);
		}
		
		/*if(varName!= null){
			for(Variable v : varName){
				_evidence.put(v, 1);
			}
		}*/
		
		
	}
	
	/**
	 * 
	 * @param varName
	 * @param allVars
	 * @author fkhawar
	 */
	public void setPurePositiveOnlyEvidence( Set<Variable> varName, Set<Variable> allVars ) {
		if(varName!= null && varName.size() >  allVars.size())
			System.out.println("The positive evidence variables are more than the total variables");
		
		get_evidence().clear();
		
		for (Variable var : allVars){
			if(varName!= null&&varName.contains(var))
				get_evidence().put(var, 1);
			
		}
		
		/*if(varName!= null){
			for(Variable v : varName){
				_evidence.put(v, 1);
			}
		}*/
		
		
	}
	
	/**
	 * 
	 * @param varName
	 * @param allVars
	 * @author fkhawar
	 */
	public void setPureNegativeOnlyEvidence( Set<Variable> varName, Set<Variable> allVars ) {
		if(varName!= null && varName.size() >  allVars.size())
			System.out.println("The positive evidence variables are more than the total variables");
		
		get_evidence().clear();
		
		for (Variable var : allVars){
			if(varName!= null&&varName.contains(var))
				get_evidence().put(var, 0);
			
		}
		
		/*if(varName!= null){
			for(Variable v : varName){
				_evidence.put(v, 1);
			}
		}*/
		
		
	}

	public void setBayesNet(BayesNet bayesNet) {
		_bayesNet = bayesNet;
	}

	public void addEvidence(Variable variable, int state) {
		BeliefNode node = _bayesNet.getNode(variable);

		assert node != null;

		assert variable.isValid(state);

		if (state == DataSet.MISSING_VALUE) {
			get_evidence().remove(node);
		} else {
			get_evidence().put(node.getVariable(), state);
		}
	}

	public int getEvidence(Variable variable) {
		BeliefNode node = _bayesNet.getNode(variable);

		assert node != null;

		if (get_evidence().containsKey(node)) {
			return get_evidence().get(node);
		} else {
			return DataSet.MISSING_VALUE;
		}
	}

	public CliqueTree get_cliqueTree() {
		return _cliqueTree;
	}

	public void set_cliqueTree(CliqueTree _cliqueTree) {
		this._cliqueTree = _cliqueTree;
	}

	public Map<Variable, Integer> get_evidence() {
		return _evidence;
	}

	public void set_evidence(Map<Variable, Integer> _evidence) {
		this._evidence = _evidence;
	}
}