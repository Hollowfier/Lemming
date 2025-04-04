package org.aksw.simba.lemming.mimicgraph.generator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.aksw.simba.lemming.ColouredGraph;
import org.aksw.simba.lemming.algo.expression.Expression;
import org.aksw.simba.lemming.metrics.single.SingleValueMetric;
import org.aksw.simba.lemming.metrics.single.edgemanipulation.EdgeModifier;
import org.aksw.simba.lemming.mimicgraph.colourmetrics.utils.ErrorScoreCalculator;
import org.aksw.simba.lemming.mimicgraph.constraints.TripleBaseSingleID;
import org.aksw.simba.lemming.mimicgraph.metricstorage.ConstantValueStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import toools.set.IntSet;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.ObjectDoubleOpenHashMap;

public class GraphOptimization {
	private static final Logger LOGGER = LoggerFactory.getLogger(GraphOptimization.class);
	
	private int mMaxIteration = 50000 ;
	private int mTrueNoOfIteration = 0;
	private int mMaxRepeatedSelection = 5000;
	private boolean mProcessRandomly = false;
	
	private IGraphGeneration mGraphGenerator;
	private EdgeModifier mEdgeModifier;
	private ErrorScoreCalculator mErrScoreCalculator;
	private List<Double> mLstErrorScore; 
	private double mOptimizedTime =0;
	
	private long seed;
	
	
	/*-----------------------------------------------
	 * Variable for storing calculation information *
	 -----------------------------------------------*/
	
	
	public GraphOptimization(ColouredGraph[] origGrphs,
			IGraphGeneration graphGenerator, List<SingleValueMetric> metrics,  ConstantValueStorage valueCarriers, long seed) {
		this.seed = seed;
		mLstErrorScore = new ArrayList<Double>();
		/*
		 *  mErrScoreCalculator is used to compute the error score compared to original
		 *  constant values of the original graphs
		 */
		mErrScoreCalculator = new ErrorScoreCalculator(origGrphs, valueCarriers);
		
		// the graph generator
		mGraphGenerator = graphGenerator;
		
		ColouredGraph clonedGrph = mGraphGenerator.getMimicGraph().clone();
		mEdgeModifier = new EdgeModifier(clonedGrph, metrics);
	}
	
	
	public void setRefineGraphRandomly(boolean isRandom){
		mProcessRandomly = isRandom;
	}
	
	public void refineGraph(){
		
		LOGGER.info("Start optimize the mimic graph!");
		
		int noOfRepeatedParent = 0;

		double lErrScore = Double.NaN; 
		double rErrScore = Double.NaN;
		
		ObjectDoubleOpenHashMap<String> baseMetricValues = mEdgeModifier.getOriginalMetricValues();
		
		double pErrScore = mErrScoreCalculator.computeErrorScore(baseMetricValues); 
		for(int i = 0 ; i < mMaxIteration ; ++i){
			
			// add errorScore to tracking list result
			mLstErrorScore.add(pErrScore);
			
			// go left by removing an edge
			TripleBaseSingleID lTriple = getOfferedEdgeforRemoving(mEdgeModifier.getGraph());
			ObjectDoubleOpenHashMap<String> metricValuesOfLeft = mEdgeModifier.tryToRemoveAnEdge(lTriple);
			//System.out.println("[L]Aft -Number of edges: "+ mEdgeModifier.getGraph().getEdges().size());
			
			//if the removal cannot happen, the error is set to max as not to be chosen
			if(metricValuesOfLeft == null) {
				lErrScore = Double.MAX_VALUE;
				LOGGER.warn("Edge Removal Prevented. Setting lErrScore: "+lErrScore);
			} else {
				lErrScore = mErrScoreCalculator.computeErrorScore(metricValuesOfLeft);
			}

			 // go right by adding a new edge
			TripleBaseSingleID rTriple = getOfferedEdgeForAdding(mEdgeModifier.getGraph());
			ObjectDoubleOpenHashMap<String> metricValuesOfRight =  mEdgeModifier.tryToAddAnEdge(rTriple);
			//System.out.println("[R]Aft -Number of edges: "+ mEdgeModifier.getGraph().getEdges().size());
			if (metricValuesOfRight == null) {
				rErrScore = Double.MAX_VALUE;
			} else {
				rErrScore = mErrScoreCalculator.computeErrorScore(metricValuesOfRight);
			}
			
			// find min error score
			double minErrScore = minValues(pErrScore, lErrScore, rErrScore);
			
			System.out.println("("+i+"/ "+mMaxIteration+") Mid: "+ pErrScore 
					+ " - Left: "+ lErrScore +" - Right: " + rErrScore);
			
			if(minErrScore == lErrScore){
				
				pErrScore = lErrScore;
				
				noOfRepeatedParent = 0;
				//mEdgeModifier.updateMapMetricValues(metricValuesOfLeft);
				mEdgeModifier.executeRemovingAnEdge(metricValuesOfLeft);
				continue;
			}
			if(minErrScore == rErrScore){
				
				pErrScore = rErrScore;
				
				noOfRepeatedParent = 0;
				//mEdgeModifier.updateMapMetricValues(metricValuesOfRight);
				mEdgeModifier.executeAddingAnEdge(metricValuesOfRight);
				continue;
			}
			
			noOfRepeatedParent ++;
			
			if(noOfRepeatedParent == mMaxRepeatedSelection){
				mTrueNoOfIteration = i+1;
				LOGGER.info("Cannot find better refined graph! Break the loop!");
				break;
			}
		}
		
		if(mTrueNoOfIteration == 0 ){
			mTrueNoOfIteration = mMaxIteration;
		}
		
		mGraphGenerator.setMimicGraph(mEdgeModifier.getGraph());
		mOptimizedTime =  System.currentTimeMillis();
	}
	
	/**
	 * Compute the smallest error score among the three inputs
	 * 
	 * @param pErrScore the error score at the parent node
	 * @param lErrScore the error score if go left
	 * @param rErrScore the error score if go right
	 * @return the smalles error score among them
	 */
	private double minValues(double pErrScore, double lErrScore, double rErrScore){
		double minErrScore = Double.MAX_VALUE;
		if(pErrScore != Double.NaN && pErrScore < minErrScore){
			minErrScore = pErrScore;
		}
		if(lErrScore!= Double.NaN && lErrScore < minErrScore){
			minErrScore = lErrScore;
		}
		if(rErrScore != Double.NaN && rErrScore < minErrScore){
			minErrScore = rErrScore;
		}
		return minErrScore;
	}
	
	
	/**
	 * remove an edge 
	 * @param clonedGrph the target graph
	 * @a
	 */
	private TripleBaseSingleID getOfferedEdgeforRemoving(ColouredGraph clonedGrph){
		int edgeId = -1;
		BitSet edgeColour = null;
		Random rand = new Random(seed);
		seed++;
		while(true){
			//LOGGER.info("Try to get an edge for removing!");
			IntSet setOfEdges =	clonedGrph.getEdges();
			int[] arrEdges = setOfEdges.toIntArray();
			// randomly choose edge id to remove
			edgeId = arrEdges[rand.nextInt(arrEdges.length)];
			edgeColour = clonedGrph.getEdgeColour(edgeId);
			if(!edgeColour.equals(clonedGrph.getRDFTypePropertyColour())){
				break;
			}
		}
		
		//track the head and tail of the removed edge
		TripleBaseSingleID triple = new TripleBaseSingleID();
		triple.tailId = clonedGrph.getTailOfTheEdge(edgeId);
		triple.headId = clonedGrph.getHeadOfTheEdge(edgeId);
		triple.edgeId = edgeId;
		triple.edgeColour = edgeColour;
		
		//LOGGER.info("Proposed removed triple: ("+triple.tailId +","+triple.headId +","+triple.edgeId+")");
		return triple;
	}
	
    
	/**
	 * add an edge
	 * @param mimicGrph the target graph
	 */
	private TripleBaseSingleID getOfferedEdgeForAdding(ColouredGraph mimicGrph){
		return mGraphGenerator.getProposedTriple(mProcessRandomly);
	}

	
	/**
	 * output results to file LemmingEx.result 
	 * @param args all input configurations
	 * @param startingTime the starting time of generation process
	 * @param savedFile the saved file's name of the mimic dataset
	 */
	public void printResult(Map<String, String> args, double startingTime, String savedFile, long seed){
		BufferedWriter fWriter ;
		try{
			LOGGER.warn("Output results to file!");
			
			fWriter = new BufferedWriter( new FileWriter("LemmingEx.result", true));
			
			BufferedWriter fErrorScoreWriter ;
			String errorScoreFile = new String(savedFile);
			errorScoreFile = "results/"+ errorScoreFile.replace(".rdf", ".scores");
			
			
			fErrorScoreWriter = new BufferedWriter( new FileWriter(errorScoreFile, true));
			
			// number of input graphs
			fWriter.write("#----------------------------------------------------------------------#\n");
			fWriter.write("# Graph Generation: " + LocalDateTime.now().toString() +".\n");
			fWriter.write("# Total number of input graphs: " + mErrScoreCalculator.getNumberOfGraphs() +".\n");
			fWriter.write("# Generate a mimic graph of "+ mEdgeModifier.getGraph().getVertices().size()+" vertices and "+ mEdgeModifier.getGraph().getEdges().size()+" edges.\n");
			fWriter.write("# Saved file: "+ savedFile +".\n");
			fWriter.write("# Saved error score file: "+ errorScoreFile +".\n");
			fWriter.write("# Duration: "+ ((int)(mOptimizedTime - startingTime)/1000)+" (s).\n");
			fWriter.write("# Optimization: "+ mTrueNoOfIteration + "/" + mMaxIteration + " iterations\n");
			fWriter.write("# Seed: "+ seed +"\n");
			if(args!=null && args.size()>0){
				//dataset 
				if(args.containsKey("-ds")){
					fWriter.write("# Input dataset: " + (args.get("-ds").equals("pg")? "PersonGraph" : "Sematic Web Dog Food")+ ".\n");
				}else{
					fWriter.write("# Default input dataset: Sematic Web Dog Food.\n");
				}
				//generation approach
				if(args.containsKey("-t")){
					fWriter.write("# Generation approach: " + args.get("-t") +".\n");	
				}else{
					fWriter.write("# Defaule generation approach: random generation.\n");	
				}
			}
			
			fWriter.write("#----------------------------------------------------------------------#\n");
			
			Map<String, String> mapGraphName = new HashMap<String, String>();
			
			// metric values of all graphs
			fWriter.write("\n");
			fWriter.write("- Metric Values\n");
			Map<String, Map<String, Double>> mapInputGraphMetricValues = mErrScoreCalculator.getMapMetricValuesOfInputGraphs();
			ObjectDoubleOpenHashMap<String> mOrigMetricValuesOfMimicGrpah = mEdgeModifier.getOriginalMetricValues();
			ObjectDoubleOpenHashMap<String> mOptimizedMetricValues = mEdgeModifier.getOptimizedMetricValues();
			Object[] arrMetricNames = mOrigMetricValuesOfMimicGrpah.keys;
			for(int i = 0 ; i < arrMetricNames.length ; i++){
				if(mOrigMetricValuesOfMimicGrpah.allocated[i]){
					String metricName = (String) arrMetricNames[i];
					fWriter.write("-- Metric: "+ metricName + ":\n");
					
					int idxGraph = 1;
					Set<String> setKeyGraphs = mapInputGraphMetricValues.keySet();
					for(String keyGraph: setKeyGraphs){
						//generate name for each graph
						String graphName = "Graph "+idxGraph;
						mapGraphName.put(keyGraph, graphName);
						
						Map<String, Double> mapInputGraphVal = mapInputGraphMetricValues.get(keyGraph);
						double inputGraphValue = mapInputGraphVal.containsKey(metricName)? mapInputGraphVal.get(metricName): Double.NaN;
						fWriter.write("\t "+ graphName +": "+ inputGraphValue + "\n");
						idxGraph ++;
					}
					
					double originalVal = mOrigMetricValuesOfMimicGrpah.get(metricName);
					fWriter.write("\t The first mimic graph: "+ originalVal + "\n");
					double optimizedVal = mOptimizedMetricValues.get(metricName);
					fWriter.write("\t The opimized mimic graph: "+ optimizedVal + "\n");
				}
			}
			fWriter.write("\n");
			fWriter.write("- Constant expressions\n");
			// constant expressions and their values for each graphs 
			Map<Expression, Map<String, Double>>mapConstantValues = mErrScoreCalculator.getMapConstantExpressions();
			Set<Expression> setExprs = mapConstantValues.keySet();
			for(Expression expr: setExprs){
				fWriter.write("-- Expression: "+ expr.toString() + ":\n");
				
				Map<String, Double> mapGraphAndConstantValues = mapConstantValues.get(expr);
				Set<String> setKeyGraphs = mapGraphAndConstantValues.keySet();
				for(String keyGraph: setKeyGraphs){
					double constVal = mapGraphAndConstantValues.get(keyGraph);
					fWriter.write("\t "+mapGraphName.get(keyGraph)+": "+ constVal + "\n");
				}
				
				double origConstantVal = expr.getValue(mOrigMetricValuesOfMimicGrpah);
				fWriter.write("\t The first mimic graph: "+ origConstantVal + "\n");
				double optimizedConstantVal = expr.getValue(mOptimizedMetricValues);
				fWriter.write("\t The opimized mimic graph: "+ optimizedConstantVal + "\n");
			}
			
			fWriter.write("\n");
			fWriter.write("- Sum error score\n");
			fWriter.write("-- Average sum error score: "+ mErrScoreCalculator.getAverageErrorScore() + ":\n");
			fWriter.write("-- Min sum error score: "+ mErrScoreCalculator.getMinErrorScore() + ":\n");
			fWriter.write("-- Max sum error score: "+ mErrScoreCalculator.getMaxErrorScore() + ":\n");
			// constant expressions and their values for each graphs 
			Map<String, Double> mapSumErrorScores = mErrScoreCalculator.getMapSumErrorScore();
			Set<String> setKeyGraphs = mapSumErrorScores.keySet();
			for(String keyGraph: setKeyGraphs){
				double errorScore = mapSumErrorScores.get(keyGraph);
				fWriter.write("\t "+mapGraphName.get(keyGraph)+": "+ errorScore + "\n");

			}
			fWriter.write("\t The first mimic graph: "+ mLstErrorScore.get(0) + "\n");
			fWriter.write("\t The opimized mimic graph: "+ mLstErrorScore.get(mLstErrorScore.size()-1) + "\n");			
			
			fWriter.write("\n\n\n");
			fWriter.close();
			
			
			
			fErrorScoreWriter.write("# Error score of "+ mMaxIteration + " iteration\n");
			// list of of error score
			for(int i = 0 ; i < mLstErrorScore.size() ; i++){
				if(i < mLstErrorScore.size() -1){
					fErrorScoreWriter.write(mLstErrorScore.get(i) + ",");
				}else{
					fErrorScoreWriter.write(mLstErrorScore.get(i)+"");
				}
			}
			fErrorScoreWriter.write("\n\n\n");
			fErrorScoreWriter.close();
			
		}catch(Exception ex){
			LOGGER.warn("Cannot output results to file! Please check: " + ex.getMessage());
		}
	}
	
	public void setNumberOfOptimizations(int iNumberOfOptimizations){
		if(iNumberOfOptimizations > 0 )
			mMaxIteration = iNumberOfOptimizations; 		
	}
}


